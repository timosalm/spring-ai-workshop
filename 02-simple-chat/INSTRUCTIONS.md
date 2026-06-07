# Simple Chat with Spring AI

## 0. Generate the project

Change the `PROVIDER` variable below to the AI provider you have access to  (`anthropic`, `ollama`, or `bedrock-converse`) and the Spring Initializr will pull the matching starter.

> **Note:** You don't have to generate the project yourself. The provided `02-simple-chat/sample-app` already bundles all of the starters mentioned above, and you can switch between providers via Spring profiles (e.g. `export SPRING_PROFILES_ACTIVE=ollama`, or `anthropic` / `bedrock-converse`). The default profile uses OpenAI.

```bash
PROVIDER=openai   # or: anthropic | ollama | bedrock-converse

curl https://start.spring.io/starter.zip \
  -d dependencies=web,actuator,spring-ai-${PROVIDER} \
  -d bootVersion=4.0.6 \
  -d type=maven-project \
  -d groupId=com.example \
  -d artifactId=support-assistant \
  -d javaVersion=25 \
  -o sample-app.zip

unzip sample-app.zip -d support-assistant
cd support-assistant
```

## 1. Configure Spring AI

Spring AI configuration is purely declarative. Each provider has its own namespace (`spring.ai.openai.*`, `spring.ai.anthropic.*`, ...) for API key, base URL, default model, sampling, etc. The auto-configuration that comes with the starter wires up a `ChatModel` and a `ChatClient.Builder` bean from these properties.

Replace `src/main/resources/application.properties` with the shared base — these keys are provider-agnostic:

```properties
spring.application.name=support-assistant

spring.mvc.apiversion.use.path-segment=1
spring.mvc.apiversion.supported=1.0
spring.mvc.apiversion.default=1.0
```

The three `spring.mvc.apiversion.*` lines enable path-segment API versioning, which is a new feature in Spring Framework 7/Spring Boot 4.

Now append the block for the provider you chose in step 0.

### OpenAI

```properties
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.model=gpt-5.4-mini
spring.ai.openai.chat.temperature=0.7
```

Run the app:

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

### Anthropic

```properties
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
spring.ai.anthropic.chat.model=claude-haiku-4-5
spring.ai.anthropic.chat.temperature=0.7
```

Run the app:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
./mvnw spring-boot:run
```

### Bedrock Converse

```properties
spring.ai.bedrock.aws.region=us-east-1
spring.ai.bedrock.aws.access-key=${AWS_ACCESS_KEY_ID}
spring.ai.bedrock.aws.secret-key=${AWS_SECRET_ACCESS_KEY}
spring.ai.bedrock.aws.session-token=${AWS_SESSION_TOKEN}
spring.ai.bedrock.aws.chat.temperature=0.7
```

Run the app:

```bash
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
export AWS_SESSION_TOKEN=...        # only when using temporary credentials
./mvnw spring-boot:run
```

### Ollama

No API key — point Spring AI at the model your local Ollama daemon already serves.

```properties
spring.ai.ollama.chat.model=gpt-oss
spring.ai.ollama.chat.temperature=0.7
```

Run the app (make sure the Ollama daemon is up and has pulled the model first, e.g. `ollama pull gpt-oss`):

```bash
./mvnw spring-boot:run
```

## 2. Verify the app is up

Smoke-check the actuator to confirm everything wired up:

```bash
curl http://localhost:8080/actuator/health
```

Keep the app running. From here on, each step is a small edit + a restart + a `curl`.

---

## 3. Create the service

Create `src/main/java/com/example/support_assistant/SupportAssistantService.java`:

```java
package com.example.support_assistant;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

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

The auto-configured `ChatModel` bean comes from the starter. `call(String)` is the convenience overload — Spring AI wraps it in a `Prompt` for you and unwraps the response back to a `String`.

## 4. Create the controller

Create `src/main/java/com/example/support_assistant/SupportAssistantController.java`:

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

    @GetMapping(path = "/api/{version}/chat")
    String chat(@RequestParam String query) {
        return service.generateResponse(query);
    }
}
```

Restart and try it:

```bash
curl -G "http://localhost:8080/api/1.0/chat" \
     --data-urlencode "query=Tell me about Spring AI"
```

You should get back a plain-text answer. Everything from here on is changes to `generateResponse` (and a few extras).

---

## 5. Step 1 — Add a system message

A raw `String` hides the message roles. Replace the body of `generateResponse` with the multi-message overload to steer the model with a `SystemMessage`:

```java
String generateResponse(String query) {
    return chatModel.call(
            new SystemMessage("You are a Spring and AI expert."),
            new UserMessage(query));
}
```

Imports:

```java
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
```

The system role shapes the model's tone and scope; the user role carries the question.

## 6. Step 2 — `PromptTemplate` for the user message

In real apps the user message is rarely a raw string — it's a template filled with runtime data. `PromptTemplate` keeps the wording in one place:

```java
String generateResponse(String query) {
    var userPromptTemplate = PromptTemplate.builder()
            .template("Answer the following question with a short, well-structured explanation: {question}")
            .variables(Map.of("question", query))
            .build();
    var userMessage = userPromptTemplate.createMessage();

    return chatModel.call(new SystemMessage("You are a Spring and AI expert."), userMessage);
}
```

Imports:

```java
import org.springframework.ai.chat.prompt.PromptTemplate;
import java.util.Map;
```

## 7. Step 3 — Full `Prompt` with `ChatOptions` and `ChatResponse`

Sometimes you need to override the model or sampling for a single call, or you want the metadata that comes back with the answer. Wrap the messages in a `Prompt` together with `ChatOptions`, and unwrap the full `ChatResponse`:

```java
String generateResponse(String query) {
    var userPromptTemplate = PromptTemplate.builder()
            .template("Answer the following question with a short, well-structured explanation: {question}")
            .variables(Map.of("question", query))
            .build();
    var userMessage = userPromptTemplate.createMessage();

    var prompt = new Prompt(
            List.of(new SystemMessage("You are a Spring and AI expert."), userMessage),
            ChatOptions.builder().model("gpt-5.4-mini").temperature(0.0).build());

    var chatResponse = chatModel.call(prompt);
    log.info("Chat Response: {}", chatResponse);
    return chatResponse.getResult().getOutput().getText();
}
```

Add a logger field at the top of the class:

```java
private static final Logger log = LoggerFactory.getLogger(SupportAssistantService.class);
```

Imports:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import java.util.List;
```

After restart, the app logs include the full `ChatResponse` — try `chatResponse.getMetadata().getUsage()` to see token counts.

## 8. Step 4 — Switch to `ChatClient`

`ChatModel` works, but everyday code reads better with the fluent `ChatClient`. It also gives us a place to put shared defaults later.

Spring Boot auto-configures a `ChatClient.Builder` but not a `ChatClient` itself, so first we expose one as a bean. Create `src/main/java/com/example/support_assistant/SupportAssistantConfiguration.java`:

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

Inject `ChatClient` alongside `ChatModel` (we'll drop `ChatModel` in a moment, but keep it for now so this step compiles standalone):

```java
private final ChatClient chatClient;

SupportAssistantService(ChatModel chatModel, ChatClient chatClient) {
    this.chatModel = chatModel;
    this.chatClient = chatClient;
}
```

Replace the body of `generateResponse`:

```java
String generateResponse(String query) {
    return chatClient.prompt()
            .user(query)
            .call()
            .content();
}
```

Imports:

```java
import org.springframework.ai.chat.client.ChatClient;
```

## 9. Step 5 — Add a streaming endpoint

Models generate text token by token. Swap `.call()` for `.stream()` to get a reactive `Flux<String>` and stream tokens to the client as soon as they arrive.

Add a method to the service:

```java
Flux<String> streamResponse(String query) {
    return chatClient.prompt()
            .user(query)
            .stream()
            .content();
}
```

Import:

```java
import reactor.core.publisher.Flux;
```

Add a method to the controller:

```java
@GetMapping(path = "/api/{version}/chat/stream",
            version = "1.0",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
Flux<String> chatStream(@RequestParam String query) {
    return service.streamResponse(query);
}
```

Imports:

```java
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
```

Try it with `curl -N` (no buffering) to see the typewriter effect:

```bash
curl -N -G "http://localhost:8080/api/1.0/chat/stream" \
     --data-urlencode "query=Tell me about Spring AI"
```

## 10. Step 6 — Inline user template

`PromptTemplate` is still useful, but for one-off templating at the call site `ChatClient` accepts a lambda that builds the user message with its own placeholder syntax. Replace `generateResponse`:

```java
String generateResponse(String query) {
    return chatClient.prompt()
            .user(u -> u
                    .text("Answer the following question with a short, well-structured explanation: {question}")
                    .param("question", query))
            .call()
            .content();
}
```

No separate `PromptTemplate` needed.

## 11. Step 7 — Add the system prompt to the chain

Same idea for the system role: declare it inline on the request.

```java
String generateResponse(String query) {
    return chatClient.prompt()
            .system("You are a Spring and AI expert.")
            .user(u -> u
                    .text("Answer the following question with a short, well-structured explanation: {question}")
                    .param("question", query))
            .call()
            .content();
}
```

## 12. Step 8 — Move the system prompt to a default

Repeating the system prompt on every call is duplication. Put it on the `ChatClient` bean as a default and every call through that client picks it up automatically.

Add `.defaultSystem(...)` to the bean we created in step 4 (`SupportAssistantConfiguration.java`):

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder) {
    return builder
            .defaultSystem("You are a Spring and AI expert.")
            .build();
}
```

Drop the `.system(...)` line from the service:

```java
String generateResponse(String query) {
    return chatClient.prompt()
            .user(u -> u
                    .text("Answer the following question with a short, well-structured explanation: {question}")
                    .param("question", query))
            .call()
            .content();
}
```

A per-call `.system(...)` would still win if you ever need to override.

## 13. Step 9 — `.chatResponse()` for the full response

`.content()` is a shortcut for the text. When you also want metadata (token counts for billing, finish reason, model id, ...), ask for the full `ChatResponse`:

```java
String generateResponse(String query) {
    var chatResponse = chatClient.prompt()
            .user(u -> u
                    .text("Answer the following question with a short, well-structured explanation: {question}")
                    .param("question", query))
            .call()
            .chatResponse();
    log.info("Chat Response {}", chatResponse);
    return chatResponse.getResult().getOutput().getText();
}
```

Use the same logger you added in step 7. Try logging `chatResponse.getMetadata().getUsage()` to see token counts.

## 14. Step 10 — Structured output with `.entity(...)`

The most interesting jump. Instead of free-form text, ask the model to return a Java type and let Spring AI handle both the prompting and the deserialization.

Create `src/main/java/com/example/support_assistant/SupportResponse.java`:

```java
package com.example.support_assistant;

import java.util.List;

record SupportResponse(String summary, List<String> keyPoints, List<String> docLinks) {}
```

Change `generateResponse` to return the record:

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

Change the controller method to match:

```java
@GetMapping(path = "/api/{version}/chat", version = "1.0")
SupportResponse chat(@RequestParam String query) {
    return service.generateResponse(query);
}
```

Restart and the same `curl` now returns JSON:

```bash
curl -G "http://localhost:8080/api/1.0/chat" \
     --data-urlencode "query=Tell me about Spring AI"
```

```json
{
  "summary": "...",
  "keyPoints": ["...", "..."],
  "docLinks": ["..."]
}
```

No string parsing on your side — Spring AI instructs the model to respond in a matching schema and deserializes the result for you. This is the building block we'll expand on in the dedicated **Structured Output** section.

---

## Recap

| Step | What changed | Key API |
|------|--------------|---------|
| 1 | Add system message | `SystemMessage`, `UserMessage` |
| 2 | Templated user message | `PromptTemplate` |
| 3 | Per-call options + metadata | `Prompt`, `ChatOptions`, `ChatResponse` |
| 4 | Fluent API | `ChatClient.prompt().user(...).call().content()` |
| 5 | Streaming endpoint | `.stream()`, `Flux<String>`, SSE |
| 6 | Inline user template | `.user(u -> u.text(...).param(...))` |
| 7 | Inline system prompt | `.system(...)` |
| 8 | Default system prompt | `ChatClient.Builder#defaultSystem` |
| 9 | Access full response | `.call().chatResponse()` |
| 10 | Structured output | `.call().entity(Class)` |