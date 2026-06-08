# Spring AI Support Assistant — RAG Walkthrough

Continues from `INSTRUCTIONS-1.md`. Your starting point is the structured-output Support Assistant: `ChatClient` with a default system prompt, a `/api/1.0/chat` endpoint returning a `SupportResponse` record.

In this part you'll add Retrieval Augmented Generation: index a small Markdown knowledge base, retrieve the most relevant chunks per query, and have the model answer grounded in them. Each step is a small change; restart and `curl` between steps.

## 1. Add RAG dependencies

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

### AWS Bedrock attendees

`spring-ai-starter-model-bedrock-converse` covers chat but does **not** ship an embedding model. Add the broader Bedrock starter alongside it:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-bedrock</artifactId>
</dependency>
```

### Optional: PostgreSQL + pgvector

If you'd rather use a real vector database than the in-memory `SimpleVectorStore`, add the pgvector starter plus Spring Boot's Docker Compose integration:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-docker-compose</artifactId>
    <optional>true</optional>
</dependency>
```

And drop a `compose.yaml` at the project root so Boot starts a Postgres container for you:

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg17
    environment:
      POSTGRES_DB: support_assistant
      POSTGRES_USER: support_assistant
      POSTGRES_PASSWORD: support_assistant
    ports:
      - "5432:5432"
```

## 2. Configure the embedding model

Embeddings live in the same provider namespace as chat (`spring.ai.<provider>.embedding.*`). Append the block matching the provider you chose in INSTRUCTIONS-1 to your `application.properties` (or to the matching profile file).

### OpenAI

```properties
spring.ai.model.embedding=openai
spring.ai.openai.embedding.model=text-embedding-3-small
```

### Anthropic

Anthropic doesn't publish an embedding model — pick a separate provider (here Voyage) for embeddings while keeping Anthropic for chat:

```properties
spring.ai.model.embedding=anthropic
spring.ai.openai.embedding.model=voyage-4
```

### Bedrock

```properties
spring.ai.model.embedding=bedrock-cohere
spring.ai.bedrock.cohere.embedding.model=cohere.embed-multilingual-v3
```

### Ollama

```properties
spring.ai.model.embedding=ollama
spring.ai.ollama.embedding.model=nomic-embed-text-v2-moe
```

Pull the model so the daemon has it locally:

```bash
ollama pull nomic-embed-text-v2-moe
```

### Vector store config (only if you chose pgvector)

Append to `application.properties`:

```properties
spring.ai.vectorstore.pgvector.initialize-schema=true
spring.ai.vectorstore.pgvector.remove-existing-vector-store-table=true
spring.docker.compose.lifecycle-management=start-and-stop
```

The first two ask Spring AI to create the vector table on startup and clear it on each run (handy during the workshop). The third tells Boot to start and stop the Compose stack with the app.

## 3. Configure the `VectorStore` bean (skip if using pgvector)

Without pgvector, no auto-configuration provides a `VectorStore` — you need to create one yourself. Add a fallback to `SupportAssistantConfiguration` using the in-memory `SimpleVectorStore`:

```java
@ConditionalOnMissingBean(VectorStore.class)
@Bean
VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
    return SimpleVectorStore.builder(embeddingModel).build();
}
```

Imports:

```java
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
```

`@ConditionalOnMissingBean` makes this bean drop out the moment a real `VectorStore` shows up — so the same class works in both the in-memory and pgvector setups.

## 4. Implement the ETL pipeline

RAG indexing is a classic ETL: **E**xtract documents, **T**ransform them into chunks the model can digest, **L**oad them (as embedding vectors) into the store.

Add a few Markdown files in `src/main/resources/knowledge-base/` — anything your support assistant should "know". Each file becomes one or more documents. (The project already ships a few support docs for Tanzu/Spring as examples.)

Create `src/main/java/com/example/support_assistant/KnowledgeBaseIndexer.java`:

```java
package com.example.support_assistant;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @PostConstruct
    public void index() {
        var config = MarkdownDocumentReaderConfig.builder().build();
        var documentReader = new MarkdownDocumentReader(Arrays.asList(knowledgeFiles), config);
        var tokenTextSplitter = TokenTextSplitter.builder().build();
        List<Document> documents = tokenTextSplitter.apply(documentReader.get());
        vectorStore.add(documents);
        log.info("Loaded {} document chunks into vector store", documents.size());
    }
}
```

The three Spring AI building blocks at play:

- **`MarkdownDocumentReader`** — extracts a `Document` per Markdown file.
- **`TokenTextSplitter`** — transforms each document into smaller chunks sized for the embedding model.
- **`VectorStore.add(...)`** — embeds and loads them. The embedding model from step 2 is invoked here, transparently.

Restart the app. You should see something like:

```
Loaded 14 document chunks into vector store
```

## 5. Add RAG to the Support Assistant

Spring AI's `QuestionAnswerAdvisor` plugs retrieval into the existing `ChatClient` chain — it runs a similarity search before the model call and appends the matches to the prompt.

### 5a. Plain `QuestionAnswerAdvisor`

Inject the `VectorStore` into `SupportAssistantService`:

```java
private final VectorStore vectorStore;

SupportAssistantService(ChatClient chatClient, VectorStore vectorStore) {
    this.chatClient = chatClient;
    this.vectorStore = vectorStore;
}
```

Replace `generateResponse`:

```java
SupportResponse generateResponse(String query) {
    var ragSearchRequest = SearchRequest.builder().topK(3).similarityThreshold(0.7).build();
    var ragAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
            .searchRequest(ragSearchRequest)
            .build();

    return chatClient.prompt()
            .user(u -> u
                    .text("Answer the following question with a short, well-structured explanation: {question}")
                    .param("question", query))
            .advisors(ragAdvisor)
            .call()
            .entity(SupportResponse.class);
}
```

Imports:

```java
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
```

`SearchRequest` defines the retrieval — top 3 most similar chunks, only if they pass a 0.7 cosine-similarity threshold.

Try a question your knowledge base covers:

```bash
curl -G "http://localhost:8080/api/1.0/chat" --data-urlencode "query=What is Tanzu Spring Runtime?"
```

Now try one it doesn't:

```bash
curl -G "http://localhost:8080/api/1.0/chat" --data-urlencode "query=Tell me about Spring AI"
```

You'll get something like:

> I can't answer that from the provided context, because it only mentions Tanzu Spring support and Spring Cloud components, not Spring AI.

That's the advisor's default prompt at work — it instructs the model to refuse anything outside the retrieved context. Strict, and often what you want. For our assistant, though, we'd rather fall back to the model's general knowledge when the knowledge base has nothing.

### 5b. Custom RAG prompt

Override the advisor's prompt. Create `src/main/resources/prompts/rag`:

```
{query}

Context information is below, surrounded by ---------------------

---------------------
{question_answer_context}
---------------------

Reply to the user based on the context if possible.
```

The key change is the closing line: "based on the context **if possible**". That gives the model permission to fall back to general knowledge when retrieval comes up empty, while still preferring the context when it has relevant material.

Inject the resource and pass it to the advisor:

```java
@Value("classpath:/prompts/rag")
private Resource ragPromptResource;
```

Update `generateResponse`:

```java
SupportResponse generateResponse(String query) {
    var ragSearchRequest = SearchRequest.builder().topK(3).similarityThreshold(0.7).build();
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
            .call()
            .entity(SupportResponse.class);
}
```

Imports:

```java
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
```

Restart and re-run both curls:

```bash
curl -G "http://localhost:8080/api/1.0/chat" --data-urlencode "query=What is Tanzu Spring Runtime?"
curl -G "http://localhost:8080/api/1.0/chat" --data-urlencode "query=Tell me about Spring AI"
```

The Tanzu question still comes back from the indexed docs; the Spring AI question now gets a real answer instead of a refusal.

---

## Recap

| Step | What changed |
|------|--------------|
| 1 | Add `vector-store-advisor` and `markdown-document-reader` (+ Bedrock embedding starter / pgvector deps) |
| 2 | Configure `spring.ai.<provider>.embedding.*` (+ optional pgvector + Compose lifecycle) |
| 3 | Fallback `SimpleVectorStore` bean for non-pgvector setups |
| 4 | `KnowledgeBaseIndexer` — read Markdown, split into chunks, load into the `VectorStore` |
| 5a | `QuestionAnswerAdvisor` on the `ChatClient` chain |
| 5b | Custom RAG prompt to allow fallback when context is empty |