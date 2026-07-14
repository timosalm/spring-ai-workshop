# Fundamentals — Hands-on Exercises

Your starting point is the minimal Spring Boot app in [`sample-app/`](sample-app/). It already ships the dependencies you need (Spring Web, Actuator, the Spring AI OpenAI starter, and DevTools) plus the Spring AI BOM. Over these steps you build the support assistant up from a single model call to a structured, memory-aware `ChatClient` with advisors.

Each step is a small change. Restart the app (DevTools restarts on a recompile) and `curl` between steps to see the effect.

> The examples use **OpenAI**. To use another provider, swap the starter and the `spring.ai.<provider>.*` properties accordingly.

## 1. Configure the model provider

Add the OpenAI configuration to `src/main/resources/application.properties`:

```properties
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.model=gpt-5.4-mini
spring.ai.openai.chat.temperature=0.7
# Point at a gateway or an OpenAI-compatible API when needed:
# spring.ai.openai.base-url=https://api.openai.com
```

Export your key in the terminal you'll run the app in:

```bash
export OPENAI_API_KEY=sk-...
```

Because the key, model, and options live outside the code, you can switch models or tune behavior without recompiling.

Start the app and confirm it is up:

```bash
cd sample-app
./mvnw spring-boot:run
```

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Keep the app running in a second terminal. From here on, each step is an edit and a `curl`.

## 2. The low-level `ChatModel` API

`ChatModel` is the portable contract over chat providers. The starter's auto-configuration gives you a ready bean to inject.

Create `SupportAssistantService.java`:

```java
package com.example.support_assistant;

import org.springframework.stereotype.Service;
import org.springframework.ai.chat.model.ChatModel;

@Service
class SupportAssistantService {

    private final ChatModel chatModel;

    SupportAssistantService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    String generateResponse(String query) {
        return chatModel.call(query);
    }
}
```

Create `SupportAssistantController.java`. The `v{version}` segment is resolved by the API versioning already configured in `application.properties`:

```java
package com.example.support_assistant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SupportAssistantController {

    private final SupportAssistantService service;

    SupportAssistantController(SupportAssistantService service) {
        this.service = service;
    }

    @GetMapping(path = "/api/v{version}/chat")
    String chat(@RequestParam String query) {
        return service.generateResponse(query);
    }
}
```

Try it:

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
```

You get a plain-text answer. From here on you only change `generateResponse`.

### Add a system message

A raw `String` hides the message roles. A `Prompt` holds `Message` objects, each with a role. The **system** role shapes the model's tone and scope, the **user** role carries the question. Replace `generateResponse`:

```java
String generateResponse(String query) {
    return chatModel.call(
            new SystemMessage("You are a support agent for the Spring framework. Answer clearly and always include a link to the relevant official docs when one exists, never inventing URLs."),
            new UserMessage(query));
}
```

Add the imports:

```java
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
```

### Use a `PromptTemplate` for the user message

In real apps the user message is a template filled with runtime data. Replace `generateResponse`:

```java
String generateResponse(String query) {
    var userPromptTemplate = PromptTemplate.builder()
            .template("Answer the following question with a short, well-structured explanation: {question}")
            .variables(Map.of("question", query))
            .build();
    var userMessage = userPromptTemplate.createMessage();

    return chatModel.call(new SystemMessage("You are a support agent for the Spring framework. Answer clearly and always include a link to the relevant official docs when one exists, never inventing URLs."), userMessage);
}
```

Add the imports:

```java
import org.springframework.ai.chat.prompt.PromptTemplate;
import java.util.Map;
```

### Full `Prompt` with `ChatOptions` and `ChatResponse`

To override the model or sampling for a single call, or to read the metadata that comes back, wrap the messages in a `Prompt` with `ChatOptions` and read the full `ChatResponse`.

> Since Spring AI 2.0 the low-level `ChatModel` API requires provider-specific options, so use `OpenAiChatOptions.builder()` rather than the portable `ChatOptions.builder()`.

Add a logger field to the class:

```java
private static final Logger log = LoggerFactory.getLogger(SupportAssistantService.class);
```

Replace the `return chatModel.call(...)` with:

```java
var prompt = new Prompt(
        List.of(new SystemMessage("You are a support agent for the Spring framework. Answer clearly and always include a link to the relevant official docs when one exists, never inventing URLs."), userMessage),
        OpenAiChatOptions.builder().model("gpt-5.4-mini").temperature(0.0).build());

var chatResponse = chatModel.call(prompt);
log.info("Chat Response: {}", chatResponse);
return chatResponse.getResult().getOutput().getText();
```

Add the imports:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import java.util.List;
```

Call it again, then check the logs for the full `ChatResponse`. The metadata includes `getUsage()`, the **token counts** for prompt and completion, which is the basis for cost monitoring.

## 3. The fluent `ChatClient` API

`ChatClient` wraps a `ChatModel` and adds a fluent builder. Spring Boot auto-configures a `ChatClient.Builder` but not a `ChatClient`, so expose one as a bean.

Create `SupportAssistantConfiguration.java`:

```java
package com.example.support_assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SupportAssistantConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
```

Now switch the service from `ChatModel` to `ChatClient`. Replace the field, constructor, and method:

```java
private final ChatClient chatClient;

SupportAssistantService(ChatClient chatClient) {
    this.chatClient = chatClient;
}

String generateResponse(String query) {
    return chatClient.prompt()
            .user(query)
            .call()
            .content();
}
```

Replace the `ChatModel`-era imports with:

```java
import org.springframework.ai.chat.client.ChatClient;
```

Verify:

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
```

### Streaming (optional look)

Models generate text token by token. Swap `.call()` for `.stream()` to get a reactive `Flux<String>`. As a quick throwaway, inject the `ChatClient` into the controller and add:

```java
@GetMapping(path = "/api/v{version}/chat/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
Flux<String> chatStream(@RequestParam String query) {
    return chatClient.prompt()
            .user(query)
            .stream()
            .content();
}
```

```bash
curl -N -G "http://localhost:8080/api/v1/chat/stream" --data-urlencode "query=Tell me about Spring AI"
```

Note the `data:` SSE prefix on each chunk, then remove the throwaway endpoint (and the injected `ChatClient`) to keep the controller simple.

### Inline user and system prompts

`ChatClient` accepts a lambda that builds the user message with its own placeholder syntax. Update `generateResponse`:

```java
String generateResponse(String query) {
    return chatClient.prompt()
            .system("You are a support agent for the Spring framework. Answer clearly and always include a link to the relevant official docs when one exists, never inventing URLs.")
            .user(u -> u
                    .text("Answer the following question with a short, well-structured explanation: {question}")
                    .param("question", query))
            .call()
            .content();
}
```

### Move the system prompt to a default on the bean

Repeating the system prompt on every call is duplication. Put it on the `ChatClient` bean instead. First drop the `.system(...)` line from the service, then update the bean.

Keep the prompt in a file so wording changes don't need a recompile. Create `src/main/resources/prompts/system-prompt.st`:

```
You are a support agent for the Spring framework. Answer clearly and always include a link to the relevant official docs when one exists, never inventing URLs.
```

Update the bean to inject it and pass it to `defaultSystem`:

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder,
        @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt) {
    return builder
            .defaultSystem(systemPrompt)
            .build();
}
```

Add the imports:

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
```

Every call through this client now picks up the system prompt automatically. A per-call `.system(...)` still wins if you need to override.

## 4. Structured output

The model only ever returns text. To get reliable, structured data you need to get two things right, how you ask and how you read the answer.

### Prompt engineering (by hand)

You can steer the model to JSON with **few-shot prompting**. The braces in the prompt would be read as template variables, so use the plain `.system(String)` method. Update `generateResponse`:

```java
String generateResponse(String query) {
    var chatResponse = chatClient.prompt()
            .system("""
                You are a Spring support classifier.
                Reply only with JSON in this form:
                {"category":"...","answer":"..."}
                The category must be one of: TECHNICAL, BILLING, SECURITY, GENERAL.
                Examples:
                - "Why was I billed twice?"     -> {"category":"BILLING","answer":"..."}
                - "How do I rotate my API key?" -> {"category":"SECURITY","answer":"..."}
                """)
            .user(query)
            .call()
            .chatResponse();
    log.info("Chat Response {}", chatResponse);
    return chatResponse.getResult().getOutput().getText();
}
```

This works, but the result is text you must parse yourself and the model can drift from the format. Let Spring AI do both the prompting and the parsing.

### Let Spring AI do the work with `.entity(...)`

Create the response type. First an enum, `SupportCategory.java`:

```java
package com.example.support_assistant;

enum SupportCategory {
    TECHNICAL,
    BILLING,
    SECURITY,
    GENERAL
}
```

Then a record, `SupportResponse.java`. The `@JsonPropertyDescription` annotations become part of the schema the model sees:

```java
package com.example.support_assistant;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

record SupportResponse(
        @JsonPropertyDescription("The category of the support question: TECHNICAL, BILLING, SECURITY, or GENERAL")
        SupportCategory category,

        @JsonPropertyDescription("The helpful answer to the customer's question")
        String answer
) { }
```

Change `generateResponse` to return the record via `.entity(...)`:

```java
SupportResponse generateResponse(String query) {
    return chatClient.prompt()
            .user(u -> u
                    .text("Answer the following question with a short, well-structured explanation: {question}")
                    .param("question", query))
            .call()
            .entity(SupportResponse.class);
}
```

And change the controller to match:

```java
@GetMapping(path = "/api/v{version}/chat")
SupportResponse chat(@RequestParam String query) {
    return service.generateResponse(query);
}
```

### Enable native structured output

The `.entity(...)` call is **prompt-based**: Spring AI appends format instructions and trusts the model. Many providers, OpenAI among them, can enforce the shape at the API level, called **native structured output**. Turn it on once on the `ChatClient` bean, and every `.entity(...)` call uses it.

`AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT` is not an advisor. It is a `Consumer<ChatClient.AdvisorSpec>` that sets an advisor parameter, so it goes through the consumer form of `defaultAdvisors(...)`. Add it to the bean:

```java
return builder
        .defaultSystem(systemPrompt)
        .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
        .build();
```

Add the import:

```java
import org.springframework.ai.chat.client.AdvisorParams;
```

Call the endpoint again. The response is the same JSON, but now the provider constrains the model to your type's schema instead of only being asked to in the prompt.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
# {"category":"...","answer":"..."}
```

> One caveat: OpenAI's native mode does not allow a top-level array, so a method returning a `List` needs a record that wraps the list.

## 5. Advisors

An **advisor** is an interceptor that wraps a `ChatClient` call, acting before the request reaches the model and after the response comes back. Several advisors form a chain, and adding one is a configuration change on the client while your prompting code stays the same.

### A custom logging advisor

Create `LoggingAdvisor.java`:

```java
package com.example.support_assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;

class LoggingAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(LoggingAdvisor.class);

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        log.info("Request to model: {}", request.prompt().getContents());   // before
        ChatClientResponse response = chain.nextCall(request);              // delegate down the chain
        log.info("Response from model: {}",
                response.chatResponse().getResult().getOutput().getText()); // after
        return response;
    }

    @Override
    public String getName() {
        return "LoggingAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
```

Register it as a default on the client bean, next to the default system prompt:

```java
return builder
        .defaultSystem(systemPrompt)
        .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
        .defaultAdvisors(new LoggingAdvisor())
        .build();
```

Call the endpoint and check the logs — you'll see two lines from `LoggingAdvisor`, without any change to the service or controller.

### Swap in the built-in `SimpleLoggerAdvisor`

Spring AI ships this advisor already. Replace `new LoggingAdvisor()` with:

```java
.defaultAdvisors(new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE))
```

Add the imports:

```java
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.core.Ordered;
```

It logs at `DEBUG`, so enable that for the advisor package in `application.properties`:

```properties
logging.level.org.springframework.ai.chat.client.advisor=DEBUG
```

Delete `LoggingAdvisor.java` — it is no longer used.

### Add conversation memory

The **`MessageChatMemoryAdvisor`** stores the messages of a conversation and replays earlier turns on the next call. It needs a `ChatMemory`, which Spring AI auto-configures. Inject it and register the advisor next to the logger:

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder,
        @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt,
        ChatMemory chatMemory) {
    return builder
            .defaultSystem(systemPrompt)
            .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
            .defaultAdvisors(
                    new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE),
                    MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build();
}
```

Add the imports:

```java
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
```

The advisor needs a **conversation id** per request to know which history to load. Update the service to accept it and set it on the call:

```java
SupportResponse generateResponse(String query, String conversationId) {
    return chatClient.prompt()
            .user(u -> u
                    .text("Answer the following question with a short, well-structured explanation: {question}")
                    .param("question", query))
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .call()
            .entity(SupportResponse.class);
}
```

Add the import `import org.springframework.ai.chat.memory.ChatMemory;` to the service.

Read the id from an optional `X-Conversation-Id` header in the controller, generating one when absent, and return it so the client can continue the conversation:

```java
private static final String CONVERSATION_ID_HEADER = "X-Conversation-Id";

@GetMapping(path = "/api/v{version}/chat")
ResponseEntity<SupportResponse> chat(@RequestParam String query,
                                     @RequestHeader(value = CONVERSATION_ID_HEADER, required = false) String conversationId) {
    var id = (conversationId != null) ? conversationId : UUID.randomUUID().toString();
    var response = service.generateResponse(query, id);
    return ResponseEntity.ok().header(CONVERSATION_ID_HEADER, id).body(response);
}
```

Add the imports:

```java
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import java.util.UUID;
```

### See memory in action

Run a two-turn conversation, sending the same id on both calls:

```bash
curl -G "http://localhost:8080/api/v1/chat" -H "X-Conversation-Id: 123e4567-e89b-12d3-a456-426614174000" --data-urlencode "query=Tell me about Spring AI"
```

```bash
curl -G "http://localhost:8080/api/v1/chat" -H "X-Conversation-Id: 123e4567-e89b-12d3-a456-426614174000" --data-urlencode "query=What did I just ask you about?"
```

On the second call the model answers in context, because the memory advisor added the earlier turn to the prompt.

## Recap

You went from a single `chatModel.call(query)` to a fluent `ChatClient` bean with a default system prompt, structured (and native) output onto a Java record, and advisors for logging and conversation memory. The next module grounds these answers in your own documents with RAG.
