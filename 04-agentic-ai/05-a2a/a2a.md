# Agent2Agent Protocol (A2A)

With MCP your assistant can reach tools and data that live outside its own codebase. But look at what sits on the other side of an MCP connection. A tool does exactly what it is told, returns a result, and is done. It does not plan, it does not ask back, and it does not work on something for an hour.

Now imagine that the capability you need is not a tool but a whole agent. The billing team has built an agent that understands invoices and contracts, and the platform team has one that can analyze a failed deployment. Each of them has its own model, its own prompts, and its own tools, and it may even be written in Python with a completely different framework. You could wrap such an agent as an MCP tool, but you would lose most of what makes it useful. The call would have to finish in one step, the agent could not ask your user a question in the middle of its work, and there would be no standard way to follow its progress.

This is the problem the **[Agent2Agent (A2A) protocol](https://a2a-protocol.org)** solves. It is an open protocol for communication between independent agents. One agent can find out what another agent can do, hand it a piece of work, follow the progress of that work, and receive the results, no matter which vendor, language, or framework each of them is built with.

A2A was introduced by Google in April 2025, but today it is part of the [Agentic AI Foundation (AAIF)](https://aaif.io), the same vendor neutral home as MCP. Just like MCP, it is language neutral, and there are official SDKs for Python, JavaScript, Java, Go, and .NET. An agent you build in Java with Spring AI can work together with an agent that somebody else built in Python, and neither side needs to know how the other one works inside.

That last point is a design goal of its own. A2A treats every agent as **opaque**. Agents exchange tasks and results, but they never share their internal memory, their prompts, or their tools. This protects the intellectual property of each team and keeps the systems loosely coupled, so the billing team can change its agent completely without breaking yours.

## Core Actors

A2A has three core actors in A2A interactions. The **user** is the person or system that starts a request. The **A2A client**, also called client agent, acts on behalf of the user and  initiates communication using the A2A protocol. The **A2A server**, also called the remote agent, receives the request and does the work, and returns results or status updates.

The roles are not fixed to an application. Your support assistant can be a client when it delegates a billing question, and at the same time a server that other agents call for Spring questions. And a remote agent that receives a task can itself call further agents to complete it.

## Discovery With the Agent Card

Before a client talks to a remote agent, it needs to know what that agent can do and how to reach it. For this every A2A server publishes an **Agent Card**, a JSON document that works like a business card for the agent. By convention it is served at the well known path `/.well-known/agent-card.json`, so a client only needs the base URL of an agent to find everything else.

The card contains the name, a description, and the version of the agent, and the URLs and protocol bindings under which it can be reached. It lists the **capabilities** the server supports, such as streaming and push notifications, the content types it accepts and produces, and the **security schemes** a client has to use. The most important part is the list of **skills**. Each skill has an id, a name, a description, tags, and example requests, and this is what a client agent reads to decide whether a remote agent fits a task.

Here is an Agent Card that the support assistant from the labs could publish in production.

```json
{
  "protocolVersion": "0.3.0",
  "name": "Spring Support Assistant",
  "description": "A support agent for the Spring framework that answers questions with links to the official docs, checks Spring versions for known vulnerabilities, and manages support tickets.",
  "url": "https://support-agent.example.com/",
  "preferredTransport": "JSONRPC",
  "version": "1.0.0",
  "provider": {
    "organization": "Example Corp",
    "url": "https://www.example.com"
  },
  "documentationUrl": "https://support-agent.example.com/docs",
  "capabilities": {
    "streaming": false,
    "pushNotifications": false
  },
  "securitySchemes": {
    "oauth2": {
      "type": "oauth2",
      "description": "OAuth 2.0 access tokens issued by the Example Corp authorization server",
      "flows": {
        "authorizationCode": {
          "authorizationUrl": "https://auth.example.com/oauth2/authorize",
          "tokenUrl": "https://auth.example.com/oauth2/token",
          "scopes": {
            "support.read": "Ask questions and list support tickets",
            "tickets.write": "Create support tickets"
          }
        }
      }
    }
  },
  "security": [
    { "oauth2": ["support.read"] }
  ],
  "defaultInputModes": ["text/plain"],
  "defaultOutputModes": ["application/json"],
  "skills": [
    {
      "id": "spring_questions",
      "name": "Answer Spring questions",
      "description": "Explains Spring and Spring AI features based on the official documentation and knows the current Spring project releases and their support status.",
      "tags": ["spring", "spring-boot", "spring-ai", "documentation"],
      "examples": [
        "How do I configure chat memory in Spring AI?",
        "Which Spring Boot versions are still supported?"
      ]
    },
    {
      "id": "support_tickets",
      "name": "Manage support tickets",
      "description": "Creates new support tickets and lists all or only the open ones.",
      "tags": ["support", "tickets"],
      "security": [
        { "oauth2": ["support.read", "tickets.write"] }
      ],
      "examples": [
        "Open a high priority ticket because our application does not start after the upgrade.",
        "Which support tickets are still open?"
      ]
    }
  ]
}
```

The assistant accepts questions as plain text and answers with JSON, the same structured response with a category and an answer that its REST endpoint returns. Each skill maps to something the assistant already does. The first one comes from the RAG setup and the Spring Releases MCP server, the second one from the `cve-lookup` Agent Skill, and the third one from the ticket tools. A client agent never sees these tools, only the skills.

The rest of the card is about running the agent in production. The agent is only reachable over HTTPS, and the provider and documentation URL tell other teams who owns it and where to learn more. The `securitySchemes` section declares that callers need an OAuth 2.0 access token from the authorization server of the company, plus the supported flows. The `security` section requires the `support.read` scope for every call, and the ticket skill adds its own requirement for `tickets.write`, because creating tickets changes data. The card only describes these rules. The server still has to check the token on every request, for example with Spring Security as an OAuth 2.0 resource server.

Notice how this differs from MCP. An MCP client first connects and then asks the server for its tools. An A2A client reads the card *before* it connects, so it can choose the right agent without talking to all of them. A server can also offer an extended Agent Card that only authenticated clients receive, for example to show skills that are not meant for everyone.

## Messages, Parts, and Artifacts

The communication itself is built from a few simple objects.

A **Message** is one turn of the conversation. It has a role, which is either `user` for the client side or `agent` for the remote agent, and it carries one or more **Parts**. A part is the actual content, and it can be text, a file, or structured JSON data. Because of this, agents can exchange more than text. A client can send a log file together with a question, and a remote agent can answer with a structured result that the client processes without parsing text.

An **Artifact** is a result that the remote agent produces as the outcome of its work, such as a report, a generated document, or a data set. It is built from *Parts* as well. The difference is that messages are for talking about the work, while artifacts are the work itself.

## Tasks

The central concept of A2A is the **Task**. A task is the unit of work that a remote agent performs for a client. Every task has a unique id that the server creates, a context id, a current status, the artifacts it has produced so far, and optionally the history of the messages that belong to it.

A task moves through a defined lifecycle. It starts as *submitted* when the server has accepted it, and changes to *working* while the agent is busy. It can end in one of four final states. *Completed* means the work is done and the artifacts are ready. *Failed* means an error stopped it, *canceled* means the client stopped it, and *rejected* means the agent decided not to do it at all. Once a task has reached one of these states it never changes again, so a follow up request always starts a new task.

Two further states are what make A2A different from a simple remote call. In the **input required** state the agent has paused and needs more information from the client before it can continue, for example which Spring Boot version a question is about. In the **auth required** state it needs additional credentials. The client answers with a new message for the same task, and the agent continues where it stopped. This is the Human-in-the-Loop pattern from the previous sections, but across two independent agents.

Not every request needs a task. For a quick answer the remote agent can simply reply with a message, and no task is created. Tasks are for work that takes longer, has several steps, or produces artifacts.

The **context id** groups related tasks and messages into one conversation. When a client sends several requests with the same context id, the remote agent knows that they belong together, similar to the conversation id you use with chat memory in your own assistant.

## Following the Progress of a Task

A task can take seconds or hours, so A2A offers three ways for a client to follow it until it is done.

The simplest one is **request and response**. The client sends a message and receives either a direct answer or a task. If the task is not finished yet, the client asks for its current state again later.

With **streaming** the server sends updates over Server-Sent Events while it works, such as status changes and artifacts that arrive piece by piece. The client sees the progress in real time, and if the connection breaks it can subscribe to the same task again. A server announces this with the streaming capability in its Agent Card.

For work that takes very long, keeping a connection open is not practical. With **push notifications** the client registers a webhook URL, and the server calls it whenever the task changes. The client does not have to stay connected at all.

Besides the primary operation for initiating agent interactions, a client can work with a task that is already running. It can ask for the current state of a task, cancel a task it no longer needs, and manage the webhooks of a task for push notifications.

## Protocol Bindings

A2A separates the data model from the way it travels, just like MCP separates its data layer from its transports. The specification defines three **protocol bindings**. **JSON-RPC 2.0** over HTTP is the most common one and uses the same message format you know from MCP. **gRPC** suits high performance communication between services, and **HTTP with JSON** follows a classic REST style. All three carry the same objects, and an agent lists the bindings it supports in its Agent Card.

Unlike MCP there is no local transport like stdio. A2A assumes that agents are independent services that run somewhere on a network.

## Security

A2A was designed for companies from the start, so it builds on the standards your applications already use instead of inventing new ones. All production traffic must use HTTPS. The Agent Card declares which authentication schemes the server accepts, and the options follow the security schemes of OpenAPI. These are API keys, HTTP authentication such as bearer tokens, OAuth 2.0, OpenID Connect, and mutual TLS.

The client gets its credentials outside of the protocol, for example from an OAuth authorization server, and sends them in the normal HTTP headers of each request. The credentials never appear inside the A2A messages. The server authenticates every request and then decides which skills and which data the caller may use, just like any other API.

## MCP and A2A

A common question is whether A2A replaces MCP. It does not. The two protocols solve different problems and are designed to work together. MCP connects an agent to tools and data. A2A connects an agent to other agents.

A good way to picture this is your support assistant. It uses MCP to fetch Spring release information from a server, which is a tool that returns a result and is done. When a customer asks about an invoice, it hands the question to the billing agent over A2A, because that agent needs to plan its own steps and may have to ask a question back. Inside, the billing agent may itself use MCP to reach its database. So an application uses A2A to talk to other agents, and each agent uses MCP for its own tools.

| | MCP | A2A |
|---|---|---|
| **Connects** | An agent to tools, data, and prompts | An agent to other agents |
| **Behavior of the server** | Does exactly what it is told | Reasons, plans, and keeps its own state |
| **Interaction** | A tool call with defined arguments and a result | A task with a lifecycle, messages, and artifacts, often over several turns |
| **Long running work** | Added with the tasks of the `2026-07-28` revision | Part of the design from the start |
| **Transports** | stdio and Streamable HTTP | JSON-RPC, gRPC, and HTTP with JSON |
| **Progress updates** | Streaming over Streamable HTTP | Streaming with SSE and push notifications with webhooks |
| **Security** | OAuth 2.1 for the HTTP transports | Schemes declared in the Agent Card, such as OAuth 2.0, OpenID Connect, API keys, and mutual TLS |
| **Internal details** | Tool schemas are visible to the client | Agents stay opaque |

The line between the two is not always strict. An A2A agent can also offer some of its skills over MCP, so that other applications can use them like a tool. But the real strength of A2A is the flexible collaboration over several turns that goes beyond a single tool call.

A simple rule helps when you are not sure which one to use. If the other side should just do something and return, it is a tool, and MCP is the right choice. If the other side should think, plan, ask back, or work for a longer time on its own, it is an agent, and A2A is the right choice.

Keep in mind the advice from the Agentic Patterns section as well. A system of several communicating agents is harder to follow and to debug than one agent with good tools. Use A2A when the other agent really is a separate system, owned by another team or built with another technology, and not just to split your own application into many small agents.
