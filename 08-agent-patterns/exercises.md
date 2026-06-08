# Spring AI Support Assistant — Agentic AI / Tool Search Walkthrough

Continues from `INSTRUCTIONS-6.md`. Your Support Assistant has a growing tool inventory: three in-process ticket tools, plus every tool advertised by every connected MCP server.

There's a problem with that: **every chat call sends the full tool schema to the model**, regardless of whether any of those tools could plausibly help with this question. Each tool description is tokens you pay for on every request, the model has more chances to pick a wrong tool, and the prompt bloats as you add MCP servers.

The fix is the **Tool Search Tool**: an agentic pattern where the model is given a *single* meta-tool called something like `searchTools`, plus an index that embeds every real tool's description into the vector store. When the model needs to act, it calls `searchTools` with a natural-language query, gets back the top-K most relevant tools, and only those flow into the next turn. It's RAG, but for tools.

## 1. Add the Tool Search advisor dependency

Add to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-tool-search-advisor</artifactId>
</dependency>
```

This pulls in `ToolSearchToolCallingAdvisor`, `ToolIndex`, and the vector-store-backed default implementation (`VectorToolIndex`).

## 2. Configure the `ToolIndex`

The index is what stores the tool descriptions for similarity search. The default `VectorToolIndex` reuses your existing `VectorStore` — so the same `SimpleVectorStore` (or pgvector, depending on which path you took in INSTRUCTIONS-2) that holds the knowledge base chunks will also hold the tool descriptions, in separate vectors.

Open `SupportAssistantConfiguration.java` and add the bean:

```java
@Bean
ToolIndex toolIndex(VectorStore vectorStore) {
    return new VectorToolIndex(vectorStore);
}
```

Imports:

```java
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.index.vectorstore.VectorToolIndex;
```

On startup, the auto-configuration iterates every available `ToolCallback` (in-process + MCP) and embeds its name + description into the index. From this point on the model never sees those tools directly — it sees only `searchTools`.

## 3. Add `ToolSearchToolCallingAdvisor` to the `ChatClient`

Wire the advisor as a **default** on the `ChatClient` bean so every call goes through it. Update the `chatClient` factory method in `SupportAssistantConfiguration.java`:

```java
@Bean
ChatClient chatClient(ChatClient.Builder builder, ToolCallbackProvider tools, ToolIndex toolIndex) {
    var toolSearchAdvisor = ToolSearchToolCallingAdvisor.builder()
            .toolIndex(toolIndex)
            .maxResults(5)
            .build();

    return builder
            .defaultSystem("You are a Spring and AI expert.")
            .defaultTools(tools)
            .defaultAdvisors(toolSearchAdvisor)
            .build();
}
```

Import:

```java
import org.springframework.ai.chat.client.advisor.toolsearch.ToolSearchToolCallingAdvisor;
```

What changed:

- **`ToolIndex toolIndex` injected** alongside the existing `ToolCallbackProvider`.
- **`maxResults(5)`** — the advisor will inject the top 5 most relevant tools per turn. Tune this for your tool count and model context budget.
- **`.defaultAdvisors(toolSearchAdvisor)`** — registered once on the bean, so every `chatClient.prompt()` chain picks it up.

Behind the scenes, when the model decides to act, the advisor:
1. Intercepts the call before tools are sent to the model.
2. Replaces the full tool list with just the `searchTools` meta-tool.
3. When the model calls `searchTools("create a ticket about login failures")`, runs a similarity search against the `ToolIndex`.
4. Surfaces the matching tools to the model on the next turn so it can actually invoke them.

The full list — `createTicket`, `retrieveTickets`, `retrieveOpenTickets`, `spring-releases_fetchReleasesInfo`, etc. — is no longer in every prompt.

## 4. Wire conversation memory through `conversationId`

The advisor is multi-turn by design: turn 1 is "search for tools", turn 2 is "now actually call them". For that to work, the framework needs `ChatMemory` plus a **conversation id** so it can correlate the two turns. `ChatMemory` is auto-configured (`MessageWindowChatMemory` + in-memory repository) once the advisor is on the classpath; the only thing you have to supply is the conversation id per request.

See <https://docs.spring.io/spring-ai/reference/2.0/api/tools.html#tool-search-tool> for the framework contract.

### Accept the conversation id as a header

Update `SupportAssistantController.java` to read `X-Conversation-Id` from the request, falling back to a generated UUID when the client doesn't supply one:

```java
package com.example.support_assistant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
class SupportAssistantController {

    private final SupportAssistantService service;

    SupportAssistantController(SupportAssistantService service) {
        this.service = service;
    }

    @GetMapping(path = "/api/{version}/chat")
    SupportResponse chat(@RequestParam String query,
                         @RequestHeader(value = "X-Conversation-Id", required = false) String conversationId) {
        return service.generateResponse(query, conversationId != null ? conversationId : UUID.randomUUID().toString());
    }
}
```

`@RequestHeader.defaultValue` only takes string literals, hence the null-check + `UUID.randomUUID()` fallback. Clients that *do* want a continuous conversation (the typical case for an agentic assistant — turn N+1 needs to see turn N's history) supply a stable id.

### Pass the id into the chain

Update `SupportAssistantService.generateResponse` to accept the id and forward it via the advisor param. The framework looks for it under `ChatMemory.CONVERSATION_ID`:

```java
SupportResponse generateResponse(String query, String conversationId) {
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
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .tools(supportTicketService)
            .call()
            .entity(SupportResponse.class);
}
```

Import:

```java
import org.springframework.ai.chat.memory.ChatMemory;
```

The `.advisors(a -> a.param(...))` call passes the conversation id to **every** registered advisor — both your RAG advisor and the tool-search advisor pick it up. Each call with the same `conversationId` lands in the same memory window; a fresh UUID gives the client a clean slate.

## 5. Test it

Restart the app and send a request that requires a tool — without setting a header, so the controller mints a fresh UUID:

```bash
curl -G "http://localhost:8080/api/1.0/chat" \
     --data-urlencode "query=Please open a high-priority ticket: SSO login returns 502 on the Tanzu portal."
```

With `logging.level.org.springframework.ai=debug` enabled (from INSTRUCTIONS-5), you'll see in the logs:
1. A first model call where only `searchTools` is advertised.
2. The model emitting a `searchTools` call with the user's intent as the query.
3. The advisor running a similarity search against the `ToolIndex` and returning the top 5 hits — `createTicket` should be one of them.
4. A second model call with just those matched tools advertised; the model then picks `createTicket`.

Now try a multi-turn flow by reusing the same id:

```bash
CID=$(uuidgen)

curl -G "http://localhost:8080/api/1.0/chat" \
     -H "X-Conversation-Id: $CID" \
     --data-urlencode "query=What's the latest release of Spring Boot?"

curl -G "http://localhost:8080/api/1.0/chat" \
     -H "X-Conversation-Id: $CID" \
     --data-urlencode "query=Please file a ticket asking the team to upgrade us to that version. High priority."
```

The second call sees the conversation history from the first, so "that version" refers to the Spring Boot release the model fetched a moment ago — and the tool search advisor still picks the right action (`createTicket`) from the indexed pool.

A pure RAG question (no tool) confirms tool search adds zero overhead when no action is needed — the model never calls `searchTools`:

```bash
curl -G "http://localhost:8080/api/1.0/chat" \
     -H "X-Conversation-Id: $(uuidgen)" \
     --data-urlencode "query=What is Tanzu Spring Runtime?"
```

---

## Recap

| Step | What changed | Key API |
|------|--------------|---------|
| 1 | Add tool-search advisor dep | `spring-ai-tool-search-advisor` |
| 2 | Embed tool descriptions into the vector store | `ToolIndex`, `VectorToolIndex` |
| 3 | Replace full tool list with `searchTools` meta-tool | `ToolSearchToolCallingAdvisor`, `.defaultAdvisors(...)` |
| 4 | Multi-turn correlation via conversation id | `@RequestHeader("X-Conversation-Id")`, `ChatMemory.CONVERSATION_ID` |
| 5 | Verify single-turn and multi-turn behavior | `curl -H "X-Conversation-Id: ..."` |