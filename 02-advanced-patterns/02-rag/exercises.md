# Retrieval Augmented Generation (RAG) Lab

In this lab you add **Retrieval Augmented Generation (RAG)** to the support assistant. You index a small Markdown knowledge base, retrieve the most relevant chunks for every question, and let the model answer based on them.

Read [Foundations of RAG and Tool Calling](../01-foundations.md) and [Retrieval Augmented Generation (RAG)](01-rag.md) first.

## Before You Start

Your starting point is the [`sample-app/`](sample-app/) of this folder. It is the assistant from the fundamentals lab, a `ChatClient` with a default system prompt, a logging and a memory advisor, and a `/api/v1/chat` endpoint that returns a structured `SupportResponse` record. All paths in this lab are relative to `sample-app/`, and all commands run inside it.

As before you work with two terminals. **Terminal 1** runs the application, and **Terminal 2** sends requests with `curl`. DevTools restarts the application when your IDE compiles a changed class. After a change to `pom.xml` you stop the application with `Ctrl+C` and start it again.

Every Java change shows the **complete file**, so you can replace the whole content of the file.

## 1. Add the RAG Dependencies

To keep things simple, this lab uses an in memory vector store. In production you would normally use an external database instead, which needs an extra dependency such as `spring-ai-starter-vector-store-pgvector`. Swapping the store is a change of dependencies and configuration, not a rewrite.

Embedding models can also need their own dependency, depending on the provider. For example `spring-ai-starter-model-bedrock-converse` covers chat but does **not** ship an embedding model, so on AWS Bedrock you would add the broader `spring-ai-starter-model-bedrock` starter next to it. Some providers such as Anthropic do not offer an embedding model at all, so there you use a different provider for the embeddings.

This lab needs two additional Spring AI modules.

- `spring-ai-vector-store-advisor` contains the advisors that work with a `VectorStore`, the `QuestionAnswerAdvisor` and the `VectorStoreChatMemoryAdvisor`.
- `spring-ai-markdown-document-reader` provides the `DocumentReader` implementation for Markdown documents. Other formats have their own module, such as `spring-ai-tika-document-reader` for PDF files.

Add the following dependencies to `pom.xml`, right after the `spring-ai-starter-model-openai` dependency.

```xml

		<dependency>
			<groupId>org.springframework.ai</groupId>
			<artifactId>spring-ai-vector-store-advisor</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.ai</groupId>
			<artifactId>spring-ai-markdown-document-reader</artifactId>
		</dependency>
```

## 2. Configure the Embedding Model

For most providers the embedding settings live in the same namespace as the chat settings, under `spring.ai.<provider>.embedding.*`. Some providers are an exception. On AWS Bedrock for example the chat model and the embedding model can come from different starters, so their settings live under different namespaces.

Append the following lines to `src/main/resources/application.properties`.

```properties
spring.ai.openai.embedding.model=text-embedding-3-small
```

Keep one rule in mind. You must use the **same embedding model for indexing and for querying**, because vectors can only be compared when they were produced in the same way.

## 3. Provide a VectorStore Bean

This lab uses the in memory `SimpleVectorStore`. It is not provided by auto configuration, so you create the bean yourself. This step is only needed for the in memory store. External stores such as pgvector are auto configured by their own starter.

Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantConfiguration.java` with the following code.

```java
package com.example.support_assistant;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;

@Configuration
public class SupportAssistantConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt,
                                 ChatMemory chatMemory) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE),
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @ConditionalOnMissingBean(VectorStore.class)
    @Bean
    VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
```

`@ConditionalOnMissingBean` makes this bean step aside as soon as a real `VectorStore` shows up. If you added the `spring-ai-starter-vector-store-pgvector` starter, for example, its auto configured store backed by PostgreSQL would take over. Note the `EmbeddingModel` parameter. The store uses it to turn documents into vectors.

## 4. Run the App

In **Terminal 1**, export your key and start the application. The first run downloads the new dependencies.

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

Wait for `Started SupportAssistantApplication` in the logs. Then check in **Terminal 2** that everything is wired up.

```bash
curl http://localhost:8080/actuator/health
```

You should see `{"status":"UP"}`. Keep the application running.

## 5. The Knowledge Base

RAG indexing is a classic ETL pipeline. You **E**xtract documents, **T**ransform them into chunks that the model can digest, and **L**oad them as embedding vectors into the store.

The project ships Markdown support documents about VMware Tanzu Spring in `src/main/resources/knowledge-base/`. This is content the model cannot fully know on its own. Anything your support assistant should know goes into this folder.

Open `src/main/resources/knowledge-base/tanzu-spring.md`. Notice how each top level section is separated by a horizontal rule (`---`). You use these markers in a moment to split the file into one document per section.

## 6. Implement the Indexer

Create `src/main/java/com/example/support_assistant/KnowledgeBaseIndexer.java` with the following code. It is a component that runs the ETL pipeline once at startup.

```java
package com.example.support_assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
class KnowledgeBaseIndexer {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseIndexer.class);

    private final VectorStore vectorStore;

    @Value("classpath:knowledge-base/*.md")
    private Resource[] knowledgeFiles;

    KnowledgeBaseIndexer(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void index() {
        var documentReaderConfig = MarkdownDocumentReaderConfig.builder()
              .withHorizontalRuleCreateDocument(true)
              .withIncludeBlockquote(true)
              .withIncludeCodeBlock(true)
              .build();
        var documentReader = new MarkdownDocumentReader(Arrays.asList(knowledgeFiles), documentReaderConfig);
        List<Document> documents = documentReader.read();

        var tokenTextSplitter = TokenTextSplitter.builder()
              .withMinChunkLengthToEmbed(25)
              .build();
        var splitDocuments = tokenTextSplitter.split(documents);
        vectorStore.add(splitDocuments);
        log.info("Loaded {} document chunks into vector store", splitDocuments.size());
    }
}
```

The three stages of the pipeline are three Spring AI building blocks.

- **Extract.** The `MarkdownDocumentReader` reads the Markdown files and returns them as `Document` objects. How you extract and split your data matters for more than the embedding step, because these chunks are the exact text that the chat model later receives as context.
  - `withHorizontalRuleCreateDocument(true)` starts a new `Document` at every horizontal rule, so you get one document per section instead of one large document per file.
  - `withIncludeBlockquote(true)` and `withIncludeCodeBlock(true)` keep blockquotes and fenced code blocks, which are dropped by default. This makes sure no part of the source text is lost before embedding.
- **Transform.** The `TokenTextSplitter` cuts each document into chunks that fit the embedding model. With `withMinChunkLengthToEmbed(25)` it skips chunks shorter than 25 characters, because a tiny fragment such as a lone heading carries little meaning and only adds noise to the store.
- **Load.** `vectorStore.add(splitDocuments)` embeds every chunk and stores it. The embedding model you configured is called here.

Spring publishes an `ApplicationReadyEvent` when the context is ready to serve requests. So `@EventListener(ApplicationReadyEvent.class)` runs the pipeline once at startup and fills the vector store before the first question arrives.

Indexing at startup is fine for this lab, but not for a real system. It embeds every document again on every restart, and it never notices a document that changes while the application runs. In production you index outside the application lifecycle, triggered when a document is added, changed, or removed, for example by a scheduled job, a message on a queue, or a webhook from your content system. You also store an identifier and a version or checksum with each chunk, so you can update or delete only the affected entries instead of rebuilding the whole store.

## 7. Watch the Logs

DevTools restarts the application with the new class. Look at the logs in Terminal 1. You should see a line like this one.

```text
Loaded 30 document chunks into vector store
```

Each of those chunks has been run through the embedding model and stored as a vector, ready for a similarity search.

## 8. Add the QuestionAnswerAdvisor

The `QuestionAnswerAdvisor` of Spring AI plugs retrieval into the existing `ChatClient` chain. It runs a similarity search against the `VectorStore` before the model call and appends the matches to the prompt.

Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantService.java` with the following code. It injects the `VectorStore`, configures the advisor, and adds it to the call.

```java
package com.example.support_assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
class SupportAssistantService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    SupportAssistantService(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    SupportResponse generateResponse(String query, String conversationId) {
        var ragSearchRequest = SearchRequest.builder().topK(4).similarityThreshold(0.4).build();
        var ragAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(ragSearchRequest)
                .build();

        return chatClient.prompt()
                .user(u -> u
                        .text("Answer the following question with a short, well-structured explanation: {question}")
                        .param("question", query))
                .advisors(ragAdvisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .entity(SupportResponse.class);
    }
}
```

The `SearchRequest` defines the retrieval. It returns the 4 most similar chunks, and only those that pass a cosine similarity threshold of 0.4.

## 9. Test the Grounded Assistant

Ask a question that your knowledge base covers.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Does VMware Tanzu Spring provide commercial support for Micrometer?"
```

Now ask one that it does not cover.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about breaking changes in Spring Framework 7"
```

You get an answer like "I can't answer that from the provided context". That is the **default prompt** of the advisor, which tells the model to refuse anything outside the retrieved context. This is strict, and often exactly what you want. For this assistant, though, you would rather fall back to the general knowledge of the model when the knowledge base has nothing.

## 10. Customize the RAG Prompt

Replace the prompt of the advisor with your own template. The advisor fills two placeholders. `{query}` receives the question of the user, and `{question_answer_context}` receives the retrieved chunks.

Create `src/main/resources/prompts/rag-prompt.st` with the following content.

```text
Use the following retrieved context to answer the user's question. Follow these rules:

1. If the answer can be found in the context, base your answer strictly on that context.
2. If the context does not contain the information needed to answer, rely on your own general knowledge to answer.
3. If you are unsure or the question cannot be answered from either the context or your own knowledge, say so clearly rather than guessing.
4. Do not fabricate facts, sources, or citations.

---
Context:
{question_answer_context}
---

Question:
{query}
```

Rule 2 is the key change. It allows the model to fall back to its general knowledge when the retrieval comes up empty, while it still prefers the context when there is relevant material.

Now inject the file into the service and pass it to the advisor as a `PromptTemplate`. Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantService.java` with the following code.

```java
package com.example.support_assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
class SupportAssistantService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    @Value("classpath:/prompts/rag-prompt.st")
    private Resource ragPromptResource;

    SupportAssistantService(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    SupportResponse generateResponse(String query, String conversationId) {
        var ragSearchRequest = SearchRequest.builder().topK(4).similarityThreshold(0.4).build();
        var promptTemplate = PromptTemplate.builder().resource(ragPromptResource).build();
        var ragAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(ragSearchRequest)
                .promptTemplate(promptTemplate)
                .build();

        return chatClient.prompt()
                .user(u -> u
                        .text("Answer the following question with a short, well-structured explanation: {question}")
                        .param("question", query))
                .advisors(ragAdvisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .entity(SupportResponse.class);
    }
}
```

The new file under `src/main/resources` only reaches the classpath with a fresh build, so stop the application in Terminal 1 with `Ctrl+C` and start it again with `./mvnw spring-boot:run`. Then run both questions again.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Does VMware Tanzu Spring provide commercial support for Micrometer?"
```

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about breaking changes in Spring Framework 7"
```

The Tanzu question still comes back grounded in the indexed documents, and the Spring Framework question now gets a real answer instead of a refusal.

## Recap

Your assistant now answers from your own documents, with the general knowledge of the model as a fallback. The [`sample-app/`](../03-tool-calling/sample-app/) of the tool calling lab contains the result of this lab. Next you give the assistant the ability to *act* with tool calling.
