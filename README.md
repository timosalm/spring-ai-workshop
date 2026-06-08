# Building AI Applications with Spring AI

A hands-on workshop for Java developers. Over a series of short modules you incrementally build a **support assistant for VMware Tanzu Spring**, an application that answers questions from documentation, creates support tickets, maintains conversation history, and eventually reaches external services and discovers its own tools. Each module pairs a focused theory chapter with a practical lab, so you understand *why* before you build.

Built on **Spring AI 2.0**, **Spring Boot 4**, and **Java 25**.

## Prerequisites

**Tooling**
- **JDK 25** (the sample apps target `java.version=25`).
- A code editor or IDE (IntelliJ IDEA, VS Code, …).
- **Maven** — each sample app ships the Maven Wrapper (`./mvnw`), so no separate install is required.
- **Docker** (optional) — used from the testing module onward for PostgreSQL/pgvector and the observability stack via each app's `compose.yaml`.
- `curl` (or any HTTP client) to exercise the endpoints.

**A model provider** — pick one and set the matching environment variable. The default profile uses OpenAI; alternatives are provided as Spring profiles:

## How the Workshop Is Structured

The repository is a sequence of numbered modules. Work through them in order, each one builds directly on the application state left by the previous module.

```
spring-ai-workshop/
├── README.md                        ← you are here
├── APPENDIX-spring-ai-1.1-vs-2.0.md ← reference: upgrading 1.1 → 2.0
│
├── 00-intro/                        ← theory only (no lab)
├── 01-ai-fundamentals/              ← theory only (no lab)
│
├── 02-simple-chat/                  ┐
│   ├── README.md                    │  theory chapter
│   ├── exercises.md                 │  step-by-step lab
│   └── sample-app/                  │  runnable Spring Boot app (the lab's starting code)
│       ├── mvnw, pom.xml            │
│       └── src/                     ┘
├── 03-rag/
├── 04-tool-calling/
├── 05-testing/
├── 06-observability/
├── 07-mcp/
├── 08-agent-patterns/
└── 99-summary/                      ← recap + final consolidated sample-app
```

Each lab module follows the same layout:

- **`README.md`** — the theory chapter. Read this first. It explains the concepts and the relevant Spring AI APIs in prose, with code snippets.
- **`exercises.md`** — the hands-on lab. A series of small, incremental changes (edit, restart, `curl`, observe) that apply the chapter to the `sample-app`.
- **`sample-app/`** — a self-contained, runnable Spring Boot application. It carries the support assistant forward from one module to the next, so module N's app already contains everything you built in modules 2…N-1.

The `00-intro` and `01-ai-fundamentals` modules are theory only. Modules `02`–`08` each have a lab. `99-summary` ties it all together.

## Content Overview

| Module | Topic | What you add to the assistant |
|--------|-------|-------------------------------|
| **00** | Introduction | What Spring AI is and what you'll build |
| **01** | GenAI fundamentals | How LLMs work — prompts, tokens, context windows, and their limitations |
| **02** | Simple chat | `ChatModel` / `ChatClient`, prompts & templates, streaming, and structured (type-safe) output |
| **03** | RAG | Embeddings, a `VectorStore`, the ETL ingestion pipeline, and the advisor pattern to ground answers in your docs |
| **04** | Tool calling | `@Tool` methods so the model can act (e.g. open a support ticket), run by the auto-registered `ToolCallingAdvisor` |
| **05** | Testing | Mocking the model for deterministic tests, plus evaluators (relevance & fact-checking) and Testcontainers |
| **06** | Observability | Metrics and distributed tracing via Micrometer/Actuator, including token-usage and cost visibility |
| **07** | MCP | The Model Context Protocol — consume external tools as a client, expose your own as a server |
| **08** | Agent patterns | Workflows vs. agents, the Tool Search Tool for many-tool agents, and emerging community patterns |
| **99** | Summary | A recap of the whole journey and the consolidated final application |

> **Reference:** [`APPENDIX-spring-ai-1.1-vs-2.0.md`](APPENDIX-spring-ai-1.1-vs-2.0.md) summarizes the main features and breaking changes when moving from Spring AI 1.1 to 2.0, useful if you've seen 1.1 code elsewhere.

## Getting Started

1. Ensure the prerequisites above are in place (JDK 25, Docker, and an API key for your chosen provider).
2. Read [`00-intro`](00-intro/README.md) and [`01-ai-fundamentals`](01-ai-fundamentals/README.md).
3. Start the first lab: read [`02-simple-chat/README.md`](02-simple-chat/README.md), then follow [`02-simple-chat/exercises.md`](02-simple-chat/exercises.md).
4. Run a module's app from its `sample-app/` directory:
   ```bash
   cd 02-simple-chat/sample-app
   export OPENAI_API_KEY=sk-...        # or use another provider profile
   ./mvnw spring-boot:run
   ```
5. Work through the modules in order, carrying the assistant forward to module `08` and the `99-summary`.
