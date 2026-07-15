# RAG — Hands-on Exercises

Your starting point is the [`sample-app/`](sample-app/), the structured-output assistant from the fundamentals module: a `ChatClient` with a default system prompt, conversation memory, and a `/api/v1/chat` endpoint returning a `SupportResponse` record.

In this lab you add **Retrieval Augmented Generation**: index a small Markdown knowledge base, retrieve the most relevant chunks per query, and have the model answer grounded in them. Each step is a small change; restart and `curl` between steps.

## 1. Add the RAG dependencies

Add these to `pom.xml`:

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

- `spring-ai-vector-store-advisor` brings the `QuestionAnswerAdvisor` that plugs retrieval into a `ChatClient` chain.
- `spring-ai-markdown-document-reader` parses `.md` files into Spring AI `Document` objects.

This lab uses the in-memory `SimpleVectorStore`. For production you'd normally use an external store (for example the `spring-ai-starter-vector-store-pgvector` starter plus a `compose.yaml` for Postgres). Swapping the store is a dependency and configuration change, not a rewrite.

## 2. Configure the embedding model

Embeddings live in the same provider namespace as chat. Append to `application.properties`:

```properties
spring.ai.openai.embedding.model=text-embedding-3-small
```

Remember the rule: you must use the **same embedding model for indexing and querying**, because vectors are only comparable when produced the same way.

## 3. Provide a `VectorStore` bean

`SimpleVectorStore` is not auto-configured, so create the bean yourself. Add it to `SupportAssistantConfiguration` (after the `chatClient` bean):

```java
@ConditionalOnMissingBean(VectorStore.class)
@Bean
VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
    return SimpleVectorStore.builder(embeddingModel).build();
}
```

Add the imports:

```java
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
```

`@ConditionalOnMissingBean` makes this bean step aside if a real `VectorStore` shows up (e.g. from a pgvector starter). The `EmbeddingModel` parameter is what the store uses to turn documents into vectors.

## 4. The knowledge base

The sample-app ships Markdown support docs under `src/main/resources/knowledge-base/`. Open `knowledge-base/tanzu-spring.md` and notice each top-level section is separated by a horizontal rule (`---`). You'll use those markers to split the file into one document per section.

## 5. Implement the indexer (the ETL pipeline)

Create `KnowledgeBaseIndexer.java`, a component that runs the ETL pipeline once at startup:

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

The three Spring AI building blocks:

- **`MarkdownDocumentReader`** *extracts* `Document` objects. `withHorizontalRuleCreateDocument(true)` starts a new document at every `---`, so you get one document per section instead of one giant document per file.
- **`TokenTextSplitter`** *transforms* each document into smaller chunks. `withMinChunkLengthToEmbed(25)` skips tiny fragments like a lone heading.
- **`VectorStore.add(...)`** embeds and *loads* them. Your configured embedding model is invoked here.

`@EventListener(ApplicationReadyEvent.class)` runs the pipeline once, after the app has fully started. Running indexing at startup is fine for a demo — in production you'd index outside the app lifecycle (a job, a queue message, a webhook) and track a version/checksum per chunk so you only update what changed.

## 6. Run and watch the logs

Start the app. In the logs you should see:

```
Loaded 30 document chunks into vector store
```

Each chunk has been embedded and stored as a vector, ready for similarity search.

## 7. Add the `QuestionAnswerAdvisor`

The `QuestionAnswerAdvisor` runs a similarity search against the `VectorStore` before the model call and appends the matches to the prompt.

Inject the `VectorStore` into `SupportAssistantService` — add the field and update the constructor:

```java
private final VectorStore vectorStore;

SupportAssistantService(ChatClient chatClient, VectorStore vectorStore) {
    this.chatClient = chatClient;
    this.vectorStore = vectorStore;
}
```

Configure and add the advisor in `generateResponse`:

```java
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
```

Add the imports:

```java
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
```

The `SearchRequest` retrieves the top 4 chunks, keeping only those above a 0.4 similarity threshold.

Try a question your knowledge base covers:

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Does VMware Tanzu Spring provide commercial support for Micrometer?"
```

Now one it doesn't:

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about breaking changes in Spring Framework 7"
```

You'll get a refusal like *"I can't answer that from the provided context …"*. That's the advisor's **default prompt**, which restricts the model to the retrieved context. Often what you want, but for this assistant we'd rather fall back to general knowledge when retrieval comes up empty.

## 8. Customize the RAG prompt

Override the advisor's prompt with your own template. The two placeholders are filled by the advisor: `{query}` with the user's question, `{question_answer_context}` with the retrieved chunks.

Create `src/main/resources/prompts/rag-prompt.st`:

```
Use the following retrieved context to answer the user's question. Follow these rules:

1. If the answer can be found in the context, base your answer strictly on that context.
2. If the context does not contain the information needed to answer, rely on your own general knowledge to answer, and explicitly note that the answer is not drawn from the provided context.
3. If you are unsure or the question cannot be answered from either the context or your own knowledge, say so clearly rather than guessing.
4. Do not fabricate facts, sources, or citations.

---
Context:
{question_answer_context}
---

Question:
{query}
```

Inject the resource into the service and pass it to the advisor as a `PromptTemplate`. Add a field:

```java
@Value("classpath:/prompts/rag-prompt.st")
private Resource ragPromptResource;
```

Update `generateResponse` to build and use the template:

```java
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
```

Add the imports:

```java
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
```

Re-run both queries. The Tanzu question still comes back grounded in the docs, and the Spring Framework 7 question now gets a real answer with a note that it's from general knowledge, instead of a refusal.

## Recap

Your assistant now answers from your own documents, with the model's general knowledge as a graceful fallback. Next you give it the ability to *act* with tool calling.
