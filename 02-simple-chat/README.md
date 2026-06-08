## Models Are Just REST APIs

For all their sophistication, the large language models you'll work with are reached the same way as any other cloud service: over HTTP. You send a JSON request containing your prompt and some options to a provider's endpoint, and you get back a JSON response containing the generated text, token counts, and metadata. Anthropic, Mistral, OpenAI, or Ollama, all follow this same request/response model, with provider-specific differences.

This is good news, because integrating with REST APIs is exactly what Spring has always excelled at. The ecosystem already gives you HTTP clients, JSON (de)serialization, connection pooling, retries, and externalized configuration. In principle you could call a model yourself with a `RestClient`, hand-build the request body, parse the response, and manage your API key.

You *could*, but you'd quickly find yourself re-implementing a lot of plumbing:
- Constructing and parsing each provider's specific JSON schema by hand
- Re-writing that code every time you switch or add a provider
- Wiring up streaming, retries, error handling, and observability yourself
- Mapping raw responses into domain objects, managing conversation history, and so on

This is the gap Spring AI fills. It builds on the same HTTP foundations but gives you a **purpose-built abstraction for talking to models**, so you describe *what* you want to ask rather than *how* to format the HTTP call. Switching providers becomes a configuration change, not a rewrite. 

If you've used Spring Data, this will feel familiar. Just as Spring Data gives you one consistent programming model over different data stores, Spring AI gives you one consistent programming model over different AI providers and models, while still letting you reach provider-specific options when you need them.

Spring AI delivers that programming model through two complementary abstractions. The low-level `ChatModel` offers a direct API that handles the HTTP call, serializing your request and mapping the provider's raw JSON back into Java objects. The higher-level `ChatClient` is built on top of it for easier everyday use and more advanced capabilities.

## Getting Started: Dependencies and Configuration

Before writing any code, you need the right starter on your classpath. Spring AI ships a dedicated Spring Boot starter per provider, so you add the one matching the model you want to talk to. For OpenAI:

```xml
<!-- Maven: pom.xml -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

```groovy
// Gradle: build.gradle
implementation 'org.springframework.ai:spring-ai-starter-model-openai'
```

Other providers follow the same naming, for example `spring-ai-starter-model-anthropic` or `spring-ai-starter-model-ollama`. 

Also import the **Spring AI BOM** so every Spring AI artifact resolves to a consistent, compatible version (and you can omit explicit versions on the starters above):

```xml
<!-- Maven: pom.xml -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>2.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

```groovy
// Gradle: build.gradle
dependencies {
    implementation platform('org.springframework.ai:spring-ai-bom:2.0.0')
}
```

The starter pulls in the provider implementation plus the Spring Boot auto-configuration, which initializes related beans for you.

The rest is configuration, which lives in `application.properties`/`application.yml` just like any other Spring Boot setting. Each provider has its own property namespace (`spring.ai.openai.*`, `spring.ai.ollama.*`, ...), but they expose similar kinds of settings. Taking OpenAI as the example:

```properties
# Authentication
spring.ai.openai.api-key=${OPENAI_API_KEY}
# The endpoint, point it at a mock, gateway, or compatible API when needed
spring.ai.openai.base-url=https://api.openai.com
# The model to use, e.g. gpt-5.5 or gpt-5.4-mini
spring.ai.openai.chat.model=gpt-5.4-mini
# Set some request parameters, such as sampling randomness. Lower is more deterministic, higher more creative
spring.ai.openai.chat.temperature=0.7
```
Because the model, and other options are externalized, you can tune behavior or switch models without touching code.

### Mixing providers in one application

Because each provider has its own namespace and starter, you can use several at once, for example one provider for chat and another for image generation, simply by adding both starters and configuring both. When more than one starter on the classpath can serve the same model type, use the `spring.ai.model.*` properties to pick which one handles it:

```properties
spring.ai.model.chat=ollama
spring.ai.model.image=openai
```

You can also vary providers across environments using Spring profiles (e.g. a local model in `application-dev.properties`, a hosted one in `application-prod.properties`).

## The low-level `ChatModel` API

`ChatModel` is the foundational interface that every chat provider implements (`OpenAiChatModel`, `AnthropicChatModel`, `OllamaChatModel`, ...). It is intentionally minimal, you hand it a `Prompt` and it returns a `ChatResponse`.

```java
ChatResponse response = chatModel.call(new Prompt("Tell me about Spring AI"));
String text = response.getResult().getOutput().getText();
```

Thanks to the auto-configuration the starter provides, this `ChatModel` is already a Spring bean, so you can inject it into any component and call it as shown above, without constructing it yourself.

For convenience, `call()` is also overloaded to accept a plain `String` directly, which Spring AI simply wraps in a `Prompt` for you. This string-based overload also simplifies the other end. Instead of returning a `ChatResponse` you have to unwrap, it can hand you the response content straight back as a `String`. So the two-line example above collapses to a single call:

```java
String text = chatModel.call("Tell me about Spring AI");
```

That shortcut hides what a `Prompt` really is, though. Under the hood, a `Prompt` holds an ordered list of Message objects, each assigned one of three roles defined by the underlying APIs: "system", "user", or "assistant". These roles structure the conversation and shape how the model interprets each message.

The "system" role sets the overall behavior and tone of the model, typically at the start of a conversation, and is represented in Spring AI by `SystemMessage`. The "user" role carries the human side of the conversation, covering questions, instructions, or input, and maps to `UserMessage`. Finally, the "assistant" role represents the model's own responses and, when included in a prompt, provides prior turns of the conversation as context. Spring AI represents this role with `AssistantMessage`.

Since `Prompt` also accepts a list of `Message` objects, you can compose them explicitly:
```java
Prompt prompt = new Prompt(List.of(
    new SystemMessage("You are a Spring and AI expert."),
    new UserMessage("Tell me about Spring AI")));
ChatResponse response = chatModel.call(prompt);
```

In practice, prompts are rarely fixed strings, they depend on runtime data. Spring AI's `PromptTemplate` lets you write a message with `{placeholder}` variables and fill them in at call time, keeping your prompts reusable and parameterized instead of building strings by hand:
```java
PromptTemplate promptTemplate = PromptTemplate.builder()
        .template("Tell me about {topic}")
        .variables(Map.of("topic", "Spring AI"))
        .build();
ChatResponse response = chatModel.call(promptTemplate.create());
```
If the default placeholder syntax conflicts with your data, you can [configure a custom template renderer](https://docs.spring.io/spring-ai/reference/api/prompt.html#_using_a_custom_template_renderer) to use a different delimiter.

A `Prompt` is more than its messages, though. It also carries a set of **`ChatOptions`** such as the model name, and `maxTokens`. You can define these as defaults in configuration and then override them on an individual call, so, for example, you can change the model for one request without changing anything globally:

```java
ChatResponse response = chatModel.call(new Prompt(
    "Tell me about Spring AI",
    ChatOptions.builder().model("gpt-5.4-mini").build()));
```

The `ChatResponse` that comes back wraps one or more **`Generation`** objects. A `Generation` is a single candidate completion, the assistant's message together with metadata such as its finish reason. Most requests return exactly one, which is why the `getResult()` shortcut above is so common, but a model can be asked to produce several alternatives.

Beyond the generated text, the `ChatResponse` also exposes metadata about the call via `getMetadata()`. This includes details such as the model that served the request and, importantly, `getUsage()` — the **token counts** for both the prompt and the completion. Tracking usage matters because providers bill per token, so surfacing these counts is the foundation for cost monitoring and budgeting.

The `call()` method shown so far is blocking: it waits for the entire completion before returning. Because models generate text token by token, you can also consume the response as a stream, displaying each piece the moment it is produced rather than waiting for the whole answer. This is what powers the "typewriter" effect in chatbots, where words appear progressively, dramatically improving perceived responsiveness for longer answers. For this, Spring AI provides a companion `StreamingChatModel` interface whose `stream()` method returns a reactive `Flux<ChatResponse>` of partial responses.

In short, `ChatModel` is the portable contract that hides each vendor's REST API behind a single Java interface. It works directly with `Prompt` and `ChatResponse` objects and has no opinion about message composition, defaults, or cross-cutting concerns. You can use it directly when you want full, explicit control.

This same low-level pattern extends beyond chat. Spring AI defines an equivalent model interface for each modality it supports.  **`ImageModel`** for image generation, plus `EmbeddingModel`, `AudioTranscriptionModel`, and others. They all share the same shape, a request object in and a response object out, so once you understand `ChatModel` the rest of the family feels familiar.

## The recommended, fluent `ChatClient` API

`ChatClient` is the higher-level API designed for everyday use. It wraps a `ChatModel` and adds a fluent builder so you can compose a prompt, invoke the model, and shape the response in a single readable chain:
```java
String answer = chatClient.prompt()      // start building a request
    .user("Tell me about Spring AI")     // add the user's message
    .call()                              // send the request (blocking)
    .content();                          // extract the response text
```

Just as with `ChatModel`, you don't build a `ChatClient` from scratch. The starter's auto-configuration provides a ready-to-use `ChatClient.Builder` bean for you to inject. What the builder adds on top is a place to configure defaults that apply to every call made through that client, such as a default system prompt.

A common pattern is to assemble one configured `ChatClient` as a `@Bean`, so those defaults live in a single place and the rest of your code just injects the finished client:

```java
@Configuration
class ChatConfiguration {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
            .defaultSystem("You are a Spring and AI expert.")
            .build();
    }
}
```

You're not limited to a single client, though. Because the `ChatClient.Builder` is just a bean, you can inject it wherever you need and `build()` a separate `ChatClient` per class or use case, each with its own system prompt and options.

Like the `ChatModel` API, the `ChatClient` supports both blocking and streaming from the same `prompt()` chain. Use `.call()` to wait for the complete answer, or `.stream()` to receive a reactive `Flux<>` that emits tokens as they're produced:

```java
String answer = chatClient.prompt()
    .user(query)
    .call() // blocking
    .content();

Flux<String> stream = chatClient.prompt()
    .user(query)
    .stream() // streaming
    .content();
```

Calling `.content()` is a shortcut that returns the String content of the response. 
When you need more information, ask for the full `ChatResponse` instead:
```java
ChatResponse response = chatClient.prompt()
    .user(query)
    .call()
    .chatResponse();
```

The response handling goes further still. Instead of working with raw text, `.call()` can map the model's output directly onto a Java type via `.entity(...)`:

```java
record SupportAnswer(String summary, List<String> keyPoints, List<String> docLinks) {}

SupportAnswer answer = chatClient.prompt()
    .user("Tell me about Spring AI")
    .call()
    .entity(SupportAnswer.class);
```

This is one of Spring AI's most powerful features: it lets the model return structured, type-safe data your application can use directly, rather than free-form prose you'd have to parse yourself. Behind the scenes Spring AI instructs the model to respond in a matching format and deserializes the result for you. We'll dedicate the **Structured Output** section to it later in the course.


## What's Next

You now have the core mental model: models are REST APIs, `ChatModel` is the portable contract over them, and `ChatClient` is the fluent, batteries-included API you'll reach for in everyday application code. In the next section it's time to put it into practice in a hands-on lab.
