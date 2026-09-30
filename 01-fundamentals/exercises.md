# Spring AI Fundamentals Lab

In this lab you start building a **support assistant** that answers customer questions through a REST API. You begin with the low level `ChatModel` API, move on to the fluent `ChatClient`, turn the answers into Java objects with structured output, and finish with advisors for logging and conversation memory.

Read [AI Fundamentals](01-ai-fundamentals.md), [Spring AI Fundamentals](02-spring-ai-fundamentals.md), and [The Advisors API](03-advisors.md) first. This lab applies what those chapters explain.

## How This Lab Works

Your starting point is the minimal Spring Boot application in [`sample-app/`](sample-app/). All paths in this lab are relative to that folder, and all commands run inside it.

You work with two terminals.

- **Terminal 1** runs the application with `./mvnw spring-boot:run`.
- **Terminal 2** sends requests to it with `curl`.

The project includes **Spring Boot DevTools**. It restarts the running application whenever the compiled classes change, so most steps are an edit, a save, and a `curl`. Your IDE has to compile the changed file for this to work.

- **VS Code** with the Java extension compiles on save by default.
- **IntelliJ IDEA** compiles when you run *Build Project* (`Ctrl+F9` or `Cmd+F9`). To compile on save, enable *Build project automatically* in the compiler settings and *Allow auto-make to start even if developed application is currently running* in the advanced settings.

If a change does not show up, or if you changed `pom.xml`, stop the application with `Ctrl+C` and start it again with `./mvnw spring-boot:run`.

Every Java change in this lab shows the **complete file**. Copy it and replace the whole content of the file, so you never have to look for the right place or a missing import.

## 1. Look at the Project

The project was generated with the [Spring Initializr](https://start.spring.io). You do not have to run this command, because the result is already in `sample-app/`.

```bash
curl https://start.spring.io/starter.zip \
  -d dependencies=web,actuator,spring-ai-openai,devtools \
  -d bootVersion=4.1.0 \
  -d type=maven-project \
  -d groupId=com.example \
  -d artifactId=support-assistant \
  -d javaVersion=21 \
  -o sample-app.zip
```

It asks for four dependencies.

- **Spring Web** lets you expose a REST API, so clients can send their questions over HTTP.
- **Spring Boot Actuator** adds health and monitoring endpoints, so you can check that the application is running.
- The **Spring AI OpenAI starter** brings in the client that talks to OpenAI. Another provider only changes this one dependency, for example `spring-ai-anthropic` or `spring-ai-ollama`.
- **Spring Boot DevTools** restarts the application automatically when the compiled classes change.

Open `pom.xml` and find the `spring-ai-starter-model-openai` dependency and the `spring-ai-bom` in the `dependencyManagement` section. The BOM keeps the versions of all Spring AI modules in line, so you never write a version for a single Spring AI dependency. The auto configuration of the starter creates a `ChatModel` and a `ChatClient.Builder` bean for you, which is all you need to start.

Now open `src/main/resources/application.properties`.

```properties
spring.application.name=support-assistant

spring.devtools.restart.enabled=true

spring.mvc.apiversion.use.path-segment=1
spring.mvc.apiversion.supported=1.0
spring.mvc.apiversion.default=1.0
```

The three `spring.mvc.apiversion.*` lines enable API versioning in the URL path. This is a new feature in Spring Framework 7 and Spring Boot 4, and you use it for your REST endpoints, such as `/api/v1/...`. The `spring.devtools.restart.enabled=true` line turns on the automatic restart of DevTools.

## 2. Configure the Model Provider

Append the following lines to `src/main/resources/application.properties`.

```properties

spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.model=gpt-5.6-sol
spring.ai.openai.chat.reasoning-effort=none
spring.ai.openai.chat.temperature=0.7
```

The API key is read from an environment variable, so it never ends up in your code or in Git. The model, the reasoning effort, and the temperature are defaults for every call. `reasoning-effort=none` switches off the extra thinking step of the model, which keeps the answers fast and cheap for this lab. Because all of this lives outside the code, you can switch models or tune the behavior without a recompile. With `spring.ai.openai.base-url` you could also point the client at a gateway or any other OpenAI compatible API.

In **Terminal 1**, export your key and start the application.

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

Wait for `Started SupportAssistantApplication` in the logs. Then check in **Terminal 2** that the application is up.

```bash
curl http://localhost:8080/actuator/health
```

You should see `{"status":"UP"}`. Keep the application running in Terminal 1. From here on each step is a small edit and a `curl` in Terminal 2.

## 3. The Low Level ChatModel API

The starter already put a `ChatModel` bean into your application context, so you can inject it and send your first request. In this part you build up a call to that bean piece by piece, from a plain string to a full `Prompt` with options.

### Create the Service

Create `src/main/java/com/example/support_assistant/SupportAssistantService.java` with the following code. It passes the query of the user to the model with the `call(String)` method.

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

### Create the Controller

Expose the service through a versioned REST endpoint. The `v{version}` path segment is resolved by the API versioning in `application.properties`.

Create `src/main/java/com/example/support_assistant/SupportAssistantController.java` with the following code.

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

Try it.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
```

You get back a plain text answer. From here on you mostly change `generateResponse`.

### Add a System Message

A plain string is always a user message. To give the assistant a persona, send a `SystemMessage` next to the `UserMessage`. The **system** role shapes the tone and the scope of the model, and the **user** role carries the question.

Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantService.java` with the following code.

```java
package com.example.support_assistant;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

@Service
class SupportAssistantService {

    private final ChatModel chatModel;

    SupportAssistantService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    String generateResponse(String query) {
        return chatModel.call(
                new SystemMessage("You are a support agent for the Spring framework. Answer clearly and always include a link to the relevant official docs when one exists, never inventing URLs."),
                new UserMessage(query));
    }
}
```

The answers now reflect the persona.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
```

### Use a PromptTemplate for the User Message

The question of the user is only one part of the message you send. Build the rest of it with a `PromptTemplate`, which fills the `{question}` placeholder at call time.

Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantService.java` with the following code.

```java
package com.example.support_assistant;

import java.util.Map;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

@Service
class SupportAssistantService {

    private final ChatModel chatModel;

    SupportAssistantService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    String generateResponse(String query) {
        var userPromptTemplate = PromptTemplate.builder()
                .template("Answer the following question with a short, well-structured explanation: {question}")
                .variables(Map.of("question", query))
                .build();
        var userMessage = userPromptTemplate.createMessage();

        return chatModel.call(new SystemMessage("You are a support agent for the Spring framework. Answer clearly and always include a link to the relevant official docs when one exists, never inventing URLs."), userMessage);
    }
}
```

Check that the change took effect.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
```

### Full Prompt with ChatOptions and ChatResponse (Optional)

To override the model for one call and to see what comes back besides the text, wrap the messages in a `Prompt` together with `ChatOptions` and read the full `ChatResponse`.

Since Spring AI 2.0 the low level `ChatModel` API needs the options builder of the provider, so the code uses `OpenAiChatOptions.builder()` instead of the portable `ChatOptions.builder()`.

Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantService.java` with the following code.

```java
package com.example.support_assistant;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

@Service
class SupportAssistantService {

    private static final Logger log = LoggerFactory.getLogger(SupportAssistantService.class);

    private final ChatModel chatModel;

    SupportAssistantService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    String generateResponse(String query) {
        var userPromptTemplate = PromptTemplate.builder()
                .template("Answer the following question with a short, well-structured explanation: {question}")
                .variables(Map.of("question", query))
                .build();
        var userMessage = userPromptTemplate.createMessage();

        var prompt = new Prompt(
                List.of(new SystemMessage("You are a support agent for the Spring framework. Answer clearly and always include a link to the relevant official docs when one exists, never inventing URLs."), userMessage),
                OpenAiChatOptions.builder().model("gpt-5.4-mini").temperature(0.0).build());

        var chatResponse = chatModel.call(prompt);
        log.info("Chat Response: {}", chatResponse);
        return chatResponse.getResult().getOutput().getText();
    }
}
```

Call the endpoint again.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
```

The logs in Terminal 1 now show the full `ChatResponse`. Look for the model that served the request and for the token counts under `usage`. These counts are the basis for cost monitoring.

## 4. The Fluent ChatClient API

Now rewrite the same service with the fluent `ChatClient` and see how much of the code above it replaces. Along the way you move the system prompt out of the service and into a file.

### Switch to ChatClient

Spring Boot auto configures a `ChatClient.Builder`, but not a `ChatClient` itself, so first you expose one as a bean.

Create `src/main/java/com/example/support_assistant/SupportAssistantConfiguration.java` with the following code.

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

Now replace the `ChatModel` in your service with the `ChatClient`. Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantService.java` with the following code.

```java
package com.example.support_assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
class SupportAssistantService {

    private static final Logger log = LoggerFactory.getLogger(SupportAssistantService.class);

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
}
```

The logger is not used right now. You need it again at the end of this part.

Check that the change took effect.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
```

### Add a Streaming Endpoint (Optional)

Models generate text token by token. The same chain streams when you swap `.call()` for `.stream()`, which returns a reactive `Flux<String>`. To keep this short you add the streaming call as a throwaway endpoint directly in the controller and remove it again right after. The rest of the lab stays on the blocking `.call()`.

Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantController.java` with the following code. It injects the `ChatClient` into the controller and adds an endpoint that produces Server Sent Events (SSE).

```java
package com.example.support_assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
class SupportAssistantController {

    private final SupportAssistantService service;
    private final ChatClient chatClient;

    SupportAssistantController(SupportAssistantService service, ChatClient chatClient) {
        this.service = service;
        this.chatClient = chatClient;
    }

    @GetMapping(path = "/api/v{version}/chat")
    String chat(@RequestParam String query) {
        return service.generateResponse(query);
    }

    @GetMapping(path = "/api/v{version}/chat/stream",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<String> chatStream(@RequestParam String query) {
        return chatClient.prompt()
                .user(query)
                .stream()
                .content();
    }
}
```

Try it with `curl -N`, which turns off buffering, to see the typewriter effect.

```bash
curl -N -G "http://localhost:8080/api/v1/chat/stream" --data-urlencode "query=Tell me about Spring AI"
```

Notice the `data:` prefix of the SSE protocol on each chunk.

Now remove the throwaway endpoint again, so the controller stays simple for the rest of the lab. Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantController.java` with the following code.

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

### Inline User Template

`PromptTemplate` is still useful, but for templating at the call site the `ChatClient` accepts a lambda that builds the user message with its own placeholder syntax.

Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantService.java` with the following code.

```java
package com.example.support_assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
class SupportAssistantService {

    private static final Logger log = LoggerFactory.getLogger(SupportAssistantService.class);

    private final ChatClient chatClient;

    SupportAssistantService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    String generateResponse(String query) {
        return chatClient.prompt()
                .user(u -> u
                        .text("Answer the following question with a short, well-structured explanation: {question}")
                        .param("question", query))
                .call()
                .content();
    }
}
```

Check that the change took effect.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
```

### Inline System Prompt

The same idea works for the system role. Declare it inline on the request.

Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantService.java` with the following code.

```java
package com.example.support_assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
class SupportAssistantService {

    private static final Logger log = LoggerFactory.getLogger(SupportAssistantService.class);

    private final ChatClient chatClient;

    SupportAssistantService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    String generateResponse(String query) {
        return chatClient.prompt()
                .system("You are a support agent for the Spring framework. Answer clearly and always include a link to the relevant official docs when one exists, never inventing URLs.")
                .user(u -> u
                        .text("Answer the following question with a short, well-structured explanation: {question}")
                        .param("question", query))
                .call()
                .content();
    }
}
```

Check that the change took effect.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
```

### Move the System Prompt to a Default

Repeating the system prompt on every call is duplication. Move it to the `ChatClient` bean as a default instead, where every call through that client picks it up. A `.system(...)` on a single call still wins when you need to override it.

Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantConfiguration.java` with the following code.

```java
package com.example.support_assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SupportAssistantConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("You are a support agent for the Spring framework. Answer clearly and always include a link to the relevant official docs when one exists, never inventing URLs.")
                .build();
    }
}
```

Then drop the `.system(...)` line from the service. Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantService.java` with the following code.

```java
package com.example.support_assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
class SupportAssistantService {

    private static final Logger log = LoggerFactory.getLogger(SupportAssistantService.class);

    private final ChatClient chatClient;

    SupportAssistantService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    String generateResponse(String query) {
        return chatClient.prompt()
                .user(u -> u
                        .text("Answer the following question with a short, well-structured explanation: {question}")
                        .param("question", query))
                .call()
                .content();
    }
}
```

### Move the System Prompt to a File

Keeping the prompt text inside Java code means that every change of the wording needs a recompile. A cleaner option is to keep the prompt in a plain text file under `src/main/resources`. You inject that file with `@Value` as a `Resource` and hand it straight to `defaultSystem`.

Create `src/main/resources/prompts/system-prompt.st` with the following content.

```text
You are a support agent for the Spring framework. Answer clearly and always include a link to the relevant official docs when one exists, never inventing URLs.
```

Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantConfiguration.java` with the following code.

```java
package com.example.support_assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class SupportAssistantConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
            @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt) {
        return builder
                .defaultSystem(systemPrompt)
                .build();
    }
}
```

Check that the change took effect.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
```

### Access the Full Response (Optional)

Ask for the full `ChatResponse` instead of `.content()` to get the metadata of the call as well.

Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantService.java` with the following code.

```java
package com.example.support_assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
class SupportAssistantService {

    private static final Logger log = LoggerFactory.getLogger(SupportAssistantService.class);

    private final ChatClient chatClient;

    SupportAssistantService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

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
}
```

Call the endpoint, then check the logs in Terminal 1 for the full `ChatResponse` with its usage metadata.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
```

## 5. Structured Output

Your endpoint still answers with plain text. In this part you first ask for JSON with a few shot prompt of your own, and then hand the same job to Spring AI so you get a Java object back.

### Prompt Engineering and Few Shot Prompting (Optional)

Write the format rules and two examples into the system prompt, and send the question as the user message.

The prompt contains `{` and `}` characters. Spring AI normally reads `{...}` as a template variable, so this step uses the plain `.system(String)` method, which leaves the braces as normal text.

Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantService.java` with the following code.

```java
package com.example.support_assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
class SupportAssistantService {

    private static final Logger log = LoggerFactory.getLogger(SupportAssistantService.class);

    private final ChatClient chatClient;

    SupportAssistantService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

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
}
```

Call the endpoint with a general question.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
```

The model replies with JSON, even though the method still returns a plain `String`. The examples did the steering, but parsing the text is still your job, and the model can drift away from the format. Let Spring AI do both the prompting and the parsing.

### Define the Response Type

Create `src/main/java/com/example/support_assistant/SupportCategory.java` with the following code. It is an enum for the category of the support request.

```java
package com.example.support_assistant;

enum SupportCategory {
    TECHNICAL,
    BILLING,
    SECURITY,
    GENERAL
}
```

Create `src/main/java/com/example/support_assistant/SupportResponse.java` with the following code. Spring AI passes the `@JsonPropertyDescription` annotations to the model as part of the generated schema, so they tell the model what belongs in each field.

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

### Return the Record

Change `generateResponse` to return the record with `.entity(...)`. Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantService.java` with the following code.

```java
package com.example.support_assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
class SupportAssistantService {

    private final ChatClient chatClient;

    SupportAssistantService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    SupportResponse generateResponse(String query) {
        return chatClient.prompt()
                .user(u -> u
                        .text("Answer the following question with a short, well-structured explanation: {question}")
                        .param("question", query))
                .call()
                .entity(SupportResponse.class);
    }
}
```

The logger is gone, because the service no longer reads the `ChatResponse` itself. The controller does not compile at this point, because it still returns a `String`.

Change the controller to match. Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantController.java` with the following code.

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
    SupportResponse chat(@RequestParam String query) {
        return service.generateResponse(query);
    }
}
```

Call the endpoint again.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
```

The response is now a JSON object with a `category` and an `answer`.

### Enable Native Structured Output

Your `.entity(...)` call is still prompt based. Spring AI appends format instructions to the prompt and trusts the model to follow them. Many providers, OpenAI among them, can also enforce the shape at the API level, which is called **native structured output**. It is off by default, so switch it on once on the `ChatClient` bean, and every `.entity(...)` call uses it from then on.

`AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT` is not an advisor. It is a consumer that sets an advisor parameter, so it goes through `defaultAdvisors(...)`.

Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantConfiguration.java` with the following code.

```java
package com.example.support_assistant;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class SupportAssistantConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
            @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .build();
    }
}
```

Call the endpoint to check that it still works.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
```

The response is the same JSON, but now the provider holds the model to the schema of your type instead of only asking for it in the prompt. One limit to keep in mind is that the native mode of OpenAI does not allow a top level array, so a method that returns a `List` needs a record that wraps the list.

## 6. Advisors

An **advisor** is an interceptor that wraps a `ChatClient` call. It runs *before* the request reaches the model and *after* the response comes back, and several advisors form a chain. In this part you first write your own logging advisor, then replace it with the built-in one.

### Write a Custom Logging Advisor

A blocking advisor implements the `CallAdvisor` interface. There is one method to fill in, `adviseCall`, plus a name and an order.

Create `src/main/java/com/example/support_assistant/LoggingAdvisor.java` with the following code.

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

Three parts are worth a closer look.

- `request.prompt()` is the *before* work. It gives you the full `Prompt` that is about to go to the model, so you can inspect it or even change it before the model sees it.
- `chain.nextCall(request)` invokes the rest of the chain and, at the end, the model. Everything above this line runs on the way in, and everything below it runs on the way out.
- `getOrder()` decides where this advisor sits in the chain. A lower value runs earlier on the way in. `Ordered.LOWEST_PRECEDENCE` makes the logger run last on the way in, so it logs the final request after every other advisor has changed it.

### Register the Advisor

An advisor does nothing until it is on a `ChatClient`. Add it as a default on the client bean, so every call through that client passes through it.

Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantConfiguration.java` with the following code.

```java
package com.example.support_assistant;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class SupportAssistantConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
            @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultAdvisors(new LoggingAdvisor())
                .build();
    }
}
```

Call the endpoint.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
```

Now check the logs in Terminal 1. You see two lines from `LoggingAdvisor`, one with the request that was sent to the model and one with the response that came back. The advisor ran around the call without any change to the service or the controller.

### Swap in the Built-in SimpleLoggerAdvisor

Logging a request and a response is such a common need that Spring AI already ships an advisor for it, the **`SimpleLoggerAdvisor`**. It does the same as your custom advisor, so there is no reason to maintain your own.

Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantConfiguration.java` with the following code.

```java
package com.example.support_assistant;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;

@Configuration
public class SupportAssistantConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
            @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultAdvisors(new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE))
                .build();
    }
}
```

The `SimpleLoggerAdvisor` logs at `DEBUG` level, so it stays quiet in production by default. Append the following lines to `src/main/resources/application.properties` to turn on `DEBUG` for the advisor package.

```properties

logging.level.org.springframework.ai.chat.client.advisor=DEBUG
```

Your custom class is no longer used, so delete it to keep the project clean.

```bash
rm src/main/java/com/example/support_assistant/LoggingAdvisor.java
```

Call the endpoint one more time.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
```

Check Terminal 1 again. This time the `request:` and `response:` lines come from `SimpleLoggerAdvisor`, with the full request and response formatted for you. You get the same behavior with no custom code to maintain.

## 7. Conversation Memory

Each call so far has been stateless. The model has no idea what was asked before, so it cannot follow up on an earlier answer. The built-in **`MessageChatMemoryAdvisor`** fixes that. It stores the messages of a conversation and adds the earlier turns back into the prompt on the next call, so the model can see the history.

### Register the Memory Advisor

The advisor needs a place to keep the messages, a `ChatMemory`. Spring AI auto configures an in memory `ChatMemory` bean for you, so there is nothing to set up for this lab. Inject it into the configuration and register the advisor next to the logger.

Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantConfiguration.java` with the following code.

```java
package com.example.support_assistant;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;

@Configuration
public class SupportAssistantConfiguration {

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
}
```

Because the logger uses `Ordered.LOWEST_PRECEDENCE`, it runs last on the way in, after the memory advisor has added the history. So the logged request shows the full conversation, which is handy to confirm that memory works.

### Tell the Advisor Which Conversation

Memory only makes sense per conversation. The advisor reads a **conversation id** from the request, so it knows whose history to load and where to store the new messages. You pass that id per call as an advisor parameter with the `ChatMemory.CONVERSATION_ID` key. The id is required, and the advisor throws an exception at runtime without it.

Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantService.java` with the following code.

```java
package com.example.support_assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Service
class SupportAssistantService {

    private final ChatClient chatClient;

    SupportAssistantService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    SupportResponse generateResponse(String query, String conversationId) {
        return chatClient.prompt()
                .user(u -> u
                        .text("Answer the following question with a short, well-structured explanation: {question}")
                        .param("question", query))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .entity(SupportResponse.class);
    }
}
```

The controller does not compile at this point, because it still calls `generateResponse` with one argument. You fix that in the next step.

### Provide or Generate a Conversation Id

The conversation id comes from the client. The endpoint reads it from an optional `X-Conversation-Id` request header. When the client sends one, you reuse it and continue that conversation. When it does not, you generate a fresh id. Either way you return the id in the `X-Conversation-Id` response header, so the client knows which value to send on the next request.

Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantController.java` with the following code.

```java
package com.example.support_assistant;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SupportAssistantController {

    private static final String CONVERSATION_ID_HEADER = "X-Conversation-Id";

    private final SupportAssistantService service;

    SupportAssistantController(SupportAssistantService service) {
        this.service = service;
    }

    // curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
    @GetMapping(path = "/api/v{version}/chat")
    ResponseEntity<SupportResponse> chat(@RequestParam String query,
                                         @RequestHeader(value = CONVERSATION_ID_HEADER, required = false) String conversationId) {
        var id = (conversationId != null) ? conversationId : UUID.randomUUID().toString();
        var response = service.generateResponse(query, id);
        return ResponseEntity.ok().header(CONVERSATION_ID_HEADER, id).body(response);
    }
}
```

### See Memory in Action

First call the endpoint without a conversation id. The `-i` option makes `curl` print the response headers.

```bash
curl -i -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
```

Notice the generated `X-Conversation-Id` header in the response. That is the id a client would send back to continue the conversation.

Now run a conversation with two turns and send the same id on both calls. First an opening question.

```bash
curl -G "http://localhost:8080/api/v1/chat" -H "X-Conversation-Id: 123e4567-e89b-12d3-a456-426614174000" --data-urlencode "query=Tell me about Spring AI"
```

Then a follow up in the same conversation.

```bash
curl -G "http://localhost:8080/api/v1/chat" -H "X-Conversation-Id: 123e4567-e89b-12d3-a456-426614174000" --data-urlencode "query=What did I just ask you about?"
```

The model answers in context. Look at the logs in Terminal 1. On the second call the `request:` line of the `SimpleLoggerAdvisor` contains the earlier question and answer next to the new question. The memory advisor added that history, and that is what lets the model follow up.

## Recap

You went from a single `chatModel.call(query)` to a fluent `ChatClient` bean with a default system prompt from a file, structured and native output into a Java record, and advisors for logging and conversation memory.

The [`sample-app/`](../02-advanced-patterns/02-rag/sample-app/) of the RAG lab contains the result of this lab, so you can compare your code with it or continue from there. The next module grounds the answers in your own documents with RAG.
