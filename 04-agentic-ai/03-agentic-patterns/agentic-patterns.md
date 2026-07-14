## The Workflow Patterns

The fundamentals section explained the difference between workflows, where your code decides the steps, and agents, where the model decides. On the workflow side, a small set of recurring shapes covers most multi-step tasks. You do not need a library for them. Each one is a small amount of code around the `ChatClient`.

**Chain** breaks a complex task into sequential steps and feeds the output of each model call into the next one. It is slower but more accurate. Use it when a task is too big for a single prompt but splits cleanly into steps, for example extract, then classify, then summarize.

**Parallelization** runs several independent model calls at the same time and combines the results. It is ideal when you need to analyze many items, or one item from several perspectives. For example, you can evaluate the impact of a change on customers, employees, and investors at the same time and then combine the answers.

**Routing** uses a first model call to classify the input and then sends it to a specialized prompt or pipeline. A support assistant might send a "charged twice" message to a billing specialist prompt and a "build won't compile" message to a technical one. Each message is handled better than a single generic prompt could handle it.

**Orchestrator-Workers** handles tasks whose subtasks cannot be predicted upfront. An orchestrator call splits the problem into subtasks at runtime, workers handle them, often in parallel, and a final call combines the results. This is more dynamic than a fixed chain because the model decides how to split the work.

**Evaluator-Optimizer** introduces a feedback loop. One call generates a response, and another call evaluates it against your criteria. If the response is not good enough, the feedback is used for an improved retry. This repeats until the response passes or a limit is reached. It is the evaluation idea from the testing section turned into an improvement loop at runtime. Spring AI's structured output works well here, because the evaluator can return a typed verdict like `record EvaluationResponse(int rating, String feedback)` that your loop can branch on.

All of these are composition patterns, not products. Pick the simplest one that fits, and move to the next one only when the task demands it. Beyond them sits the fully autonomous agent, which is the tool-calling loop directing itself. That is where the next pattern becomes essential.

## Tool Search: Giving an Agent Hundreds of Tools

An autonomous agent is only as capable as the tools it can reach, so the natural move is to give it more: your tools, plus several MCP servers' worth of others. But there's a wall you hit fast. The conventional approach sends *every* tool definition to the model on *every* request, and that creates two problems:

1. **Token bloat** A multi-server setup with 50+ tools burns a large amount of context before the user has said anything. The measured cost is real, for a 28-tool setup, tool definitions alone consume roughly 5,400 tokens on Gemini, 7,200 on OpenAI, and 17,300 on Anthropic, every single request.
2. **Accuracy degradation** When a model faces 30+ similarly-named tools, it picks the wrong one more often. More tools can make an agent *worse*.

Spring AI's answer, now part of the official release, is the **Tool Search Tool**: instead of loading all tools upfront, the agent *discovers them on demand*. The idea is simple and clever, you give the model one tool, a search tool, and let it look up the others when it needs them.

### How it works

All your registered tools are indexed into a **`ToolIndex`**, but **not** sent to the model. On the first request the model sees only the **Tool Search Tool**. When it needs a capability, it calls that search tool with a natural-language query ("find a tool to get the weather"). The index returns the matching tools, and *their* full definitions are expanded into the next request, so the model now sees the search tool plus the handful of relevant tools, calls them, and produces its answer. Hundreds of tools become reachable while only a few definitions ever enter the context at a time.

This is delivered as an advisor, the **`ToolSearchToolCallingAdvisor`**, which extends the familiar `ToolCallingAdvisor` with the discovery step. You add the dedicated module:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-tool-search-advisor</artifactId>
</dependency>
```

Then register the advisor and your tools as usual. Note the tools are configured on the client, but thanks to the advisor they aren't all shipped to the model:

```java
var advisor = ToolSearchToolCallingAdvisor.builder()
    .toolIndex(toolIndex)
    .build();

ChatClient chatClient = builder
    .defaultTools(new MyTools())  // hundreds of tools registered, NOT all sent to the model
    .defaultAdvisors(advisor)     // activates the Tool Search Tool
    .build();

String answer = chatClient.prompt("""
        Help me plan what to wear today in Amsterdam.
        Suggest clothing shops that are open right now.
        """)
    .call()
    .content();
```

### Choosing how tools are searched

The `ToolIndex` is pluggable, because "find the right tool" can mean different things:

| Strategy | Implementation | Best for |
|----------|----------------|----------|
| **Semantic** | `VectorToolIndex` | Natural-language queries, fuzzy matching by meaning |
| **Keyword** | `LuceneToolIndex` | Exact term matching, known tool names |
| **Regex** | `RegexToolIndex` | Tool-name patterns like `get_*_data` |

The semantic `VectorToolIndex` brings the embeddings-and-similarity ideas from the RAG section full circle: the tools themselves are embedded and searched by meaning, exactly as documents were.

### When to use it

Tool Search earns its keep once you have **20+ tools**, tool definitions exceeding ~5K tokens, or several MCP servers feeding one agent, the precise situation an ambitious assistant lands in. Reported savings range from **34% to 64%** of tool-definition tokens across providers, with better selection accuracy as a bonus. For a small, fixed tool set (under ~20, all used every session), the traditional upfront approach is simpler and fine. Like everything in this section: add the capability when the scale demands it, not before.

## Beyond the Core: Community Agent Patterns

The patterns above use only the core framework. A set of further agentic capabilities is being built by the Spring team but currently lives in **`spring-ai-community`**, more experimental, but maintained by the same people and worth knowing exist:

- **LLM-as-a-Judge / Self-Refine** — the evaluator-optimizer pattern packaged as a reusable advisor. Built on Spring AI's experimental *recursive advisors*, a `SelfRefineEvaluationAdvisor` generates a response, has a (separate, bias-avoiding) judge model rate it on a structured scale, and retries with the feedback until it passes. It turns "evaluate then improve" into a single drop-in advisor.
- **Skills** — a `SkillsTool` that lets an agent load reusable *knowledge modules* written as Markdown files with YAML front-matter. Skills are discovered by name and description at startup and their full instructions loaded only when semantically relevant, the same load-on-demand philosophy as Tool Search, applied to instructions rather than tools.
- **Ask-User-Question** — an `AskUserQuestionTool` that puts a human in the loop. Instead of guessing at ambiguous instructions, the agent can pause to ask the user a structured question (with options or free text) and continue once answered, essential for high-stakes actions where you want confirmation, not assumption.
- **Agent-to-Agent (A2A)** — `spring-ai-a2a` provides server-side support for exposing a Spring AI agent over the open **A2A protocol**, so agents in *different* systems can discover and delegate to one another. Where MCP connects an agent to *tools*, A2A connects an agent to *other agents*, the next layer of composition once a single agent isn't enough.

These are moving targets, so treat them as a map of where Spring AI's agentic story is heading rather than stable APIs to build on today.
