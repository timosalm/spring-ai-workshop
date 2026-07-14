# Building AI Applications with Spring AI

A hands-on workshop for Java developers. Over a series of modules you incrementally build a **support assistant for VMware Tanzu Spring**, an application that answers questions from documentation, creates support tickets, maintains conversation history, and eventually reaches external services and discovers its own tools. Each module pairs focused theory chapters with practical labs, so you understand *why* before you build.

Built on **Spring AI 2.0**, **Spring Boot 4.1**, and **Java 21**.

## Prerequisites

**Tooling**
- **JDK 21** (the sample apps target `java.version=21`).
- A code editor or IDE (IntelliJ IDEA, VS Code, …).
- **Maven** — each sample app ships the Maven Wrapper (`./mvnw`), so no separate install is required.
- **Docker** (optional) — used from the production-ready module onward for PostgreSQL/pgvector and the observability stack via each app's `compose.yaml`.
- `curl` (or any HTTP client) to exercise the endpoints.

**A model provider** — the labs use **OpenAI**. Set your key in the terminal you run an app in:

```bash
export OPENAI_API_KEY=sk-...
```

To use another provider, swap the Spring AI starter dependency and the `spring.ai.<provider>.*` properties.

## How the Workshop Is Structured

The repository mirrors the course's learning modules. Work through them in order — each lab builds directly on the application state left by the previous one.

```
spring-ai-workshop/
├── README.md                        ← you are here
├── APPENDIX-spring-ai-1.1-vs-2.0.md ← reference: upgrading 1.1 → 2.0
│
├── 00-intro/
│   └── intro.md                     ← what Spring AI is and what you'll build
│
├── 01-fundamentals/                 ← Module 1
│   ├── ai-fundamentals.md           │  theory: how LLMs work
│   ├── spring-ai-fundamentals.md    │  theory: ChatModel, ChatClient, structured output
│   ├── advisors.md                  │  theory: the advisor pattern
│   ├── exercises.md                 │  hands-on lab
│   └── sample-app/                  ┘  starting code for the lab
│
├── 02-advanced-patterns/            ← Module 2
│   ├── foundations.md               ← theory: RAG & tool-calling foundations
│   ├── rag/                         ┐  RAG lab
│   │   ├── rag.md                   │  theory
│   │   ├── exercises.md             │  hands-on
│   │   └── sample-app/              ┘  starting code
│   └── tool-calling/                ┐  tool-calling lab
│       ├── tool-calling.md          │
│       ├── exercises.md             │
│       └── sample-app/              ┘
│
├── 03-production-ready-features/    ← Module 3
│   ├── testing/                     ┐  testing lab (theory + exercises + app)
│   │   └── …                        ┘
│   └── observability/               ┐  observability lab
│       └── …                        ┘
│
├── 04-agentic-ai/                   ← Module 4
│   ├── agentic-ai-fundamentals.md   ← theory: agents vs. workflows
│   ├── mcp/                         ┐  MCP lab
│   │   └── …                        ┘
│   └── agentic-patterns/            ┐  agentic-patterns lab (+ spring-releases-mcp-server)
│       └── …                        ┘
│
└── 99-summary/                      ← recap + consolidated final sample-app
    ├── summary.md
    ├── sample-app/
    └── spring-releases-mcp-server/
```

Each lab folder follows the same layout:

- **`*.md` theory chapters** — read these first. They explain the concepts and the relevant Spring AI APIs in prose, with code snippets. Multi-topic modules split the theory into topic-named files (`ai-fundamentals.md`, `advisors.md`, `rag.md`, …).
- **`exercises.md`** — the hands-on lab. A series of small, incremental changes (edit, restart, `curl`, observe) that apply the theory to the `sample-app`.
- **`sample-app/`** — a self-contained, runnable Spring Boot application. It carries the support assistant forward, so each lab's app already contains everything you built in the earlier labs. In other words, a lab's `sample-app` is roughly the *solution* of the previous lab.

The `00-intro` module is theory only. `99-summary` ties it all together with the finished application.

## Content Overview

| Module | Topic | What you add to the assistant |
|--------|-------|-------------------------------|
| **00** | Introduction | What Spring AI is and what you'll build |
| **01** | Fundamentals | `ChatModel` / `ChatClient`, prompts & templates, streaming, structured (type-safe) output, and the advisor pattern (logging, memory) |
| **02** | Advanced patterns | RAG (embeddings, a `VectorStore`, the ETL pipeline, the `QuestionAnswerAdvisor`) and tool calling (`@Tool` methods run by the `ToolCallingAdvisor`) |
| **03** | Production-ready features | Testing (mocking the model, evaluators) and observability (metrics, tracing, token-usage/cost via Micrometer/Actuator) |
| **04** | Agentic AI | The Model Context Protocol (consume and expose tools) and agentic patterns (the Tool Search Tool for many-tool agents) |
| **99** | Summary | A recap and the consolidated final application |

> **Reference:** [`APPENDIX-spring-ai-1.1-vs-2.0.md`](APPENDIX-spring-ai-1.1-vs-2.0.md) summarizes the main features and breaking changes when moving from Spring AI 1.1 to 2.0.

## Getting Started

1. Ensure the prerequisites above are in place (JDK 21 and a key for your chosen provider).
2. Read [`00-intro/intro.md`](00-intro/intro.md), then the theory chapters in [`01-fundamentals`](01-fundamentals/).
3. Start the first lab by following [`01-fundamentals/exercises.md`](01-fundamentals/exercises.md).
4. Run a lab's app from its `sample-app/` directory:
   ```bash
   cd 01-fundamentals/sample-app
   export OPENAI_API_KEY=sk-...
   ./mvnw spring-boot:run
   ```
5. Work through the modules in order, carrying the assistant forward to `99-summary`.
