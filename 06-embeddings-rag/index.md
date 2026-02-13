---
title: Embeddings & RAG
---

In this section, you'll build the **knowledge** module that implements Retrieval-Augmented Generation (RAG) to answer questions from documentation.

## Learning Objectives

- Understand embeddings and vector similarity
- Create a vector store from documents
- Implement RAG to enhance AI responses with context

## The Problem RAG Solves

LLMs have inherent limitations that can impact enterprise applications:
- **Knowledge cutoff**: Training data has a fixed date limit, so the model doesn't know about recent updates, patches, or documentation changes
- **No proprietary knowledge**: Models are trained on public data and have no awareness of your internal documentation, policies, or customer-specific information
- **Hallucinations**: When asked about unfamiliar topics, models may generate plausible-sounding but incorrect answers

RAG (Retrieval-Augmented Generation) addresses these limitations by combining the LLM's reasoning capabilities with your own knowledge base.

## How Embeddings Work

Embeddings are the foundation of RAG. They convert text into high-dimensional numerical vectors (typically 256-1536 dimensions) that capture semantic meaning. The key insight is that **semantically similar texts produce vectors that are close together** in this high-dimensional space.

```
"Spring Boot"      → [0.2, 0.8, 0.1, ...]
"Spring Framework" → [0.3, 0.7, 0.2, ...]  ← Similar vectors (close in space)
"Pizza recipe"     → [0.9, 0.1, 0.4, ...]  ← Different vector (far in space)
```

These vectors are stored in a **vector database** (or vector store), which is optimized for **similarity search** - finding vectors that are closest to a query vector. When a user asks a question:
1. The question is converted to a vector using the same embedding model
2. The vector database finds the most similar document vectors
3. The corresponding text chunks are retrieved and added to the prompt

## Explore the Knowledge Base

First, let's see what documentation is available:

```terminal:execute
command: ls -la ~/sample-app/src/main/resources/data/
session: 1
```

```terminal:execute
command: head -50 ~/sample-app/src/main/resources/data/spring-enterprise-support.md
session: 1
```

## Add Required Dependencies

Spring AI supports various vector stores (PostgreSQL/pgvector, Redis, Pinecone, Chroma, etc.) and document readers (PDF, Markdown, HTML, etc.). Depending on your choice, you need to add the corresponding dependencies.

For this workshop, we add:
- `spring-ai-advisors-vector-store` - Includes the core classes and the `SimpleVectorStore` in-memory implementation that's useful for development and testing but not recommended for production
- `spring-ai-markdown-document-reader` - Reads and parses Markdown files

```editor:select-matching-text
file: ~/sample-app/pom.xml
text: "</dependencies>"
description: Add RAG dependencies
before: 0
after: 0
cascade: true
```
```editor:replace-text-selection
file: ~/sample-app/pom.xml
hidden: true
text: |2
          <dependency>
              <groupId>org.springframework.ai</groupId>
              <artifactId>spring-ai-advisors-vector-store</artifactId>
          </dependency>

          <dependency>
              <groupId>org.springframework.ai</groupId>
              <artifactId>spring-ai-markdown-document-reader</artifactId>
          </dependency>
      </dependencies>
```

## Configure the Vector Store

The vector store requires an embedding model to convert text into vectors. Spring AI auto-configures an embedding model based on your AI provider, but we need to specify which model to use.

First, configure the embedding model in the application configuration:

```editor:select-matching-text
file: ~/sample-app/src/main/resources/application-openai.yaml
text: "model: gpt-4o"
description: Add embedding model configuration
before: 0
after: 0
cascade: true
```
```editor:replace-text-selection
file: ~/sample-app/src/main/resources/application-openai.yaml
hidden: true
before: 0
after: 0
text: |2
            model: gpt-4o
        embedding:
          options:
            model: text-embedding-3-small
```

Now add the `VectorStore` bean to the existing configuration class. The `SimpleVectorStore` uses the `EmbeddingModel` to convert documents into vectors and stores them in memory:

```editor:select-matching-text
file: ~/sample-app/src/main/java/com/example/supportassistant/SupportAssistantConfiguration.java
text: "import org.springframework.context.annotation.Configuration;"
description: Add VectorStore bean
before: 0
after: 0
cascade: true
```
```editor:replace-text-selection
file: ~/sample-app/src/main/java/com/example/supportassistant/SupportAssistantConfiguration.java
hidden: true
cascade: true
text: |
  import org.springframework.context.annotation.Configuration;
  import org.springframework.ai.embedding.EmbeddingModel;
  import org.springframework.ai.vectorstore.SimpleVectorStore;
  import org.springframework.ai.vectorstore.VectorStore;
```

```editor:insert-lines-before-line
file: ~/sample-app/src/main/java/com/example/supportassistant/SupportAssistantConfiguration.java
line: 17
hidden: true
text: |2

      @Bean
      public VectorStore vectorStore(EmbeddingModel embeddingModel) {
          return SimpleVectorStore.builder(embeddingModel).build();
      }
```

## Create the DocumentLoader

Before we can query our knowledge base, we need to build an ETL (Extract, Transform, Load) pipeline:

1. **Extract**: Read documents from source files (Markdown in our case)
2. **Transform**: Split documents into smaller chunks that fit within the model's context window
3. **Load**: Convert chunks to embeddings and store them in the vector store

Spring AI provides components for each step:
- `DocumentReader` implementations (like `MarkdownDocumentReader`) handle extraction
- `DocumentTransformer` implementations (like `TokenTextSplitter`) handle chunking
- `VectorStore` handles embedding and storage

Create the Knowledge module directory:
```terminal:execute
command: mkdir -p ~/sample-app/src/main/java/com/example/supportassistant/knowledge
session: 1
description: Create DocumentLoader with ETL pipeline
cascade: true
```

```editor:append-lines-to-file
file: ~/sample-app/src/main/java/com/example/supportassistant/knowledge/DocumentLoader.java
hidden: true
text: |
    package com.example.supportassistant.knowledge;

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

    import jakarta.annotation.PostConstruct;
    import java.util.List;
    import java.util.Arrays;

    @Component
    public class DocumentLoader {

        private static final Logger log = LoggerFactory.getLogger(DocumentLoader.class);

        private final VectorStore vectorStore;

        @Value("classpath:data/*.md")
        private Resource[] knowledgeFiles;

        public DocumentLoader(VectorStore vectorStore) {
            this.vectorStore = vectorStore;
        }

        @PostConstruct
        public void loadDocuments() {
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder().build();
            MarkdownDocumentReader documentReader = new MarkdownDocumentReader(Arrays.asList(knowledgeFiles), config);
            List<Document> documents = new TokenTextSplitter().apply(documentReader.get());
            vectorStore.add(documents);
            log.info("Loaded {} document chunks into vector store", documents.size());
        }
    }
```

## Create the Knowledge Service

Now we need a service that combines the vector store search with the chat model. Spring AI uses **Advisors** to intercept and modify requests and responses in the chat pipeline - we'll explore Advisors in more detail in a later section.

For RAG, Spring AI provides two advisor options:
- `QuestionAnswerAdvisor` - A simple, out-of-the-box solution for basic RAG scenarios
- `RetrievalAugmentationAdvisor` - A more flexible advisor for advanced RAG patterns with customizable query transformation, document selection, and context augmentation

We'll use the `QuestionAnswerAdvisor` which automatically:
- Searches the vector store for relevant documents based on the user's question
- Injects the retrieved context into the prompt using a customizable template
- Handles the complete RAG flow in a single advisor

```editor:append-lines-to-file
file: ~/sample-app/src/main/java/com/example/supportassistant/knowledge/KnowledgeService.java
description: Create KnowledgeService with QuestionAnswerAdvisor
text: |
  package com.example.supportassistant.knowledge;

  import org.springframework.ai.chat.client.ChatClient;
  import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
  import org.springframework.ai.vectorstore.SearchRequest;
  import org.springframework.ai.vectorstore.VectorStore;
  import org.springframework.stereotype.Service;

  @Service
  public class KnowledgeService {

      private final ChatClient chatClient;
      private final VectorStore vectorStore;

      public KnowledgeService(ChatClient chatClient, VectorStore vectorStore) {
          this.chatClient = chatClient;
          this.vectorStore = vectorStore;
      }

      public String answerQuestion(String question) {
          SearchRequest ragSearchRequest = SearchRequest.builder().topK(3).build();
          QuestionAnswerAdvisor ragAdvisor = QuestionAnswerAdvisor.builder(vectorStore).searchRequest(ragSearchRequest).build();

          return chatClient.prompt()
                  .user(question)
                  .advisors(ragAdvisor)
                  .call()
                  .content();
      }
  }
```

Key points:
- The `QuestionAnswerAdvisor` handles the similarity search and context injection automatically
- `SearchRequest.builder().topK(3)` configures how many document chunks to retrieve
- The advisor intercepts the request, searches for relevant documents, and adds them to the prompt

## Create the Knowledge Controller

Add a REST controller to expose the RAG functionality:

```editor:append-lines-to-file
file: ~/sample-app/src/main/java/com/example/supportassistant/knowledge/KnowledgeController.java
description: Create KnowledgeController REST endpoint
text: |
  package com.example.supportassistant.knowledge;

  import org.springframework.web.bind.annotation.*;

  import java.util.Map;

  @RestController
  @RequestMapping("/knowledge")
  public class KnowledgeController {

      private final KnowledgeService knowledgeService;

      public KnowledgeController(KnowledgeService knowledgeService) {
          this.knowledgeService = knowledgeService;
      }

      @GetMapping("/ask")
      public Map<String, String> askQuestion(@RequestParam String question) {
          String answer = knowledgeService.answerQuestion(question);
          return Map.of(
                  "question", question,
                  "answer", answer
          );
      }
  }
```

## Test the RAG System

Start the application:

```terminal:execute
command: cd ~/sample-app && ./mvnw spring-boot:run
session: 2
```

Ask a question that requires the knowledge base:

```execute
http localhost:8080/knowledge/ask question=="What severity levels are supported for CVE patches?"
```

Ask about support tiers:

```execute
http localhost:8080/knowledge/ask question=="What's the difference between Premium and Standard support?"
```

Ask about Spring Boot versions:

```execute
http localhost:8080/knowledge/ask question=="Which Spring Boot versions have extended LTS support?"
```

## Understanding the RAG Flow

Let's trace what happens:

1. **Query**: "What CVEs are covered?"
2. **Embedding**: Query converted to vector
3. **Search**: Find similar document vectors
4. **Retrieve**: Get top 3 matching chunks
5. **Augment**: Add chunks to system prompt
6. **Generate**: LLM answers using the context

## Stop the Application

```terminal:interrupt
session: 2
```

## Summary

You've built a complete RAG system.

**Key Concepts:**
- Embeddings convert text to vectors for similarity search
- `SimpleVectorStore` is good for development (use a real DB in production)
- `QuestionAnswerAdvisor` simplifies RAG by handling retrieval and context injection
- RAG = Retrieve relevant context → Augment prompt → Generate answer

Next, you'll learn tool calling to connect the AI to external systems!
