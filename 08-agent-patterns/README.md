## From Single Calls to Systems That Decide

Every technique so far has been, at heart, a single step: send a prompt, get an answer. RAG enriched that step with context, tools let it reach outside, MCP widened its reach further, but it was still *one* model call wrapped in your code. Real problems are rarely one step. "Triage this support ticket, research the customer's history, draft a reply, and check it's accurate before sending" is a *sequence of decisions*, some of which the system should make for itself.

Systems that use a model to do multi-step work are called **agentic**. The word covers a wide spectrum, and the most important skill in this space isn't wiring up the fanciest agent, it's knowing *how much* agency a task actually needs. Spring AI's approach, drawn directly from Anthropic's "Building Effective Agents," is built around exactly that judgment: **start simple, add autonomy only when it earns its place.**

## Workflows vs. Agents

That guidance rests on a distinction worth making sharp:

- **Workflows** are systems where "LLMs and tools are orchestrated through predefined code paths." *You* decide the steps; the model fills in the intelligence at each one. The control flow is in your Java code, so it's predictable, testable, and repeatable.
- **Agents** are systems where "LLMs dynamically direct their own processes and tool usage." The *model* decides what to do next, which tool to call, whether it's finished. More flexible, but less predictable.

The instinct is to reach for a fully autonomous agent, but the documentation is blunt about why you usually shouldn't: "while fully autonomous agents might seem appealing, workflows often provide better predictability and consistency for well-defined tasks. This aligns perfectly with enterprise requirements where reliability and maintainability are crucial." For most enterprise problems, a workflow you can reason about beats an agent you can only hope about.

Crucially, Spring AI gives you no heavy "agent framework" for these. The patterns below are built entirely from primitives you already have, `ChatClient`, structured output, tools, advisors, so an agentic system is just ordinary, debuggable Spring code.

## The Workflow Patterns

There are a handful of recurring shapes for composing model calls into a workflow. You don't need a library for them; each is a small amount of code around the `ChatClient`.

**Chain** breaks a complex task into sequential steps, feeding each model call's output into the next. It trades latency for accuracy, useful when a task is too much to ask in one shot but decomposes cleanly (extract → classify → summarize).

**Parallelization** runs several independent model calls at once and aggregates the results, ideal for analyzing many items, or one item from several perspectives (gauge the impact of a change on customers, employees, and investors simultaneously), then combining the answers.

**Routing** uses a first model call to *classify* the input, then dispatches it to a specialized prompt or pipeline. A support assistant might route a "charged twice" message to a billing specialist prompt and a "build won't compile" message to a technical one, each handled better than a single generic prompt could.

**Orchestrator-Workers** handles tasks whose subtasks *can't be predicted upfront*. An orchestrator call decomposes the problem into subtasks at runtime, workers handle them (often in parallel), and a final call synthesizes the results. This is more dynamic than a fixed chain because the model decides the breakdown.

**Evaluator-Optimizer** introduces a feedback loop: one call generates a response, another *evaluates* it against criteria, and if it falls short, the feedback drives a refined retry, repeating until it passes or a limit is hit. This is the evaluation idea from the testing section turned into a runtime improvement loop. (Spring AI's structured output shines here, the evaluator returns a typed verdict like `record EvaluationResponse(int rating, String feedback)` that your loop can branch on.)

The throughline: these are *composition patterns*, not products. Pick the simplest one that fits, and reach for the next only when the task demands it.

## The Autonomous Agent You've Already Built

Here's something you may not have noticed: you've already run an autonomous agent. The tool-calling loop from section four, where the model is given tools and the `ToolCallingAdvisor` lets it call them, see the results, and decide what to do next, *is* the minimal agent. The model dynamically directs its own tool usage until it judges the task complete. Give it RAG, a handful of tools, and a few MCP servers, and that loop becomes a capable assistant that plans its own path through a request.

This is the bridge from workflows to agents, and it's where the next capability becomes essential.

## Tool Search: Giving an Agent Hundreds of Tools

An autonomous agent is only as capable as the tools it can reach, so the natural move is to give it more: your tools, plus several MCP servers' worth of others. But there's a wall you hit fast. The conventional approach sends *every* tool definition to the model on *every* request, and that creates two problems:

1. **Token bloat.** A multi-server setup with 50+ tools burns a large amount of context before the user has said anything. The measured cost is real, for a 28-tool setup, tool definitions alone consume roughly 5,400 tokens on Gemini, 7,200 on OpenAI, and 17,300 on Anthropic, every single request.
2. **Accuracy degradation.** When a model faces 30+ similarly-named tools, it picks the wrong one more often. More tools can make an agent *worse*.

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

## What's Next

Agentic AI is a spectrum, and the craft is choosing the right point on it. **Workflows**, chain, parallelization, routing, orchestrator-workers, evaluator-optimizer, give you predictable, composable multi-step systems built from the `ChatClient`, structured output, and advisors you already know; reach for a fully **autonomous agent** (really just the tool-calling loop) only when the task genuinely needs to direct itself. When an agent grows to many tools across many MCP servers, the **Tool Search Tool** keeps it fast and accurate by discovering tools on demand instead of loading them all. And an expanding set of **community patterns**, self-refinement, skills, human-in-the-loop, and agent-to-agent collaboration, shows where this is all heading. In the final exercise you'll bring the whole workshop together: an assistant that retrieves, acts, reaches external MCP servers, and discovers its tools as it needs them.
