## Tools Were Great, Until Everyone Had to Write Their Own

In the tool-calling section you gave the model hands: it could look up an order or open a ticket by calling methods inside your application. But notice the limitation, those tools lived *in your codebase*. You wrote them, you wired them, they ran in your process. That's fine when the capability is yours, but most of the capabilities an assistant could use belong to *someone else*: a GitHub repository, a Slack workspace, a Jira board, a filesystem, a payments API, an internal service another team owns.

If every application has to hand-write a tool wrapper for every external system it wants to reach, you get the same integration sprawl that has plagued software forever, N applications each writing custom glue for M systems. The team that runs the ticketing service has to re-explain its API to every AI app that wants to use it, in every framework, over and over.

What if, instead, a capability provider could describe its tools *once*, in a standard way, and any AI application could discover and use them without bespoke code? That's the idea behind the **Model Context Protocol**.

## What MCP Is

The **Model Context Protocol (MCP)** is an open, standardized protocol for connecting AI applications to external tools and data. If REST standardized how services talk over HTTP, MCP standardizes how AI applications reach the outside world, a common interface, sometimes described as "a bridge between your AI models and the real world." Instead of every app inventing its own integration, both sides speak MCP.

The architecture is **client–server**, and the split is the whole point:

- An **MCP server** is a program that *exposes* capabilities, it publishes a set of tools (and other things) that any client can discover and call.
- An **MCP client** lives inside an AI application and *consumes* those capabilities, connecting to one or more servers and making their tools available to the model.

This decouples who *provides* a capability from who *uses* it. The ticketing team writes one MCP server; your support assistant, a colleague's chatbot, and an off-the-shelf AI client can all connect to it, with no per-consumer integration work. A whole ecosystem of ready-made servers already exists for common systems, and you can drop them into an app without writing the integration yourself.

An MCP server can expose three kinds of things:

- **Tools** — functions the model can call to fetch data or take action (the same idea as Spring AI tools, now provided remotely).
- **Resources** — URI-addressable data the application can read (files, records, documents).
- **Prompts** — reusable prompt templates the server offers to clients.

Tools are by far the most common, and the ones that connect most directly to what you've already built.

## Transports: Local and Remote

Because client and server are separate programs, they need a channel to talk over. MCP defines a few **transports**, and Spring AI supports them all:

- **STDIO** — the server runs as a local child process and communicates over standard input/output. Ideal for tools that run on the same machine (a filesystem server, a local CLI).
- **HTTP-based transports** — for servers running as independent, possibly remote, processes. The original **SSE** (Server-Sent Events) transport and the newer **Streamable HTTP** transport (more on that below) let many clients connect to a networked server.

The transport is a deployment detail; the tools a server exposes look the same to the model regardless of how the bytes travel.

## The MCP Client: Borrowing Other People's Tools

The consumer side is where MCP pays off immediately, and where it connects cleanly to the tool-calling you already know. Add the client starter:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```

You declare the servers to connect to in configuration, by transport. For example, an SSE server and a local STDIO server:

```yaml
spring:
  ai:
    mcp:
      client:
        sse:
          connections:
            ticketing-server:
              url: http://localhost:8080
        stdio:
          connections:
            filesystem:
              command: npx
              args: ["-y", "@modelcontextprotocol/server-filesystem", "/data"]
```

On startup the client connects to each server, **discovers the tools it exposes, and turns each one into a Spring AI `ToolCallback`**, the exact same abstraction you met in the tool-calling section. Spring AI hands them to you through a `ToolCallbackProvider` bean, which you pass to a `ChatClient` like any other tools:

```java
ChatClient chatClient = ChatClient.create(chatModel)
    .defaultTools(toolCallbackProvider.getToolCallbacks())
    .build();
```

That's the elegant part: as far as your `ChatClient` and the auto-registered `ToolCallingAdvisor` are concerned, a remote MCP tool is indistinguishable from a local `@Tool` method. The model decides to call it, the framework runs the loop, and the call happens to travel over MCP to another process. Your support assistant can now use tools it never had to implement.

A few practical capabilities the starter provides:

- **Multiple servers at once**, across mixed transports, all merged into one tool set. When two servers expose a tool with the same name, Spring AI automatically prefixes them to avoid collisions.
- **Sync or async** clients via `spring.ai.mcp.client.type` (`SYNC` is the default; `ASYNC` for reactive apps).
- **Tool filtering** through an `McpToolFilter`, so you can include or exclude specific tools rather than exposing everything a server offers, useful for trimming a large tool set down to what your assistant actually needs.

## The MCP Server: Sharing *Your* Tools

The other side of the coin: you can turn your own Spring application into an MCP server, so the capabilities you've built become available to *other* AI applications. The ticket-creation logic in our support assistant, for instance, could be exposed so any MCP-capable client in the organization can file tickets through it.

Pick a server starter for your transport, STDIO for a local tool, or WebMVC/WebFlux for a networked one:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

Then you expose capabilities declaratively with annotations, the same spirit as `@Tool`, but for the MCP protocol:

```java
@Component
class TicketingTools {

    @McpTool(name = "openTicket", description = "Open a support ticket and return its number")
    String openTicket(
            @McpToolParam(description = "Short summary of the problem") String summary) {
        return ticketService.create(summary).number();
    }
}
```

The auto-configuration scans for these annotated beans, generates the JSON schema for each tool automatically, and registers them with the MCP server, no protocol plumbing on your part. Alongside `@McpTool` you can expose resources with `@McpResource` (URI-addressable data) and prompt templates with `@McpPrompt`. As on the client, you choose `SYNC` or `ASYNC` mode, and only methods matching that mode are registered.

So Spring AI is symmetric: the same application can be an MCP *client* (consuming others' tools) and an MCP *server* (offering its own), often at the same time.

## A Note on Streamable HTTP

You'll see two HTTP transports mentioned: **SSE** and **Streamable HTTP**. Streamable HTTP is the newer one (introduced in a later MCP spec revision) and is intended to **replace SSE**. It carries messages over ordinary HTTP POST/GET with optional SSE streaming for server-to-client messages, which makes it a better fit for cloud-native deployments: many concurrent clients, real-time notifications, and load-balanced microservices. You enable it with a single property:

```properties
spring.ai.mcp.server.protocol=STREAMABLE
```

It comes in **stateful** and **stateless** flavors (`STREAMABLE` vs `STATELESS`); the stateless variant drops per-session state, which suits horizontally-scaled, cloud-native servers. For new networked servers, prefer Streamable HTTP over the older SSE transport.

## Securing MCP: The MCP Security Module

Once your tools live behind a network boundary, served to clients you may not control, access control matters. An MCP server that can open tickets or read files shouldn't be open to anyone who finds the URL. The community **Spring AI MCP Security** module addresses this by bringing **Spring Security** to MCP, with **OAuth 2.0 and API-key** authentication for both servers and clients.

On the server side it lets your MCP server act as an OAuth 2.0 resource server that validates JWT tokens (or checks API keys) before any tool runs, and because it builds on standard Spring Security, you can guard individual tools with familiar method-level rules:

```java
@PreAuthorize("isAuthenticated()")
@McpTool(name = "openTicket", description = "Open a support ticket")
String openTicket(@McpToolParam(description = "Summary") String summary) { ... }
```

On the client side it handles obtaining and attaching tokens (authorization-code, client-credentials, or a hybrid flow) so your application can call secured servers on behalf of a user or as a machine. It's worth knowing this exists rather than memorizing its API: it's a **community project, still in development and not yet officially part of Spring AI**, so treat it as the emerging answer to MCP security rather than a finished, blessed module.

## What's Next

MCP is the standard that lets AI applications reach beyond their own code. Where tool calling let the model call *your* methods, MCP lets it call *anyone's*, through a common client–server protocol over STDIO or HTTP. As an **MCP client**, Spring AI discovers a server's tools and exposes them as ordinary `ToolCallback`s, so remote tools drop straight into the `ChatClient` you already use. As an **MCP server**, a few annotations (`@McpTool`, `@McpResource`, `@McpPrompt`) turn your application into a capability provider for others, with **Streamable HTTP** the modern transport for networked deployments and the **MCP Security** module emerging to lock it all down. In the next section you'll connect your support assistant to an MCP server and watch it use tools it never had to write.
