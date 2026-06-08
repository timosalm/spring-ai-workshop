## The Model Only Knows What It Was Trained On

A language model is a snapshot. Its knowledge is frozen at the point its training data was collected, and that data is whatever was publicly available at the time. Ask it about a framework released last month, your company's internal runbook, or the support ticket a customer opened this morning, and it simply has no way to know. Worse, models rarely admit the gap. Instead of saying "I don't know," they tend to produce a confident, plausible-sounding answer that is wrong. This is what people mean by *hallucination*.

For a support assistant, that's a serious problem. The whole point is to answer questions about *your* product, using *your* documentation, including content the model has never seen and that changes over time.

You could try to solve this by fine-tuning a model on your data, but that's expensive, slow to update, and has to be repeated every time the data changes. There's a far more practical approach: instead of baking the knowledge into the model, you fetch the relevant facts at question time and hand them to the model alongside the question. The model then answers using that supplied context rather than its frozen memory.

This technique is called **Retrieval Augmented Generation (RAG)**, and it's the single most common pattern for building AI applications over private or fast-changing data. The name describes the flow exactly: you **retrieve** relevant information, use it to **augment** the prompt, and let the model **generate** an answer grounded in that information.

## Two Phases: Indexing and Retrieval

RAG splits naturally into two phases that run at different times.

The first is **indexing**, an offline job you run ahead of time. You take your source documents, e.g. PDFs, web pages, Markdown files, break them into manageable pieces, convert each piece into a form you can search by meaning, and store them. This is a batch process you re-run whenever your documents change, not something that happens on every user request.

The second is **retrieval and generation**, which runs online for each question. When a user asks something, you search the indexed data for the most relevant pieces, attach them to the prompt as context, and call the model. 

The bridge that makes both phases work, finding text "by meaning" rather than by keyword, is the **embedding**. Let's start there, because everything else builds on it.

## Embeddings: Turning Meaning Into Numbers

An **embedding** is a numerical representation of a piece of content, an array of floating-point numbers (a *vector*) that captures its semantic meaning. The key property is that texts with similar meaning produce vectors that sit close together in this numerical space, even when they share no words. "How do I reset my password?" and "I forgot my login credentials" land near each other; "What's the weather in Berlin?" lands far away. By measuring the distance between two vectors, you can measure how related two pieces of text are. This is what lets you search by meaning instead of exact keywords.

Just as `ChatModel` is the portable contract over chat providers, Spring AI defines **`EmbeddingModel`** as the portable contract over embedding providers (OpenAI, Ollama, and others). You hand it text and it returns vectors:

```java
float[] vector = embeddingModel.embed("How do I reset my password?");
List<float[]> vectors = embeddingModel.embed(List.of("first text", "second text"));
```

Like the chat starters, each provider ships an embedding starter (for example `spring-ai-starter-model-openai`), and the auto-configuration gives you a ready `EmbeddingModel` bean to inject. The model and other options are externalized in configuration, so you can switch embedding models without touching code:

```properties
spring.ai.openai.embedding.model=text-embedding-3-small
```

One important rule: you must use the **same embedding model for indexing and for querying**. The vectors are only comparable if they were produced the same way, so the model that embeds your documents is the model that must embed the user's question.

In practice you'll rarely call `EmbeddingModel` directly. It does its work behind the scenes, inside the vector store and the RAG advisors we'll meet next.

## The Vector Store: A Database for Embeddings

Once your content is embedded, you need somewhere to keep the vectors and, crucially, a way to find the nearest ones to a query vector quickly. That's the job of a **vector store**. Conceptually it's a database specialized for storing embeddings and running *similarity search*: "give me the items whose vectors are closest to this one."

Spring AI defines a single **`VectorStore`** abstraction over the many vector database implementations (PGVector, Redis, Qdrant, Milvus, Chroma, and many more), plus a `SimpleVectorStore` for testing. As with everything else, swapping implementations is mostly a dependency-and-configuration change, not a rewrite. You add the matching starter, and the auto-configuration provides a `VectorStore` bean.

The store works with the **`Document`** abstraction, a piece of text plus arbitrary metadata:

```java
Document doc = new Document(
    "To reset your password, open Settings and choose 'Security'.",
    Map.of("source", "support-guide.pdf", "category", "account"));
```

Adding documents is deliberately simple. You hand the store a list of `Document` objects, and it **computes the embeddings for you** (using your configured `EmbeddingModel`) before persisting the vector alongside the text and metadata:

```java
vectorStore.add(List.of(doc1, doc2, doc3));
```

Searching is just as direct. You describe what you want with a **`SearchRequest`** and get back the most similar documents:

```java
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("I forgot my login")
        .topK(5)                  // return at most 5 matches
        .similarityThreshold(0.7) // ignore weak matches (0..1)
        .build());
```

Two parameters do most of the tuning. `topK` caps how many documents come back, and `similarityThreshold` (from 0 to 1) sets how close a match must be to count, filtering out loosely-related noise. Note that you pass a plain-text `query` here; the store embeds it for you and compares it against the stored vectors.

### Filtering by metadata

Semantic similarity is powerful, but sometimes you also need hard, exact constraints: only documents for a given product version, language, or tenant. That metadata you attached to each `Document` is searchable through a **portable filter expression language** that looks like SQL and works the same way across every vector store:

```java
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("how do I upgrade?")
        .filterExpression("category == 'billing' && version >= 2")
        .build());
```

You can also build these filters programmatically with `FilterExpressionBuilder` when the criteria are dynamic (for example, restricting results to the current user's tenant). The result is a search that combines meaning ("upgrade") with precise filtering ("billing docs, version 2 or newer").

## Getting Data In: The ETL Pipeline

We've glossed over how raw files become `Document` objects in the store. That's the indexing phase, and Spring AI models it as a classic **ETL pipeline** — Extract, Transform, Load — built from three composable interfaces.

**Extract** is handled by a **`DocumentReader`**, which reads a source and produces `Document` objects. Spring AI ships readers for the formats you'll actually encounter: `PagePdfDocumentReader` for PDFs, `TikaDocumentReader` for Office formats like DOCX and PPTX (and HTML), `JsonReader`, `MarkdownDocumentReader`, and `TextReader` for plain text.

```java
PagePdfDocumentReader reader = new PagePdfDocumentReader("classpath:/support-guide.pdf");
List<Document> documents = reader.read();
```

**Transform** is handled by a **`DocumentTransformer`**, which takes documents and returns reshaped documents. The most important one is the **`TokenTextSplitter`**, which breaks large documents into smaller **chunks**. Chunking matters more than it first appears. If you embed a whole 50-page manual as one vector, a search either returns all 50 pages or nothing, and the single vector is too "blurry" to match anything precisely. Splitting into focused chunks means retrieval can pull back exactly the paragraph that answers the question, and you only spend tokens on relevant context:

```java
TokenTextSplitter splitter = TokenTextSplitter.builder()
    .withChunkSize(800)   // target chunk size in tokens
    .build();
List<Document> chunks = splitter.apply(documents);
```

Other transformers can *enrich* documents before storage, for instance `KeywordMetadataEnricher` and `SummaryMetadataEnricher` use a chat model to attach keywords or summaries as metadata, giving you more to filter and match on later.

**Load** is handled by a **`DocumentWriter`** — and here's the neat part: `VectorStore` *is* a `DocumentWriter`. So the store you search at query time is also the sink at the end of your pipeline. Because each stage is a standard Java functional interface (`Supplier`, `Function`, `Consumer`), the whole pipeline composes into a single readable line:

```java
vectorStore.write(splitter.split(reader.read()));
```

You run this once (and again whenever your documents change) to populate the store. With indexing done, everything is in place for the query-time phase.

## The Advisor Pattern

You now have all the pieces of retrieval-and-generation: embed the question, search the vector store, attach the results to the prompt, call the model. You *could* wire those steps together by hand on every request, but retrieval is a *cross-cutting concern*, the kind of logic you want to apply consistently around many calls without scattering it through your code. Spring AI handles this with **advisors**.

If you've used Servlet filters or Spring's `HandlerInterceptor`, advisors will feel familiar. An advisor is an **interceptor that wraps a `ChatClient` call**, with a chance to act both *before* the request reaches the model and *after* the response comes back. Several advisors form a **chain**, and a request passes through all of them on the way in, hits the model, and passes back through them on the way out. This is the classic "around" pattern: each advisor can inspect and modify the request, decide whether to proceed, and then inspect and modify the response.

Concretely, the framework wraps your `Prompt` in a **`ChatClientRequest`** (the request plus a shared context map) and hands it to the first advisor. Each advisor does its *before* work, then calls the chain to invoke the next advisor; the last one calls the model. The model's answer travels back as a **`ChatClientResponse`**, and each advisor gets to do its *after* work as it unwinds. A logging advisor captures the shape nicely, log on the way in, delegate to the rest of the chain, log on the way out:

```java
public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    logRequest(request);                                 // before
    ChatClientResponse response = chain.nextCall(request); // delegate down the chain → model
    logResponse(response);                               // after
    return response;
}
```

A few properties are worth understanding, because they explain how advisors behave when you combine them:

- **Order matters, and it's stack-like.** Each advisor reports a priority via `getOrder()` (lower runs first). On the way *in*, advisors run lowest-order first; on the way *out*, the order reverses, just like nested method calls. So an advisor that adds context before the model also gets the first look at the response.
- **Around both phases, two flavors.** The same advisor can implement the blocking `.call()` path (`CallAdvisor`) and the reactive `.stream()` path (`StreamAdvisor`), so cross-cutting behavior works whether you wait for the whole answer or stream it.
- **A shared context map** travels with the request through the whole chain. This is how you pass per-request parameters to an advisor at call time, for example, telling a memory advisor which conversation this is.

You register advisors in one of two places. Most live on the `ChatClient` as **defaults**, applied to every call made through that client:

```java
ChatClient chatClient = builder
    .defaultAdvisors(
        MessageChatMemoryAdvisor.builder(chatMemory).build(),  // conversation memory
        QuestionAnswerAdvisor.builder(vectorStore).build())    // RAG
    .build();
```

Or you attach and parameterize them **per request**, which is also how you feed values into that shared context map:

```java
String answer = chatClient.prompt()
    .user("How do I reset my password?")
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "user-42"))
    .call()
    .content();
```

Spring AI ships a set of built-in advisors for exactly these recurring patterns: the **chat-memory advisors** that maintain conversation history, the **`ToolCallingAdvisor`** that runs the tool-calling loop (you saw it auto-register in the tools section), a **`SafeGuardAdvisor`** that blocks unwanted content, a logging advisor, and, most relevant here, the RAG advisors. The beauty of the pattern is that adding any of these is a configuration change on the `ChatClient`, your prompting code stays the same fluent chain. RAG is just one more advisor in the chain.

## Bringing It Together: The `QuestionAnswerAdvisor`

The **`QuestionAnswerAdvisor`** is the advisor that turns a plain `ChatClient` into a RAG application. In its *before* phase it takes the user's question, runs a similarity search against the vector store, and injects the matching documents into the prompt as context; the call then proceeds to the model, which answers grounded in that context. You attach it to a `ChatClient` and otherwise prompt exactly as you did in the previous section:

```java
ChatClient chatClient = builder
    .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).build())
    .build();

String answer = chatClient.prompt()
    .user("How do I reset my password?")
    .call()
    .content();
```

That's a complete RAG application. The advisor retrieves the relevant support documents and grounds the answer in them, without you writing any retrieval code in the call itself. You can tune what it retrieves by giving it a `SearchRequest` (the same `topK` and `similarityThreshold` knobs as before), supply a custom prompt template to control how the context is framed, or pass a filter expression per request:

```java
ChatClient chatClient = builder
    .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
        .searchRequest(SearchRequest.builder().topK(6).similarityThreshold(0.8).build())
        .build())
    .build();
```

Because the advisor is configured on the `ChatClient`, the rest of your application code stays the same fluent chain you already know, retrieval just becomes part of how that particular client behaves. This pairs naturally with the structured-output and system-prompt techniques from earlier: a support assistant might use a system prompt to set its tone, the `QuestionAnswerAdvisor` to ground its facts, and `.entity(...)` to return a typed answer.

## When You Need More: Modular RAG

`QuestionAnswerAdvisor` covers the common case beautifully, but real systems sometimes need more control over the retrieval flow. What if the user's question depends on earlier conversation turns ("and what about *its* second largest city?")? What if the query is phrased badly for search, or written in a different language than your documents? What if you want the assistant to politely refuse when nothing relevant is found, instead of guessing?

For these cases Spring AI offers a second, more flexible advisor, **`RetrievalAugmentationAdvisor`**, built on a **modular RAG architecture**. It breaks the retrieval flow into well-defined stages, pre-retrieval, retrieval, post-retrieval, and generation, that you can mix and match like building blocks:

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

Each stage has ready-made components. **Query transformers** reshape the question before searching: `CompressionQueryTransformer` folds conversation history into a standalone query, `RewriteQueryTransformer` rephrases an awkward question for better search results, and `TranslationQueryTransformer` translates it into your documents' language. **Query expanders** like `MultiQueryExpander` turn one question into several variations to widen the net. The **`VectorStoreDocumentRetriever`** does the actual search, and a **`ContextualQueryAugmenter`** controls generation, including whether to allow an answer when no context was found.

You don't need these on day one, and the lab uses the simpler `QuestionAnswerAdvisor`. But it's worth knowing the modular path exists: when your support assistant outgrows naive retrieval, you can reach for these components without abandoning the programming model.

## What's Next

You now have the full RAG mental model: models only know their training data, so you **retrieve** relevant facts and **augment** the prompt to **generate** grounded answers. That rests on **embeddings** (meaning as vectors), a **`VectorStore`** (search by similarity, filter by metadata), and an **ETL pipeline** (read, chunk, load your documents). At query time, an **advisor**, the interceptor that wraps a `ChatClient` call, ties it together: the **`QuestionAnswerAdvisor`** performs retrieval as part of the chain, with **modular RAG** waiting for when you need finer control. In the next section you'll build exactly this: a support assistant that indexes real documentation and answers questions from it.
