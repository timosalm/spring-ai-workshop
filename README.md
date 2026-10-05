# Building AI Applications with Spring AI

A hands-on workshop for Java developers. Over a series of modules you build a **support assistant for VMware Tanzu Spring** step by step. It answers questions from documentation, creates support tickets, remembers the conversation, reaches external services over MCP, finds its own tools, and finally uses experimental agentic patterns such as skills and a human in the loop. Each module pairs theory chapters with practical labs, so you understand *why* before you build.

The content is based on a free, [interactive workshop at Spring Academy](https://spring-staging.academy/courses/spring-ai-intro/).

Built on **Spring AI 2.0**, **Spring Boot 4.1**, and **Java 21**.

## Prerequisites

**Tooling**

- **JDK 21** or newer. The sample apps target `java.version=21`.
- A code editor or IDE, such as IntelliJ IDEA or VS Code.
- **Maven** is not needed separately, because each sample app ships the Maven Wrapper (`./mvnw`).
- **Docker** for the observability lab (Grafana stack) and the MCP security part (Keycloak). Both run through the `compose.yaml` of the app.
- `curl` to call the endpoints, and [`jq`](https://jqlang.org) for readable JSON output in some labs.

**A model provider.** The labs use **OpenAI** with the `gpt-5.6-sol` chat model and the `text-embedding-3-small` embedding model. Set your key in the terminal where you run an app.

```bash
export SPRING_AI_OPENAI_BASE_URL=https://devoxx-be.openai.azure.com
export OPENAI_API_KEY=$(curl -s https://gist.githubusercontent.com/timosalm/acc69bb791b2a79cf9084db75d51bb5e/raw/4124770e881ba74cb1587ea0e1f1af483e4e0883/key.txt | base64 -d)
```

To use another provider, swap the Spring AI starter dependency and the `spring.ai.<provider>.*` properties.

## How the Workshop Is Structured

The repository follows the learning modules of the course. Work through them in order, because each lab builds on the application state that the previous one left behind.

Each lab folder follows the same layout.

- **Theory chapters (`*.md`)** explain the concepts and the relevant Spring AI APIs in prose, with code snippets and diagrams. Read them first.
- **`exercises.md`** is the lab. It is written for self paced learning. Each step says which file to create or change and gives you the code to copy. Java changes always show the complete file, so you can replace the whole content without looking for the right place or a missing import. Between the steps you call the application with `curl` and look at the result.
- **`sample-app/`** is a runnable Spring Boot application and the starting point of the lab. It carries the support assistant forward, so it already contains everything you built in the earlier labs. In other words, the `sample-app/` of a lab is the solution of the previous lab. If you get stuck, compare your code with it or continue from there.

The `00-intro` module is theory only. `99-summary` ties it all together with the finished applications.

## Content Overview

| Module | Topic | What you add to the assistant |
|--------|-------|-------------------------------|
| **00** | Introduction | What Spring AI is and what you build |
| **01** | Fundamentals | `ChatModel` and `ChatClient`, prompts and templates, streaming, structured output, and advisors for logging and conversation memory |
| **02** | Advanced patterns | RAG with embeddings, a `VectorStore`, the ETL pipeline, and the `QuestionAnswerAdvisor`, then tool calling with `@Tool` methods |
| **03** | Production ready features | Testing with semantic assertions and an LLM as judge, and observability with metrics, token usage, and traces in Grafana |
| **04** | Agentic AI | MCP servers and clients secured with OAuth 2.0, the Tool Search Tool for many tools, experimental patterns (evaluator optimizer, Agent Skills, plan and execute, human in the loop), and the Agent2Agent protocol |
| **99** | Summary | A recap and the finished applications |

## Getting Started

1. Make sure the prerequisites above are in place, especially JDK 21 and your OpenAI key.
2. Read [`00-intro/intro.md`](00-intro/intro.md), then the theory chapters in [`01-fundamentals`](01-fundamentals/).
3. Start the first lab with [`01-fundamentals/exercises.md`](01-fundamentals/exercises.md).
4. Run the app of a lab from its `sample-app/` folder.
   ```bash
   cd 01-fundamentals/sample-app
   export OPENAI_API_KEY=sk-...
   ./mvnw spring-boot:run
   ```
5. Work through the modules in order until you reach [`99-summary`](99-summary/summary.md).
