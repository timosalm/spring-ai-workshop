# Appendix: Spring AI 1.1 vs 2.0

Moving from 1.1 to 2.0 is not a rewrite. The core model is the same: `ChatModel` for direct access, `ChatClient` for everyday use. 2.0 mostly cleans up APIs, simplifies configuration, and removes some modules. This page lists the new features and the breaking changes most likely to touch your code.

> The labs in this workshop already use 2.0. This page helps you read older 1.1 code without surprises.

## Quick overview

| Topic | 1.1 | 2.0 |
|-------|-----|-----|
| Baseline | Spring Boot 3.x, Java 17, Jackson 2 | Spring Boot 4.0/4.1, Java 17+, Jackson 3 |
| Tool execution | Register the advisor yourself; `internalToolExecutionEnabled` flag | Automatic; flag removed |
| Tools | Resolved by bean name (`Function` + `@Description`) | Passed as `ToolCallback` objects |
| Many tools | All tool definitions sent to the model | On-demand discovery with `ToolSearchToolCallingAdvisor` |
| Conversation memory | Default conversation id | Conversation id is required |
| Memory advisor | Several variants | One: `MessageChatMemoryAdvisor` |
| Options objects | Mutable | Immutable, change with `mutate()` |
| Config keys | `...embedding.options.model` | `...embedding.model` (no `.options`) |
| Default temperature | `0.7` set by Spring AI | Provider's own default |
| Anthropic | Custom `AnthropicApi` | Official SDK + builder |
| MCP packages | `org.springaicommunity.mcp.*` | `org.springframework.ai.mcp.*` |
| JSON helpers | `JsonParser`, `ModelOptionsUtils`, `McpJsonParser` | One `JsonHelper` |

## Baseline: Spring Boot 4

Spring AI 2.0 runs on **Spring Boot 4.0.x and 4.1.x**. This is the biggest change. Spring Boot 4 brings:

- **Spring Framework 7** and **Java 17 or newer**.
- **Jackson 3** instead of Jackson 2. The package changed from `com.fasterxml.jackson` to `tools.jackson`. If you use Jackson directly, update the imports.

So upgrade your Spring Boot version first. The Spring AI changes below come on top of that.

## Tool calling

In 2.0, tools run automatically. You no longer register the advisor or set a flag.

```java
// 2.0
String answer = chatClient.prompt("What's the weather in Copenhagen?")
    .tools(currentWeather)
    .call()
    .content();
```

Tools are now objects, not bean names. Register a `ToolCallback`:

```java
@Bean
ToolCallback currentWeather(WeatherService weatherService) {
    return FunctionToolCallback.builder("currentWeather", weatherService::getWeather)
        .description("Get the weather in location")
        .inputType(WeatherRequest.class)
        .build();
}
```

Per-call data (tenant id, API key) goes through `.toolContext(...)`.

## Dynamic tool search (new)

When an app has many tools (for example many MCP servers), sending all tool definitions to the model wastes tokens and lowers accuracy. 2.0 adds the **`ToolSearchToolCallingAdvisor`**. The model first gets only a search tool. It searches for the tools it needs, and only the matching definitions are added to the request.

```java
var advisor = ToolSearchToolCallingAdvisor.builder()
    .toolIndex(toolIndex)
    .build();

ChatClient chatClient = builder
    .defaultTools(new MyTools())  // many tools registered, but NOT all sent to the model
    .defaultAdvisors(advisor)
    .build();
```

It lives in its own module and package:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-tool-search-advisor</artifactId>
</dependency>
```

```java
// 1.1
import org.springframework.ai.tool.toolsearch.advisor.ToolSearchToolCallingAdvisor;
// 2.0
import org.springframework.ai.chat.client.advisor.toolsearch.ToolSearchToolCallingAdvisor;
```

## Conversation memory

The conversation id is now required. This keeps each conversation separate.

```java
// 2.0
String answer = chatClient.prompt()
    .user("Hello again!")
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "user-42-session"))
    .call()
    .content();
```

Use `MessageChatMemoryAdvisor` for memory. If you store memory in a database (JDBC), the schema has a new `sequence_id` column, so existing tables need a small migration.

## Options and configuration

Options objects are immutable. To change one, use `mutate()`:

```java
// 2.0
OllamaChatOptions tuned = baseOptions.mutate()
    .temperature(0.2)
    .build();
```

Config keys dropped the `.options` part:

```properties
# 1.1
spring.ai.openai.embedding.options.model=text-embedding-3-small
# 2.0
spring.ai.openai.embedding.model=text-embedding-3-small
```

Also: a model reports its options with `getOptions()` (not `getDefaultOptions()`), and there is no default temperature anymore. Each provider uses its own default. Set `temperature` yourself if you need a fixed value.

## Anthropic

Anthropic now uses the official Java SDK. Build the model with its builder; `AnthropicApi` is gone.

```java
// 2.0
AnthropicChatModel model = AnthropicChatModel.builder()
    .apiKey(apiKey)
    .defaultOptions(options)
    .build();
```

The default `maxTokens` changed from `500` to `4096`.

## MCP (Model Context Protocol)

MCP is now part of Spring AI. Update the imports:

```java
// 1.1
import org.springaicommunity.mcp.annotation.McpTool;
// 2.0
import org.springframework.ai.mcp.annotation.McpTool;
```

The transport modules (`mcp-spring-webflux`, `mcp-spring-webmvc`) now use the Spring AI group id. An OpenRewrite recipe from Spring AI handles most of these renames for you.

## JSON helpers

The old JSON utility classes are merged into one `JsonHelper`:

```java
// 2.0
JsonHelper jsonHelper = new JsonHelper();
MyType value = jsonHelper.fromJson(json, MyType.class);
String json = jsonHelper.toJson(value);
```

## Removed modules

Some integrations were removed. The main ones:
- **Minimax** → use the Anthropic-compatible endpoint.
- **Azure OpenAI** → use the standard OpenAI starter.

If a starter no longer resolves, check the upgrade notes for its replacement.

## Migration checklist

- Remove `internalToolExecutionEnabled`; register tools as `ToolCallback` beans.
- Pass `ChatMemory.CONVERSATION_ID` on every memory call; use `MessageChatMemoryAdvisor`.
- Change options with `mutate()`; remove `.options` from config keys.
- Set `temperature` yourself if you need `0.7`.
- Use the Anthropic builder (new `maxTokens` default is `4096`).
- Update MCP imports to `org.springframework.ai.mcp.*`.
- Use `JsonHelper` for direct JSON calls.
- Replace removed modules (Azure OpenAI → OpenAI, Minimax → Anthropic).

_Full list: [Spring AI 2.0 Upgrade Notes](https://docs.spring.io/spring-ai/reference/2.0/upgrade-notes.html)._
