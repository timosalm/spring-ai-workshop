## What is MCP?

**Model Context Protocol (MCP)** is an open protocol that standardizes how AI applications connect to external tools and data sources. It provides:

- A **standardized interface** for tool execution and context retrieval
- **Transport-agnostic communication** (STDIO, SSE, Streamable-HTTP)
- **Interoperability** between different AI applications (hosts) and tool providers (servers)

Think of MCP as a universal adapter: any MCP-compatible host can connect to any MCP server without custom integration code for each pairing.

## Architecture

MCP follows a client-server model:

```
┌─────────────────────────────────────────────┐
│                  MCP Host                   │
│         (Claude Desktop, Cursor, ...)       │
│  ┌─────────────┐         ┌─────────────┐    │
│  │ MCP Client 1│         │ MCP Client 2│    │
│  └──────┬──────┘         └──────┬──────┘    │
└─────────┼───────────────────────┼───────────┘
          │ MCP Protocol          │ MCP Protocol
          ▼                       ▼
┌─────────────────┐     ┌─────────────────┐
│   MCP Server    │     │   MCP Server    │
│ (Your Spring    │     │  (Other tools)  │
│  AI App)        │     │                 │
└─────────────────┘     └─────────────────┘
```

- **MCP Host** — the AI application (e.g., Claude Desktop, Cursor) that coordinates connections to one or more servers
- **MCP Client** — a component within the host that maintains a dedicated connection to one server
- **MCP Server** — a program that exposes tools, resources, and prompts to MCP clients

## Why MCP vs. Direct Tool Integration?

Without MCP, every AI application needs a custom integration for every tool provider.

| Without MCP | With MCP |
|-------------|----------|
| Custom integration per client | Single MCP server serves all clients |
| Tight coupling between host and tools | Loose coupling via protocol |
| Duplicate tool implementations | Reusable, shareable tool definitions |

Use **direct tools** (`.tools()`) when tools are internal to a single application. Use an **MCP server** when you want to expose tools to multiple AI hosts or external consumers.

## Spring AI MCP Server Support

Spring AI provides two starter options:

- `spring-ai-starter-mcp-server-webmvc` — Streamable-HTTP transport (WebMVC)
- `spring-ai-starter-mcp-server-webflux` — Streamable-HTTP transport (WebFlux)

Key features of the auto-configuration:
- **Automatic tool discovery** — existing `@Tool` methods are exposed via `ToolCallbackProvider`
- **Multiple transport options** — STDIO (for local subprocess use), SSE (deprecated), Streamable-HTTP
- **Alternative annotations** — `@McpTool`, `@McpResource`, `@McpPrompt` for MCP-specific beans without a `ToolCallbackProvider`

## Transport Types

| Transport | Use Case |
|-----------|----------|
| STDIO | Local tools: MCP host launches the server as a subprocess |
| Streamable-HTTP | Remote servers accessible over the network; supports multiple concurrent clients |

## MCP-Compatible Hosts

Once your MCP server is running, it can be used by any MCP-compatible host, including:
- Claude Desktop (Anthropic)
- Cursor IDE
- Open WebUI
- Any custom application using Spring AI's MCP client support
