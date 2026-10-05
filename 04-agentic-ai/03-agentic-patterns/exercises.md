# Agentic Patterns Lab

Your support assistant has a growing number of tools. Three ticket tools run inside the application, and every MCP server you connect adds more on top, like the `fetchReleasesInfo` tool of the Spring Releases MCP server from the previous lab.

That creates a problem. **Every chat call sends the full list of tool definitions to the model**, even when none of those tools can help. Every tool description costs tokens on every request, the model has more chances to pick the wrong tool, and the prompt grows with each MCP server you add.

In this lab you put the **Tool Search Tool** in front of that list, so the model only sees the tools that fit the current request. The model gets a single tool, `toolSearchTool`, plus an index that holds the descriptions of all the real tools. When the model needs to act, it calls `toolSearchTool` with a query in plain language, gets back the most relevant tools, and only those are offered in the next call. It is RAG, but for tools.

Read [Agentic Patterns](agentic-patterns.md) and [Agentic Patterns With Spring AI](agentic-patterns-spring-ai.md) first.

## Before You Start

This lab works with two projects in this folder.

- [`sample-app/`](sample-app/) is the support assistant from the MCP lab.
- [`spring-releases-mcp-server/`](spring-releases-mcp-server/) is the Spring Releases MCP server from the MCP lab.

Both keep the OAuth 2.0 security from the MCP lab behind the `mcp-security` profile. This lab runs them without that profile, so you do not need Keycloak.

All paths in this lab are relative to the folder of this lab. You work with three terminals.

- **Terminal 1** runs the support assistant in `sample-app/`.
- **Terminal 2** sends requests with `curl`.
- **Terminal 3** runs the MCP server in `spring-releases-mcp-server/`.

## 1. Start the Spring Releases MCP Server

Start the MCP server in **Terminal 3**, so its `fetchReleasesInfo` tool is available to the support assistant.

```bash
cd spring-releases-mcp-server
./mvnw spring-boot:run
```

You should see the embedded MCP server start on port 8090 and log one registered tool at startup.

## 2. Add the Tool Search Advisor Dependency

Add the following dependency to `sample-app/pom.xml`, right after the `mcp-client-security-spring-boot` dependency.

```xml

		<dependency>
			<groupId>org.springframework.ai</groupId>
			<artifactId>spring-ai-starter-tool-search-advisor</artifactId>
		</dependency>
```

The starter brings the `ToolSearchToolCallingAdvisor` together with its auto configuration, plus the `ToolIndex` interface and `VectorToolIndex`, the index implementation that searches by meaning.

## 3. Enable Tool Search

With the starter you do not write any wiring code. Three properties are enough. Append the following lines to `sample-app/src/main/resources/application.properties`.

```properties

spring.ai.chat.client.tool-search-advisor.enabled=true
spring.ai.chat.client.tool-search-advisor.tool-index-type=vector
spring.ai.chat.client.tool-search-advisor.max-results=5
```

Here is what each of them does.

- **`enabled=true`** is all the wiring you need. The auto configuration builds a `ToolSearchToolCallingAdvisor` and adds it to every `ChatClient.Builder` in the application, so your `chatClient` bean in `SupportAssistantConfiguration.java` stays exactly as it is, and every `chatClient.prompt()` chain runs through the advisor. Your tools stay registered on the client, but from now on `createTicket`, `retrieveTickets`, `retrieveOpenTickets`, and `fetchReleasesInfo` are no longer sent with every prompt. The model has to search for them first.
- **`tool-index-type=vector`** creates a `VectorToolIndex` bean that works with the `VectorStore` you already have. The same `SimpleVectorStore` that holds your knowledge base documents now also holds the tool descriptions, stored as separate vectors. At startup the auto configuration walks through every available `ToolCallback`, the local ones as well as the ones from your MCP servers, and adds the name and the description of each of them to the index.
- **`max-results=5`** adds only the five best matching tools to the next model call. Pick that number based on how many tools you have and how much context you want to spend on them.

The advisor needs more than one model call to do its work. In the first call the model searches for tools, and in the second one it calls them. This only works because the `ChatMemory` you configured earlier keeps the result of the search in the conversation.

If the properties are not enough for your use case, the article explains how to use the `spring-ai-tool-search-advisor` module without the starter and build the index and the advisor yourself.

## 4. Start the Support Assistant

In **Terminal 1**, export your key and start the support assistant.

```bash
cd sample-app
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

## 5. Test It

Send a request in **Terminal 2** that needs a tool. Do not set a header, so the controller creates a fresh conversation id.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Open a high-priority ticket for an auth issue with the Spring Enterprise Repository."
```

With `logging.level.org.springframework.ai=debug` already enabled in `application.properties`, you can follow the whole flow in the logs of the assistant in Terminal 1.

1. A first model call where `toolSearchTool` is the only tool that is offered.
2. The model calls `toolSearchTool` with the intent of the user as the query.
3. The advisor searches the `ToolIndex` and returns the top 5 hits, and `createTicket` should be one of them.
4. A second model call that offers only those matched tools, and the model picks `createTicket`.

Whether the model completes the action depends on the model. The search for tools competes with the RAG prompt and the structured output, and a model can sometimes answer from the retrieved context instead of following the two steps. If that happens, phrase the request more clearly as an action.

Now run a conversation with two turns that reuses the same id.

```bash
CID=$(uuidgen) # if uuidgen is not available, just use a random string, e.g. CID=test
```

```bash
curl -G "http://localhost:8080/api/v1/chat" -H "X-Conversation-Id: $CID" \
     --data-urlencode "query=What is the latest release of Spring Boot? Please look it up."

curl -G "http://localhost:8080/api/v1/chat" -H "X-Conversation-Id: $CID" \
     --data-urlencode "query=Please file a ticket asking the team to upgrade us to that version. High priority."
```

The second call sees the history of the first turn, so "that version" resolves to the release that was just fetched, and the Tool Search advisor still finds the right tool for the action.

Finally, ask a question that needs no tool at all.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=What are the key features of VMware Tanzu Spring?"
```

The model answers from the knowledge base and never calls `toolSearchTool`, so tool search costs you nothing when there is nothing to do.

## Recap

Your assistant now scales to many tools without a bigger prompt on every request. It finds the right tools on demand with the Tool Search Tool, grounded by RAG and carried across turns by conversation memory. The [`sample-app/`](../../99-summary/sample-app/) in the summary folder contains the result of this lab. In the next lab you try the experimental agentic patterns.
