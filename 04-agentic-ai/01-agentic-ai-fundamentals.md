Every technique so far has been a single step. You send a prompt and get an answer. RAG enriched that step with context, and tools let it reach outside, but it was still one model call wrapped in your code. Real problems are rarely one step. "Triage this support ticket, research the customer's history, draft a reply, and check it's accurate before sending" is a sequence of decisions, and the system should make some of them on its own.

Systems that use a model to do multi-step work are called **agentic**. The word covers a wide range of designs, and the most important skill in this space is not building the most advanced agent. It is knowing how much agency a task actually needs. Spring AI follows the advice from Anthropic's "Building Effective Agents" guide. Start simple, and add autonomy only when it provides clear value.

## Workflows vs. Agents

This advice is based on an important distinction.

- **Workflows** are systems where LLMs and tools are orchestrated through predefined code paths. You decide the steps, and the model provides the intelligence at each one. The control flow lives in your Java code, so it is predictable, testable, and repeatable.
- **Pure Agents** are systems where LLMs dynamically direct their own processes and tool usage. The model decides what to do next, which tool to call, and whether it is finished. This is more flexible, but less predictable.

Many developers want to start with a fully autonomous agent. But, while fully autonomous agents might seem appealing, workflows often provide better predictability and consistency for well-defined tasks. This aligns perfectly with enterprise requirements where reliability and maintainability are crucial.

Spring AI does not give you a heavy "agent framework" for any of this. Everything agentic is built from the building blocks you already know, such as the `ChatClient`, structured output, tools, and advisors. An agentic system is therefore just ordinary Spring code that you can debug.

## The Autonomous Agent You've Already Built

You may not have noticed it, but you have already run an autonomous agent. The tool-calling loop from the tool calling section is the minimal agent. The model is given tools, calls them, sees the results, and decides what to do next, until it judges the task complete. Give that loop RAG and a handful of tools, and it becomes a capable assistant that plans its own path through a request.

Two things turn this minimal agent into one that is ready for production. It needs to reach capabilities outside your application, and it needs to combine model calls in the right way for the task. The next sections cover both.

## Reaching External Systems With the Model Context Protocol

So far, every tool the assistant can call lives inside its own codebase. The **Model Context Protocol (MCP)** is an open standard that changes this. It allows an application to expose tools and other capabilities as a server that any MCP-compatible client can discover and call. It also allows your agent to use tools from servers built by others, in any programming language. MCP is quickly becoming the standard way for agents to connect to the outside world. The next section covers MCP in detail, and in its lab you will build an MCP server and connect the support assistant to it.

## Common Agentic Patterns

For the multi-step work itself, a small set of common patterns has emerged. On the workflow side these are **chain**, **parallelization**, **routing**, **orchestrator-workers**, and **evaluator-optimizer**. For agents that grow to many tools, there are techniques such as on-demand **tool search**. Each pattern is a small amount of code around the `ChatClient`, not a product. The skill is to pick the least autonomous pattern that solves the task. The agentic patterns section explains each of them and when to use it.
