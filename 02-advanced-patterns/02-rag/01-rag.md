# Retrieval-Augmented Generation (RAG)

In the foundations section you saw what RAG is and why it works. Models only know their training data, so you retrieve relevant facts, augment the prompt, and let the model generate a grounded answer. That rests on embeddings, a vector store, and an ETL pipeline that reads, chunks, and loads your documents. This section shows how Spring AI gives you each of those pieces, and how an advisor ties them together at query time.

## The EmbeddingModel

Just as `ChatModel` is the portable contract over chat providers, Spring AI defines **`EmbeddingModel`** as the portable contract over embedding providers such as OpenAI and Ollama. You hand it text and it returns vectors.

```java
float[] vector = embeddingModel.embed("Does VMware Tanzu Spring provide commercial support for Micrometer?");
List<float[]> vectors = embeddingModel.embed(List.of("first text", "second text"));
```

Like the chat starters, each provider ships an embedding starter such as `spring-ai-starter-model-openai`, and the autoconfiguration gives you a ready `EmbeddingModel` bean to inject. The model and the other options live in configuration, so you can switch embedding models without touching code.

```properties
spring.ai.openai.embedding.model=text-embedding-3-small
```

Remember the rule from the foundations section. You must use the same embedding model for indexing and for querying, because vectors are only comparable when they were produced the same way.

In practice you will rarely call `EmbeddingModel` directly. It does its work behind the scenes, for example inside the `VectorStore` abstraction.

## The VectorStore Abstraction

Spring AI defines a single **`VectorStore`** abstraction over the many vector database implementations such as PGVector, Redis, Qdrant, Milvus, and Chroma, plus an in-memory `SimpleVectorStore` for testing. As with everything else, swapping implementations is mostly a dependency and configuration change rather than a rewrite. You add the matching starter, and the autoconfiguration provides a `VectorStore` bean.

The store works with the **`Document`** abstraction, a piece of text plus arbitrary metadata.

```java
Document doc = new Document(
    "VMware Tanzu Spring includes commercial support for Micrometer.",
    Map.of("source", "tanzu-spring-support.pdf", "type", "Spring"));
```

Adding documents is deliberately simple. You hand the store a list of `Document` objects, and it **computes the embeddings for you** with your configured `EmbeddingModel` before it persists the vector alongside the text and the metadata.

```java
vectorStore.add(List.of(doc1, doc2, doc3));
```

Searching is just as direct. You describe what you want with a **`SearchRequest`** and get back the most similar documents.

```java
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("Does VMware Tanzu Spring provide commercial support for Micrometer?")
        .topK(5)                  // return at most 5 matches
        .similarityThreshold(0.7) // ignore weak matches (0..1)
        .build());
```

The `topK` and `similarityThreshold` settings are the ones you met in the foundations section. You pass a plain text `query` here, and it is embedded for you before it is compared against the stored vectors.

### Filtering by metadata

The metadata you attached to each `Document` is searchable through a **portable filter expression language** that looks like SQL and works the same way across every vector store.

```java
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("Does VMware Tanzu Spring provide commercial support for Micrometer?")
        .filterExpression("type == 'Spring'")
        .build());
```

You can also build these filters programmatically with `FilterExpressionBuilder` when the criteria are dynamic, for example when you restrict results to the project the user is asking about.

## The ETL Pipeline in Spring AI

The indexing phase follows the Extract, Transform, Load pattern from the foundations section, and Spring AI models it as three interfaces you can compose.

**Extract** is handled by a **`DocumentReader`**, which reads a source and produces `Document` objects. Spring AI ships readers for the formats you actually meet, such as `PagePdfDocumentReader` for PDFs, `TikaDocumentReader` for Office formats like DOCX and PPTX as well as HTML, `JsonReader`, `MarkdownDocumentReader`, and `TextReader` for plain text.

```java
PagePdfDocumentReader reader = new PagePdfDocumentReader("classpath:/support-guide.pdf");
List<Document> documents = reader.read();
```

**Transform** is handled by a **`DocumentTransformer`**, which takes documents and returns reshaped documents. The most important one is the **`TokenTextSplitter`**, which does the chunking you read about in the foundations section and breaks large documents into smaller, focused pieces.

```java
TokenTextSplitter splitter = TokenTextSplitter.builder()
    .withChunkSize(800)   // target chunk size in tokens
    .build();
List<Document> chunks = splitter.split(documents);
```

Other transformers can enrich documents before storage. The `KeywordMetadataEnricher` and the `SummaryMetadataEnricher`, for example, use a chat model to attach keywords or summaries as metadata, which gives you more to filter and match on later.

**Load** is handled by a **`DocumentWriter`**, and here comes the neat part. `VectorStore` itself *is* a `DocumentWriter`, so the store you search at query time is also the sink at the end of your pipeline. Each stage is a standard Java functional interface, namely `Supplier`, `Function`, and `Consumer`, so the whole pipeline composes into a single readable line.

```java
vectorStore.add(splitter.split(reader.read()));
```

You run this once, and again whenever your documents change, to fill the store. With indexing done, everything is in place for the query time phase.

You now have all the pieces of retrieval, augmentation, and generation. Embed the question, search the vector store, attach the results to the prompt, call the model. You *could* wire those steps together by hand, but Spring AI builds this on the **advisors** concept instead.

## RAG as an Advisor

You met the **advisor** concept in the fundamentals module. An advisor is an interceptor that wraps a `ChatClient` call, acting *before* the request reaches the model and *after* the response comes back, with several advisors forming a chain. Adding one is a configuration change on the `ChatClient`, while your prompting code stays the same fluent chain.

RAG is just one more advisor in that chain. A RAG advisor retrieves matching documents from the vector store and injects them into the prompt as context, so the call reaches the model grounded in your own data. Spring AI ships two such advisors, a simple one and a modular one.

## Bringing It Together With the QuestionAnswerAdvisor

The **`QuestionAnswerAdvisor`** is the advisor that turns a plain `ChatClient` into a RAG application. In its *before* phase it takes the question of the user, runs a similarity search against the vector store, and injects the matching documents into the prompt as context. The call then proceeds to the model, which answers grounded in that context.

```java
String answer = chatClient.prompt()
    .user("Does VMware Tanzu Spring provide commercial support for Micrometer?")
    .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
    .call()
    .content();
```

That is a complete RAG application. The advisor retrieves the relevant support documents and grounds the answer in them, without any retrieval code in the call itself. You can tune what it retrieves by giving it a `SearchRequest`, supply a custom prompt template to control how the context is framed, or pass a filter expression per request.

```java
var searchRequest = SearchRequest.builder().topK(6).similarityThreshold(0.8).build();
var promptTemplate = new PromptTemplate("""
			{query}

			Context information is below, surrounded by ---------------------

			---------------------
			{question_answer_context}
			---------------------

			Given the context and provided history information and not prior knowledge,
			reply to the user comment. If the answer is not in the context, inform
			the user that you can't answer the question.
			""");

String answer = chatClient.prompt()
    .user("Does VMware Tanzu Spring provide commercial support for Micrometer?")
    .advisors(QuestionAnswerAdvisor.builder(vectorStore).searchRequest(searchRequest).promptTemplate(promptTemplate).build())
    .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "type == 'Spring'"))
    .call()
    .content();
```

The default template of the `QuestionAnswerAdvisor` looks just like the custom one in the example above. Whichever template you use, it must contain a `{query}` placeholder for the question of the user and a `{question_answer_context}` placeholder for the retrieved context.

## Modular RAG With the RetrievalAugmentationAdvisor

The foundations section described how the retrieval flow can be broken into stages for the cases where naive retrieval is not enough. Spring AI offers a second, more flexible advisor for exactly that, the **`RetrievalAugmentationAdvisor`**, built on a **modular RAG architecture**. It breaks the retrieval flow into the well defined stages you read about, pre-retrieval, retrieval, post-retrieval, and generation, which you mix and match like building blocks.

```java
Advisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
    .queryTransformers(RewriteQueryTransformer.builder()
        .chatClientBuilder(chatClientBuilder)
        .build())
    .documentRetriever(VectorStoreDocumentRetriever.builder()
        .vectorStore(vectorStore)
        .similarityThreshold(0.5)
        .build())
    .build();
```

Each stage has ready-made components. **Query transformers** reshape the question before the search, where `CompressionQueryTransformer` folds the conversation history into a standalone query, `RewriteQueryTransformer` rephrases an awkward question for better search results, and `TranslationQueryTransformer` translates it into the language of your documents. **Query expanders** such as `MultiQueryExpander` turn one question into several variations to widen the net. The **`VectorStoreDocumentRetriever`** does the actual search, and a **`ContextualQueryAugmenter`** controls generation, including whether an answer is allowed when no context was found.

You do not need these on day one, and the lab uses the simpler `QuestionAnswerAdvisor`. It is worth knowing that the modular path exists, so that you can reach for these components without leaving the programming model once your support assistant outgrows naive retrieval.
