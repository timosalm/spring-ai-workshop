---
title: Advisors & Agentic Patterns
---

In this section, you'll learn about Spring AI's **Advisor** concept and how it enables powerful AI Agent patterns. You've already used an advisor in the RAG section -- now let's explore the full picture.

## Learning Objectives

- Understand the Advisor API and how it intercepts the chat pipeline
- Explore built-in advisors beyond RAG
- Learn the key AI Agent patterns and when to apply them
- Discover advanced agentic capabilities in the Spring AI ecosystem

## What are Advisors?

**Advisors** intercept and modify requests and responses in the Spring AI chat pipeline. They work like a chain of filters (similar to servlet filters or Spring AOP advice), each one processing the request before it reaches the model and the response after it comes back.

```
User Request -> [Advisor 1] -> [Advisor 2] -> [Advisor 3] -> Chat Model -> LLM
                                                                  |
Response    <- [Advisor 3] <- [Advisor 2] <- [Advisor 1] <-  Chat Model
```

### The Advisor API

Spring AI provides two main advisor interfaces:

```java
// For synchronous (blocking) calls
public interface CallAdvisor extends Advisor {
    ChatClientResponse adviseCall(
        ChatClientRequest chatClientRequest,
        CallAdvisorChain callAdvisorChain);
}

// For streaming calls
public interface StreamAdvisor extends Advisor {
    Flux<ChatClientResponse> adviseStream(
        ChatClientRequest chatClientRequest,
        StreamAdvisorChain streamAdvisorChain);
}
```

Each advisor can:
- **Modify the request** before passing it to the next advisor in the chain
- **Modify the response** before returning it to the previous advisor
- **Block the request** entirely (e.g., for content safety)
- **Share state** with other advisors via the `advise-context`

### Advisor Ordering

Advisors implement Spring's `Ordered` interface. Lower order values execute first on the request side and last on the response side. This is important when combining multiple advisors.

## The Advisor You Already Know

In the RAG section, you used the `QuestionAnswerAdvisor` to implement Retrieval Augmented Generation:

```java
ChatClient.builder(chatModel)
    .defaultAdvisors(
        QuestionAnswerAdvisor.builder(vectorStore).build()
    )
    .build();
```

This advisor automatically:
1. Searches the vector store for documents relevant to the user's question
2. Injects the retrieved context into the prompt
3. Passes the augmented prompt to the model

Spring AI also provides the more flexible `RetrievalAugmentationAdvisor` for advanced RAG patterns with customizable query transformation, document selection, and context augmentation.

## Built-in Advisors

Spring AI ships with several ready-to-use advisors:

### Chat Memory Advisors

Maintaining conversation history is essential for multi-turn interactions. Spring AI offers multiple approaches:

| Advisor | How it works |
|---------|-------------|
| `MessageChatMemoryAdvisor` | Adds conversation history as a collection of messages to the prompt |
| `PromptChatMemoryAdvisor` | Incorporates memory into the prompt's system text |
| `VectorStoreChatMemoryAdvisor` | Retrieves relevant memories from a vector store |

Example with `MessageChatMemoryAdvisor`:

```java
ChatMemory chatMemory = new InMemoryChatMemory();

ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(
        MessageChatMemoryAdvisor.builder(chatMemory).build()
    )
    .build();

// Per-conversation memory using conversationId
chatClient.prompt()
    .user("Remember my name is Alice")
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "user-123"))
    .call()
    .content();
```

### SimpleLoggerAdvisor

Logs request and response data for debugging and monitoring:

```java
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(new SimpleLoggerAdvisor())
    .build();
```

Enable DEBUG logging to see the output:
```properties
logging.level.org.springframework.ai.chat.client.advisor=DEBUG
```

### SafeGuardAdvisor

Prevents the model from generating harmful or inappropriate content by filtering requests.

### ReReadingAdvisor

Implements the RE2 (Re-Reading Improves Reasoning) strategy, which enhances LLM reasoning by repeating the question in the prompt.

### Combining Multiple Advisors

The recommended order when combining advisors:

```java
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(
        MessageChatMemoryAdvisor.builder(chatMemory).build(),  // 1. Add conversation history
        QuestionAnswerAdvisor.builder(vectorStore).build(),     // 2. RAG search with context
        new SimpleLoggerAdvisor()                               // 3. Logging (place last)
    )
    .build();
```

## AI Agent Patterns

The Advisor concept and Spring AI's tool calling capabilities enable powerful **agentic patterns**. Drawing from Anthropic's research on building effective agents, Spring AI distinguishes between:

- **Workflows**: Predefined code paths where LLMs are orchestrated through fixed steps
- **Agents**: LLMs dynamically directing their own processes and tool usage

The key principle: **start simple, add complexity only when needed**. Most applications don't need full autonomous agents -- simpler patterns are often more reliable.

### Pattern 1: Chain Workflow

Break a complex task into sequential steps, where the output of one LLM call feeds into the next.

```java
// Step 1: Generate outline
String outline = chatClient.prompt()
    .user("Create an outline for an article about: " + topic)
    .call()
    .content();

// Step 2: Write article based on outline
String article = chatClient.prompt()
    .user("Write an article based on this outline: " + outline)
    .call()
    .content();

// Step 3: Review and polish
String finalArticle = chatClient.prompt()
    .user("Review and improve this article: " + article)
    .call()
    .content();
```

**When to use:** Clear sequential steps where each step has a focused responsibility.

### Pattern 2: Routing Workflow

Classify input and direct it to a specialized handler with its own prompt and tools.

```java
// Classifier determines intent
String intent = chatClient.prompt()
    .user("Classify this request into one of: [billing, technical, general]. Request: " + userInput)
    .call()
    .content();

// Route to specialized handler
String response = switch (intent.trim().toLowerCase()) {
    case "billing" -> billingClient.prompt().user(userInput).call().content();
    case "technical" -> technicalClient.prompt().user(userInput).call().content();
    default -> generalClient.prompt().user(userInput).call().content();
};
```

**When to use:** Complex tasks with distinct categories requiring specialized processing.

### Pattern 3: Parallelization

Run multiple LLM calls simultaneously and aggregate results.

```java
List<String> responses = new ParallelizationWorkflow(chatClient)
    .parallel(
        "Analyze how market changes will impact this stakeholder group.",
        List.of("Customers: ...", "Employees: ...", "Investors: ..."),
        3
    );
```

**When to use:** Large volumes of similar items, multiple independent perspectives, or time-critical processing.

### Pattern 4: Orchestrator-Workers

A central LLM dynamically plans and delegates tasks to worker LLMs, then synthesizes results.

**When to use:** Complex tasks where subtasks can't be predicted upfront and require different approaches.

### Pattern 5: Evaluator-Optimizer

One LLM generates output, another evaluates it, and the loop continues until quality criteria are met.

**When to use:** Clear evaluation criteria exist and iterative refinement adds measurable value.

### Pattern 6: The Agentic Loop

The most powerful pattern -- the LLM operates in a loop, using tools and reasoning to accomplish goals autonomously. Spring AI's `ChatModel` handles this natively through its tool-calling loop:

```
User Prompt -> LLM -> [Tool Call Decision]
                         |
                    Yes: Execute Tool -> Return Result -> LLM (loop)
                    No:  Return Final Response
```

You've already seen this in action with the Tool Calling section, where the model decides when to call `DateTimeTool` or `TicketTool` and loops until the task is complete.

**When to use:** Open-ended problems requiring flexible, multi-step solutions with tool usage.

## Advanced Agentic Capabilities

The Spring AI community has built an extensive `spring-ai-agent-utils` toolkit inspired by Claude Code that provides additional agentic patterns. These capabilities are experimental but showcase the direction of agentic AI development.

### Agent Skills

[Agent Skills](https://spring.io/blog/2026/01/13/spring-ai-generic-agent-skills/) are modular folders of instructions, scripts, and resources that AI agents can discover and load on demand. Instead of hardcoding knowledge into prompts, skills provide a flexible way to extend agent capabilities.

Key properties:
- **Portable** across LLM providers -- define once, use with OpenAI, Anthropic, Google Gemini, or any other supported model
- **Composable** -- share across projects, version-control with your code, combine to create complex workflows
- **Discoverable** -- agents find relevant skills through semantic matching

### AskUserQuestionTool

The [AskUserQuestionTool](https://spring.io/blog/2026/01/16/spring-ai-ask-user-question-tool/) implements the "human-in-the-loop" pattern, enabling agents to ask users clarifying questions during execution rather than making assumptions.

Instead of guessing, the agent can pause and ask: *"Which database should I connect to: production or staging?"* This transforms agents from assumption-based responders into collaborative partners.

### TodoWriteTool

The [TodoWriteTool](https://spring.io/blog/2026/01/20/spring-ai-agentic-patterns-3-todowrite) provides structured task management with state tracking, addressing a common problem: agents forgetting tasks mid-session. The agent can create, update, and track a task list, maintaining explicit working memory throughout the session.

### Dynamic Tool Discovery

[Tool Search](https://spring.io/blog/2025/12/11/spring-ai-tool-search-tools-tzolov/) addresses the challenge of large tool libraries. Instead of loading all tool definitions into every request (consuming tokens and reducing accuracy), the model receives only a search tool initially and discovers relevant tools on-demand, achieving **34-64% token savings**.

### Tool Argument Augmenter

The [Tool Argument Augmenter](https://spring.io/blog/2025/12/23/spring-ai-tool-argument-augmenter-tzolov/) enables dynamic augmentation of tool input schemas with additional arguments before sending tool definitions to the LLM. This allows capturing extra information from the model -- such as reasoning, confidence levels, or metadata -- without affecting the underlying tool implementation.

### Agent-to-Agent (A2A) Protocol

[A2A integration](https://spring.io/blog/2026/01/29/spring-ai-agentic-patterns-a2a-integration/) brings multi-agent collaboration to Spring AI. Instead of one monolithic agent, you can compose specialized agents that discover each other's capabilities, exchange messages, and coordinate workflows across platforms using the open A2A protocol. Spring AI enables exposing your agents as A2A-compliant servers.

## Summary

Advisors are the backbone of Spring AI's extensibility:

**Key Concepts:**
- **Advisors** intercept and modify the chat pipeline (requests and responses)
- **Built-in advisors** provide chat memory, RAG, logging, safety, and more
- **Agentic patterns** range from simple chains to autonomous agents with tool calling
- Start with the **simplest pattern** that solves your problem
- The **spring-ai-agent-utils** toolkit provides advanced capabilities like Agent Skills, TodoWrite, AskUserQuestion, dynamic tool discovery, and A2A integration

**Further Reading:**
- [Building Effective Agents with Spring AI](https://spring.io/blog/2025/01/21/spring-ai-agentic-patterns/)
- [Spring AI Advisors Documentation](https://docs.spring.io/spring-ai/reference/api/advisors.html)
- [Effective Agents Reference](https://docs.spring.io/spring-ai/reference/api/effective-agents.html)
- [spring-ai-agent-utils Toolkit](https://github.com/spring-ai-community/spring-ai-agent-utils)
