# Agentic Patterns — Hands-on Exercises

Your assistant now has a growing set of tools: three in-process ticket tools plus every tool from every connected MCP server (like the `fetchReleasesInfo` tool from the Spring Releases MCP server).

That creates a problem: **every chat call sends the full tool schema to the model**, even when none of those tools can help. Every tool description costs tokens on every request, the model has more chances to pick the wrong tool, and the prompt grows as you add MCP servers.

The fix is the **Tool Search Tool**: the model gets a single meta-tool, `searchTools`, plus an index that embeds every real tool's description into the vector store. When the model needs to act, it calls `searchTools` with a natural-language query, gets back the most relevant tools, and only those flow into the next turn. It's RAG, but for tools.

Your starting point is the [`sample-app/`](sample-app/), the MCP-connected assistant, plus the [`spring-releases-mcp-server/`](spring-releases-mcp-server/) from the previous lab.

## 1. Start the Spring Releases MCP server

```bash
cd spring-releases-mcp-server
./mvnw spring-boot:run
```

It starts on port 8090 with its `fetchReleasesInfo` tool.

## 2. Add the Tool Search advisor dependency

In the assistant's `pom.xml`, after the `spring-ai-starter-mcp-client` dependency:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-tool-search-advisor</artifactId>
</dependency>
```

This pulls in `ToolSearchToolCallingAdvisor`, `ToolIndex`, and the vector-store-backed default implementation `VectorToolIndex`.

## 3. Configure the `ToolIndex`

The index stores the tool descriptions for similarity search. The default `VectorToolIndex` reuses your existing `VectorStore`, so the same `SimpleVectorStore` that holds the knowledge-base chunks also holds the tool descriptions, in separate vectors.

Add the bean to `SupportAssistantConfiguration`:

```java
@Bean
ToolIndex toolIndex(VectorStore vectorStore) {
    return new VectorToolIndex(vectorStore);
}
```

Add the imports:

```java
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.index.vectorstore.VectorToolIndex;
```

On startup the auto-configuration goes through every available `ToolCallback` (in-process and MCP) and embeds its name and description into the index. From then on the model never sees those tools directly — it sees only `searchTools`.

## 4. Add the `ToolSearchToolCallingAdvisor`

Register the advisor as a default on the `ChatClient` bean so every call goes through it. Update the `chatClient` factory method — inject `ToolIndex`, build the advisor, and add it to `defaultAdvisors`:

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder,
                             @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt,
                             ChatMemory chatMemory,
                             ToolCallbackProvider tools,
                             ToolIndex toolIndex) {
    var toolSearchAdvisor = ToolSearchToolCallingAdvisor.builder()
            .toolIndex(toolIndex)
            .maxResults(5)
            .build();

    return builder
            .defaultSystem(systemPrompt)
            .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
            .defaultAdvisors(
                    new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE),
                    MessageChatMemoryAdvisor.builder(chatMemory).build(),
                    toolSearchAdvisor)
            .defaultTools(tools)
            .build();
}
```

Add the import:

```java
import org.springframework.ai.chat.client.advisor.toolsearch.ToolSearchToolCallingAdvisor;
```

`maxResults(5)` injects the 5 most relevant tools per turn. Behind the scenes the advisor replaces the full tool list with just `searchTools`; when the model calls it, the advisor runs a similarity search against the `ToolIndex` and surfaces the matches on the next turn. The full list (`createTicket`, `retrieveTickets`, `retrieveOpenTickets`, `fetchReleasesInfo`, …) is no longer in every prompt.

The tool-search advisor is multi-turn (turn 1 searches, turn 2 acts), so it relies on the conversation memory already wired into the assistant.

## 5. Test it

Start the assistant. Send a request that needs a tool (no header, so a fresh conversation id is minted):

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Please open a high-priority ticket: SSO login returns 502 on the Tanzu portal."
```

With debug logging on you'll see: a first model call advertising only `searchTools`; the model calling it with the user's intent; the advisor returning the top 5 hits (with `createTicket` among them); then a second model call with only those tools, from which the model picks `createTicket`.

Now a multi-turn flow reusing the same id:

```bash
CID=$(cat /proc/sys/kernel/random/uuid)

curl -G "http://localhost:8080/api/v1/chat" -H "X-Conversation-Id: $CID" \
     --data-urlencode "query=What's the latest release of Spring Boot?"

curl -G "http://localhost:8080/api/v1/chat" -H "X-Conversation-Id: $CID" \
     --data-urlencode "query=Please file a ticket asking the team to upgrade us to that version. High priority."
```

The second call sees the first turn's history, so "that version" resolves to the release just fetched, and the tool search advisor still picks the right action.

Finally, a pure RAG question confirms tool search adds no overhead when no action is needed — the model never calls `searchTools`:

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=What is Tanzu Spring Runtime?"
```

## Recap

Your assistant now scales to many tools without bloating every prompt: it discovers the right tools on demand via the Tool Search Tool, grounded by RAG and carried across turns by conversation memory. That's the finished support assistant — see [`99-summary`](../../99-summary/summary.md) for the consolidated application.
