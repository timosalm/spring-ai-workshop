## Summary: What You've Built

You started this workshop with a raw language model, a brilliant but isolated text predictor, frozen at training time, with no memory, no access to your data, and no ability to act. You're ending it with a production-shaped support assistant for VMware Tanzu Spring that answers from real documentation, takes actions, remembers conversations, is tested and observable, and can reach beyond its own code. The journey between those two points *is* the curriculum, and it's worth seeing it whole.

## The Through-Line

Every section addressed one specific limitation of a bare LLM, and each built on the one before.

**Fundamentals.** You saw what an LLM actually is, a next-token predictor, and the vocabulary that follows from that: prompts and completions, tokens (the unit of both capacity and cost), the context window, and parameters. You also named the limitations that the rest of the workshop exists to overcome: hallucination, frozen knowledge, statelessness, inability to act, and probabilistic output.

**Simple chat.** The foundation: models are just REST APIs, and Spring AI gives you a consistent, portable programming model over them, the same way Spring Data does over data stores. You met the low-level `ChatModel` and the fluent `ChatClient`, learned to compose prompts from system/user/assistant messages and `PromptTemplate`s, stream responses, read token usage, and, crucially, map model output straight onto Java types with `.entity(...)` for structured, type-safe results.

**RAG.** To fix frozen knowledge and hallucination, you grounded the model in your own documents. This introduced **embeddings** (meaning as vectors), the **`VectorStore`** (search by similarity, filter by metadata), and the **ETL pipeline** (read, chunk, load). You also met the **advisor** pattern, the interceptor that wraps a `ChatClient` call, and saw the `QuestionAnswerAdvisor` perform retrieval as just one more advisor in the chain.

**Tool calling.** To let the model *act*, you gave it tools: ordinary `@Tool`-annotated methods whose descriptions are documentation written for the model. The auto-registered `ToolCallingAdvisor` runs the request-execute-respond loop, and `ToolContext` keeps sensitive data out of the model's reach. The model proposes; your application disposes.

**Testing.** Because output is probabilistic, you can't assert exact strings. You learned to split deterministic code (test conventionally with a mocked `ChatModel`) from probabilistic output (assert *properties*), and to use **evaluators**, `RelevancyEvaluator` and `FactCheckingEvaluator`, that turn a model on itself (LLM-as-judge) to check answers are on-topic and free of hallucination.

**Observability.** Built on **Micrometer and Spring Boot Actuator**, Spring AI emits **metrics and distributed traces** for every layer, ChatClient, advisors, ChatModel, tools, vector store, so a trace mirrors the request. **Token-usage metrics** make cost visible, while prompt/completion **content logging stays opt-in** for privacy.

**MCP.** To let the assistant reach *anyone's* tools, not just its own, the **Model Context Protocol** standardizes how AI apps connect to external capability providers. As a **client**, Spring AI discovers a server's tools and exposes them as ordinary `ToolCallback`s; as a **server**, a few annotations (`@McpTool`, `@McpResource`, `@McpPrompt`) turn your app into a provider for others, with the emerging **MCP Security** module to lock it down.

**Agent patterns.** Finally, you composed all of this into multi-step systems, choosing carefully between predictable **workflows** (chain, parallelization, routing, orchestrator-workers, evaluator-optimizer) and autonomous **agents** (really just the tool-calling loop directing itself). When an agent grows to many tools across many MCP servers, the **Tool Search Tool** discovers tools on demand to keep it lean and accurate, and a set of **community patterns** (self-refinement, skills, human-in-the-loop, agent-to-agent) shows where the field is heading.

## The Ideas That Recur

Step back and a few principles run through every section:

- **One portable programming model.** From chat to embeddings to tools to MCP, it's the same `ChatModel`/`ChatClient` and the same Spring idioms, auto-configured beans, externalized configuration, switch providers without rewriting code.
- **Advisors compose everything.** RAG, memory, tool calling, and tool search are all advisors in one chain. Add a capability by adding an advisor; your prompting code stays the same fluent chain.
- **Structured output is a workhorse.** Mapping responses to Java types underpins not just clean application code but evaluation verdicts and agent decisions.
- **It's just Spring.** No parallel universe, AI operations are normal beans, tested with JUnit, observed through Actuator, secured with Spring Security.
- **Start simple; add capability when the task earns it.** The recurring discipline, from choosing a workflow over an agent to adding Tool Search only past ~20 tools.

## Where to Go Next

You now have the complete mental model and a working assistant that demonstrates it. From here you can deepen any layer, swap in a production vector store, wire real MCP servers, build out an evaluation suite, ship traces to your observability backend, or explore the fast-moving community agent patterns. Spring AI is under active development and the applied-AI landscape keeps shifting, but the foundation you've built, models as portable REST APIs, grounded in your data, able to act, tested, observable, and composable, is exactly what stays steady underneath it all.

You set out to build your first AI-enabled application. You've built one, and you understand every piece of it.
