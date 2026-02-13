---
title: Simple Chat
---

# Building the Chat Module

In this module, you'll build the **chat** module - your first Spring AI integration using the `ChatClient` API.

## Learning Objectives

- Understand Spring AI dependencies and configuration
- Use the `ChatClient` fluent API
- Create REST endpoints that communicate with an LLM
- Test blocking and streaming responses

## Required Dependencies

To use Spring AI with OpenAI (or any other supported AI provider), you have to add the related starter dependency to your `pom.xml`, in this case `org.springframework.ai:spring-ai-starter-model-openai`.

```editor:select-matching-text
file: ~/sample-app/pom.xml
text: "spring-ai-starter-model-openai"
```
You also need the Spring AI BOM (Bill of Materials) to manage versions:
```editor:select-matching-text
file: ~/sample-app/pom.xml
text: "spring-ai-bom"
```

This starter provides the implementation and Spring Boot auto-configuration for interactions with OpenAI's via the `ChatClient`.

## Required Configuration

Spring AI requires additional configuration to connect to an AI provider. Here's the configuration for OpenAI:

```editor:select-matching-text
file: ~/sample-app/src/main/resources/application-openai.yaml
text: "openai:"
```

| Property | Description |
|----------|-------------|
| `api-key` | Your OpenAI API key (in this case from environment variable) |
| `chat.options.model` | Which model to use (gpt-4o, gpt-4-turbo, etc.) |

For more configuration options, see the documentation [here](https://docs.spring.io/spring-ai/reference/1.0/api/chat/openai-chat.html#_chat_properties).

{{< note >}}
This workshop uses a mock service by default. The mock service uses the same configuration structure but points to `http://localhost:8080/mock` instead of OpenAI's servers.
{{< /note >}}

## Create ChatController

First, let's create the chat module directory:

```terminal:execute
command: mkdir -p ~/sample-app/src/main/java/com/example/supportassistant/chat
session: 1
```

Now let's create a simple chat controller. Click to add the code:

```editor:append-lines-to-file
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatController.java
description: Add ChatController
text: |
  package com.example.supportassistant.chat;

  import org.springframework.ai.chat.client.ChatClient;
  import org.springframework.web.bind.annotation.GetMapping;
  import org.springframework.web.bind.annotation.RequestMapping;
  import org.springframework.web.bind.annotation.RequestParam;
  import org.springframework.web.bind.annotation.RestController;

  @RestController
  @RequestMapping("/chat")
  public class ChatController {

      private final ChatClient chatClient;

      public ChatController(ChatClient chatClient) {
          this.chatClient = chatClient;
      }

      @GetMapping("/simple")
      public String simpleChat(
              @RequestParam(defaultValue = "What is Tanzu Spring?") String query) {

          return chatClient.prompt()
                  .user(query)
                  .call()
                  .content();
      }
  }
```

Let's examine what this code does:

```editor:select-matching-text
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatController.java
text: "chatClient.prompt()"
```

1. `chatClient.prompt()` - Starts building a new prompt
2. `.user(query)` - Adds the user's question as a user message
3. `.call()` - Executes the request (blocking)
4. `.content()` - Extracts just the text content from the response

## Start the Application

```terminal:execute
command: cd ~/sample-app && ./mvnw spring-boot:run
session: 2
```

{{< note >}}
Wait for "Started SupportAssistantApplication" in the logs before testing.
{{< /note >}}

## Test the Chat Endpoint

Try the default query about Tanzu Spring:

```execute
http -b localhost:8080/chat/simple
```

Now try a custom query:

```execute
http -b localhost:8080/chat/simple query=="What support options are available for Spring?"
```

## Add Streaming Support

For longer responses, streaming provides a better user experience. Let's add a streaming endpoint:

```editor:insert-lines-before-line
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatController.java
line: 8
description: Add streaming endpoint to ChatController
cascade: true
text: import reactor.core.publisher.Flux;
```
```editor:insert-lines-before-line
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatController.java
line: 29
hidden: true
text: |2

      @GetMapping(value = "/stream", produces = "text/event-stream")
      public Flux<String> streamChat(
              @RequestParam(defaultValue = "What is Tanzu Spring?") String query) {

          return chatClient.prompt()
                  .user(query)
                  .stream()
                  .content();
      }
```

The key differences:
- Returns `Flux<String>` instead of `String`
- Uses `.stream()` instead of `.call()`
- Sets `produces = "text/event-stream"` for SSE

Notice the "data:" prefix of the SSE protocol.

## Restart and Test Streaming

Stop the application first:

```terminal:interrupt
session: 2
```

Restart:

```terminal:execute
command: cd ~/sample-app && ./mvnw spring-boot:run
session: 2
```

Test the streaming endpoint:

```execute
curl -N http://localhost:8080/chat/stream
```

You'll see the response arrive word by word!

## Understanding the Response

Let's also see what metadata is available. Add another endpoint that returns the full response:

```editor:insert-lines-before-line
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatController.java
line: 9
description: Add detailed response endpoint to ChatController
cascade: true
text: import org.springframework.ai.chat.model.ChatResponse;
```
```editor:insert-lines-before-line
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatController.java
line: 40
hidden: true
text: |2

      @GetMapping("/detailed")
      public ChatResponse detailedChat(
              @RequestParam(defaultValue = "What is Tanzu Spring?") String query) {

          return chatClient.prompt()
                  .user(query)
                  .call()
                  .chatResponse();
      }
```

Restart and test:

```terminal:interrupt
session: 2
```

```terminal:execute
command: cd ~/sample-app && ./mvnw spring-boot:run
session: 2
```

```execute
http -b localhost:8080/chat/detailed | jq
```

This shows the full response including:
- `result` - The actual response with finish reason
- `metadata` - Model information, request details
- `metadata.usage` - Token counts (important for cost tracking!)

## Stop the Application

```terminal:interrupt
session: 2
```

## Summary

You've built the foundation of the chat module.

**Key Concepts:**
- `spring-ai-starter-model-openai` provides auto-configuration
- Configure via `spring.ai.openai.*` properties
- `ChatClient` provides a fluent API for LLM interaction
- Use `.call()` for blocking, `.stream()` for streaming
- `.content()` returns text, `.chatResponse()` returns full metadata

Next, you'll enhance the chat with prompt engineering techniques!
