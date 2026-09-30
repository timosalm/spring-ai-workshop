# Agentic Patterns With Spring AI

The patterns from the previous section are just shapes you build with the `ChatClient`, the structured output, and the advisors you already know.

Spring AI is still evolving, and today the single agent with harness patterns around one loop is where the framework is most mature. 
Complex multi-agent orchestration is not part of the API yet, which matches the state of the industry, where its value is still being proven.

That said, the Spring AI team is already preparing the next steps, with durable multi-agent orchestration and higher level, more opinionated agentic abstractions targeted at the coming releases.

Some patterns are already provided out of the box, and this article walks through those in detail. The remaining ones exist as experimental implementations and are planned to become generally available in future releases. For those, the article shows what each one provides and how you would use it in code, but treat the samples as a first impression rather than as a stable API, because they will probably still change.

## Giving an Agent Hundreds of Tools With Tool Search

You know the problem from the previous section. As soon as an agent has a high number of tools, often because several MCP servers feed into it, sending every tool definition on every request costs a lot of tokens and makes the model pick the wrong tool more often.

The answer of Spring AI, generally available since the 2.0 release, is the **Tool Search Tool**. Instead of loading all tools upfront, the agent *discovers them on demand*.

### How it works

All your registered tools go into a **`ToolIndex`**, but they are **not** sent to the model. In the first request the model only sees the Tool Search Tool. When it needs a capability, it calls that search tool with a query. The index returns the tools that match, and only their full definitions are added to the next request. The model now sees the search tool plus a few relevant tools, calls them, and writes its answer. Hundreds of tools stay available this way, and only a few definitions are in the context at a time.

Spring AI provides this as an advisor, the **`ToolSearchToolCallingAdvisor`**. It is the `ToolCallingAdvisor` you already know, with the search step added. The easiest way to use it is the starter, because it contains the advisor and its auto configuration.

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-tool-search-advisor</artifactId>
</dependency>
```

One property switches it on. You do not have to write any code for the setup.

```properties
spring.ai.chat.client.tool-search-advisor.enabled=true
```

The auto configuration does two things. It creates a `ToolIndex` bean of the type you selected, and it adds the `ToolSearchToolCallingAdvisor` to the `ChatClient.Builder`. The advisor replaces the default `ToolCallingAdvisor`, so tool calling works as before and only gets the search step in front of it.

Your own configuration stays the same. You register your tools as before, and the advisor makes sure that not all of them are sent to the model.

```java
ChatClient chatClient = builder
    .defaultTools(supportTools)  // hundreds of tools registered, NOT all sent to the model
    .build();

String answer = chatClient.prompt("""
        My Spring AI application fails to start after the upgrade to 2.0.
        Open a support ticket for it and tell me the ticket number.
        """)
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "user-42"))
    .call()
    .content();
```

The advisor keeps one index per session, so every request has to carry a session id. It reads that id from the advisor context under the `ChatMemory.CONVERSATION_ID` key, the same key you already use for the chat memory. When your application works with a memory advisor, the id is therefore already in the context and you get the session scoping for free.

### What you can configure

Only `enabled` is required. All other properties have a default that works, and they all start with `spring.ai.chat.client.tool-search-advisor`.

The property you change most often is `max-results`. It defines how many tools one search returns, and with that how many tool definitions are added to the context. A small number keeps the prompt cheap. A larger number makes it more likely that the right tool is in the result.

`reference-tool-name-accumulation` is `true` by default. The tools from earlier searches then stay available in the conversation. If you set it to `false`, only the tools from the last search stay available, which keeps long conversations shorter.

The model receives built-in instructions that explain the search tool to it. With `system-message-suffix` you can customize them.

The most important property is `tool-index-type`, because it defines how the search works.

### Choosing how tools are searched

The `ToolIndex` interface hides the search implementation from the rest of your setup. Spring AI provides three implementations.

| `tool-index-type` | Implementation | Best for | Needs |
|----------|----------------|----------|-------|
| `regex` (default) | `RegexToolIndex` | Few tools, predictable wording | Nothing extra |
| `lucene` | `LuceneToolIndex` | Many tools, free phrasing | Lucene, included in the starter |
| `vector` | `VectorToolIndex` | Many tools, matching by meaning | A `VectorStore` bean |

The `RegexToolIndex` is the default, so you get it when you do not set `tool-index-type`. Its name promises less than it does, because you never write a regular expression yourself. The index builds one for you out of the query of the model. It lowercases the words, removes stop words, escapes special characters, and joins the rest with an OR into a case insensitive pattern such as `(?i)(open|support|ticket)`. A very long query is cut off at 200 characters. The pattern then runs against the names and the descriptions of all registered tools, and the matches are scored. If you need a different strategy, you can extend the class and convert the query in your own way.

This costs you no extra dependency and no model call, which makes it the cheapest of the three. It is also the most limited one, because it compares words and not meaning. A tool is only found when the query uses words that appear in its name or description.

The `VectorToolIndex` searches by meaning. It works like the retrieval step in the RAG section, only with tools instead of documents. Each tool description becomes an embedding and is stored in a vector store. The search query becomes an embedding as well, and the index returns the tools that are closest in meaning. The agent can ask for "something to open a ticket" and still find a tool with the name `createSupportCase`.

The `LuceneToolIndex` uses [Apache Lucene](https://lucene.apache.org/), the open source search library that also powers Elasticsearch and Solr. It indexes the words in your tool names and descriptions and compares the query with these words. There is no model call and no embedding, so it is fast and cheap. The disadvantage is that it only finds a tool when the query uses similar words. With `lucene.min-score-threshold` you define how good a match has to be before it is returned.

### Building the advisor yourself

If the properties are not enough for your use case, use the `spring-ai-tool-search-advisor` module without the starter. It contains only the advisor. You then create the index and the advisor yourself, and you register the advisor with `defaultAdvisors`.

```java
var advisor = ToolSearchToolCallingAdvisor.builder()
    .toolIndex(new VectorToolIndex(vectorStore))
    .maxResults(5)
    .build();
```

## Implementations for the Workflow Patterns (Experimental)

If you want to see the workflow patterns as running code rather than as diagrams, the Spring AI team wrote them up in [Building Effective Agents with Spring AI](https://spring.io/blog/2025/01/21/spring-ai-agentic-patterns), and each one has a complete sample implementation in the [agentic-patterns examples](https://github.com/spring-projects/spring-ai-examples/tree/main/agentic-patterns) repository.

### LLM as a Judge and Self-Refine

The evaluator-optimizer pattern is packaged as a reusable advisor. Built on the experimental *recursive advisors* of Spring AI, a [`SelfRefineEvaluationAdvisor`](https://spring.io/blog/2025/11/10/spring-ai-llm-as-judge-blog-post) generates a response, has a separate judge model rate it on a structured scale, and retries with that feedback until it passes. Using a different model as the judge avoids the bias a model has towards its own output. Because it is an advisor, you add it with `defaultAdvisors` and the whole evaluate and improve loop happens inside a single `call()`. This is the first of the two multi-agent setups from the previous section, and Spring AI makes it a one line change to your client. A sample implementation is available [here](https://github.com/spring-projects/spring-ai-examples/blob/main/advisors/evaluation-recursive-advisor-demo/src/main/java/com/example/advisor/SelfRefineEvaluationAdvisor.java).

## Agentic Patterns in the spring-ai-community Project (Experimental)

Several of the other patterns already have a ready-made implementation as well. Those are maintained in the **`spring-ai-community`** project, and most of them in the [spring-ai-agent-utils](https://github.com/spring-ai-community/spring-ai-agent-utils) repository.

They are plain tools, so you register them on the `ChatClient` the same way you registered your own ticket tools. A BOM and a single dependency bring all of them in, with the Agent-to-Agent (A2A) support as the only exception, because that one ships as artifacts of its own.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springaicommunity</groupId>
            <artifactId>spring-ai-agent-utils-bom</artifactId>
            <version>0.11.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils</artifactId>
</dependency>
```

The Spring team introduced each pattern in an [ongoing blog series](https://spring.io/blog/2026/04/15/spring-ai-session-management#agentic-patterns-series), which is the place to look for the full picture. Here is what already exists and how you would use it.

### Agent Skills

The [`SkillsTool`](https://spring.io/blog/2026/01/13/spring-ai-generic-agent-skills) points at one or more directories of skills. Each skill is a folder with a `SKILL.md` file, and the name and the description from its YAML front matter are all that is loaded at startup. The full instructions follow only when the task matches, which is the same load on demand idea as Tool Search, applied to instructions instead of tools.

```java
@Value("classpath:skills")
private Resource skillsResource;

ChatClient chatClient = builder
    .defaultTools(
        SkillsTool.builder().addSkillsResource(skillsResource).build(),
        FileSystemTools.builder().build(), 
        ShellTools.builder().build())
    .build();
```

A skill can also ship reference files and scripts, so it is usually combined with the `FileSystemTools` and `ShellTools` from the same library, which let the agent read those files and run those scripts. You can also point at a directory on disk instead of a classpath resource, for example with `.addSkillsDirectory(".claude/skills")`.

### Plan and Execute

The [`TodoWriteTool`](https://spring.io/blog/2026/01/20/spring-ai-agentic-patterns-3-todowrite) lets the agent write its own task list and keep it current while it works. Every item on that list has three fields. The `content` field says what needs to be done, `activeForm` is the same step worded as something in progress so it reads well while the agent works on it, and `status` is one of `pending`, `in_progress`, or `completed`. Only one item may be in progress at a time, which walks the model through the steps one after the other instead of letting it skip ahead. The tool describes itself as being for tasks with three or more steps, so the model decides on its own when a written plan is worth the effort.

```java
ChatClient chatClient = builder
    .defaultTools(TodoWriteTool.builder()
        .todoEventHandler(todos -> ...)
        .build())
    .defaultAdvisors(MessageChatMemoryAdvisor.builder(
            MessageWindowChatMemory.builder().build())
        .build())
    .build();
```

Two things matter here. Chat memory is required, because without it the list does not survive from one model call to the next. The event handler is optional and hands you every update of the list and its status, so you can show the live plan in a user interface.

### Ask-User-Question

The [`AskUserQuestionTool`](https://spring.io/blog/2026/01/16/spring-ai-ask-user-question-tool) puts a human in the loop. Instead of guessing at an ambiguous instruction, the agent asks a structured question with two to four options, and you decide how that question reaches the user.

```java
ChatClient chatClient = builder
    .defaultTools(AskUserQuestionTool.builder()
        .questionHandler(this::askUser)
        .build())
    .build();

Map<String, String> askUser(List<Question> questions) {
    // show the question and its options, then return one answer per question
}
```

The handler is a normal Java method, so a command line application can read from the console. A web application has to bridge the gap to an asynchronous user interface, usually with a `CompletableFuture` that the handler waits on until the answer arrives over WebSocket or REST.

### Subagents

The [Task tool](https://spring.io/blog/2026/01/27/spring-ai-agentic-patterns-4-task-subagents) lets the main agent hand a piece of work to a separate agent with its own context window, its own system prompt, its own tools, and even its own model. Only the result comes back, so a long research or review step never clutters the main conversation, and a simple job can go to a cheap model while the hard analysis stays with a strong one.

```java
ChatClient chatClient = builder
    .defaultTools(TaskTool.builder()
        .subagentTypes(ClaudeSubagentType.builder().build())
        .build())
    .build();
```

The subagents themselves are Markdown files in an `agents` directory, the same shape as Skills, with a name, a description that tells the main agent when to delegate, a list of allowed and forbidden tools, and the model to use. The main agent picks one by its description. This is the second multi-agent setup, and note how the main agent stays in charge. It delegates a task and reads the result, which is what keeps such a system easy to follow.

### Agent-to-Agent (A2A)

Once your subagents no longer live in the same application, the Agent2Agent protocol lets them find and call each other across services, teams, and languages. It is a protocol of its own, just like MCP, so it has dedicated sections at the end of this module.

The series has grown beyond this list as well, with long term memory through `AutoMemoryTools` and an event sourced Session API that is meant to replace `ChatMemory`.
