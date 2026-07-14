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

You can also vary providers across environments with Spring profiles, for example a local model in `application-dev.properties` and a hosted one in `application-prod.properties`. And when you need full control, you can switch off the auto-configuration and wire the model beans yourself.

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
    new SystemMessage("You are a support agent for the Spring framework. Answer clearly and always include a link to the relevant official docs when one exists, never inventing URLs."),
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

A `Prompt` is more than its messages, though. It also carries a set of **`ChatOptions`** such as the model name, and `maxTokens`. You can define these as defaults in configuration and then override them on an individual call, so, for example, you can change the model for one request without changing anything globally.

**Note:** Since Spring AI 2.0, the low-level ChatModel API requires provider-specific options. Use the provider’s builder such as OpenAiChatOptions.builder() instead of the portable ChatOptions.builder().

```java
ChatResponse response = chatModel.call(new Prompt(
    "Tell me about Spring AI",
    ChatOptiOpenAiChatOptions.builder().model("gpt-5.4-mini").build()));
```

The `ChatResponse` that comes back wraps one or more **`Generation`** objects. A `Generation` is a single candidate completion, the assistant's message together with metadata such as its finish reason. Most requests return exactly one, which is why the `getResult()` shortcut above is so common, but a model can be asked to produce several alternatives.

Beyond the generated text, the `ChatResponse` also exposes metadata about the call via `getMetadata()`. This includes details such as the model that served the request and, importantly, `getUsage()`, which provides the **token counts** for both the prompt and the completion. Tracking usage matters because providers bill per token, so surfacing these counts is the foundation for cost monitoring and budgeting.

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
            .defaultSystem("You are a support agent for the Spring framework. Answer clearly and always include a link to the relevant official docs when one exists, never inventing URLs.")
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

## Structured Output: From Text to Java Objects

So far, every response has come back as plain text. That's fine for a chatbot, but in an enterprise application you usually want to *do* something with the model's answer: store it, validate it, render it in a UI, or pass it to other business logic. Free-form text is a poor fit for that. This is why structured output is one of Spring AI's most important features. You will probably use it in almost every real application.

### A short excursus: Prompt Engineering

How would you get structured data out of a model without framework support? Since the only way to influence a model is through the prompt, the answer lies in **Prompt Engineering**. It's the practice of phrasing and structuring prompts to steer the model toward the output you want.

One of the most effective techniques is **Few-Shot Prompting**. Instead of only describing the desired output, you show the model a few examples of it. Models are excellent at continuing patterns, so given a few sample question/answer pairs in the right format, they will follow that format for the real question. You can use this to instruct the model into responding with JSON:

```java
String json = chatClient.prompt()
    .system("""
        You are a Spring support classifier.
        Reply only with JSON in this form:
        {"category":"...","answer":"..."}
        The category must be one of: TECHNICAL, BILLING, SECURITY, GENERAL.
        Examples:
        - "Why was I billed twice?"     -> {"category":"BILLING","answer":"..."}
        - "How do I rotate my API key?" -> {"category":"SECURITY","answer":"..."}
        """)
    .user("Tell me about Spring AI")
    .call()
    .content();
```

This works, and few-shot prompting remains a valuable technique for steering model behavior far beyond formatting. But for structured data it leaves the tedious parts to you: you hand-craft the format instructions, you have to keep the examples in sync with your Java types, and you still get back a raw `String` you must deserialize yourself, with no guarantee the model didn't deviate from the format.

### Letting Spring AI handle it with `.entity(...)`

This is exactly the boilerplate that Spring AI's structured output support abstracts away. Instead of returning raw text, `.call()` can map the model's output directly onto a Java type via `.entity(...)`:

```java
enum SupportCategory { TECHNICAL, BILLING, SECURITY, GENERAL }

record SupportResponse(SupportCategory category, String answer) {}

SupportResponse answer = chatClient.prompt()
    .user("Tell me about Spring AI")
    .call()
    .entity(SupportResponse.class);
```

Behind the scenes, Spring AI does the same thing you just did by hand: it appends format instructions to your prompt that tell the model to respond as JSON matching your type's structure, except that these instructions are generated from the Java type itself, and then deserializes the result into the object for you. You get structured, type-safe data your application can use directly, with no manual parsing and no brittle string handling. The boundary between "AI code" and the rest of your Spring application disappears: the model becomes just another collaborator that returns domain objects.

The `.entity(...)` method isn't limited to flat records. It handles nested types, and collections such as a `List` of your domain objects, too.

### Native structured output

The approach above is **prompt-based**. Spring AI appends the format instructions to your prompt and trusts the model to follow them. It works with every model, but it is still only a request, so a model can sometimes return text that does not parse.

Many providers now offer a stronger guarantee called **native structured output**. Instead of asking in the prompt, Spring AI sends your type's JSON schema to the provider's own structured-output API, and the provider constrains the model so the response is always valid JSON that matches the schema. This is more reliable, and it keeps the format instructions out of the prompt. OpenAI, Anthropic, Google Gemini, Mistral, and Ollama all support it on their newer models, each through its own API.

Because that support varies by provider and model, it is **not enabled by default**. There are also provider-specific limitations to keep in mind. OpenAI's native mode, for example, does not allow a top-level array, so return a record that wraps the `List` instead of a `List` directly. And not every Ollama model honors the schema reliably.

You enable it in one of two ways.

The first way is through the `.entity(...)` spec. Add `validateSchema()`, which is not supported for streaming, if you also want Spring AI to check the result against the schema and retry when a model still returns something malformed.

```java
SupportResponse answer = chatClient.prompt()
    .user("Tell me about Spring AI")
    .call()
    .entity(SupportResponse.class, spec -> spec
        .useProviderStructuredOutput()
        .validateSchema());
```

The structured output support of Spring AI is based on the Advisors API, you'll learn about in the next section, which brings us to the second option to enable native structure output in this example, via the `ChatClient` bean:

The second way uses the Advisors API, the structured output support of Spring AI is based on and which you will learn more about in the next section. You can either, like here, configure it as a default on the `ChatClient` bean, or on every request. 

```java
ChatClient chatClient(ChatClient.Builder builder) {
    return builder
        .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
        .build();
}
```

```java
SupportResponse answer = chatClient.prompt()
    .user("Tell me about Spring AI")
    .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
    .call()
    .entity(SupportResponse.class);
```

## Multimodality

Everything so far has treated a prompt as text. Many modern models are **multimodal**, which means that they can take in more than one kind of content at once, most commonly text together with images, and some also accept audio or video. For a support assistant this is immediately useful, since a user can attach a screenshot of an error dialog and ask what went wrong instead of trying to put it into words.

Spring AI supports this by adding a `media` field to `UserMessage`. The text stays in the usual content, and any images, audio, or other attachments go alongside it as one or more **`Media`** objects. Each `Media` pairs the raw content, either a `Resource` or a `URI`, with a `MimeType` that tells the provider what kind of data it is. Media is only allowed on user messages, since it represents human input, system and assistant messages stay text-only.

With the low-level `ChatModel` API you build the `UserMessage` yourself and hand it to a `Prompt`:
```java
var screenshot = new ClassPathResource("/error-dialog.png");

var userMessage = UserMessage.builder()
    .text("What does this error mean, and how do I fix it?")
    .media(new Media(MimeTypeUtils.IMAGE_PNG, screenshot))
    .build();

ChatResponse response = chatModel.call(new Prompt(userMessage));
```

The fluent `ChatClient` API exposes the same capability through its user-message builder, so you attach media inline without constructing the message by hand:
```java
String answer = chatClient.prompt()
    .user(u -> u
        .text("What does this error mean, and how do I fix it?")
        .media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/error-dialog.png")))
    .call()
    .content();
```

Which modalities actually work depends on the provider and the specific model. Image understanding is the most widely supported, offered on the vision-capable models from providers such as OpenAI, Anthropic, Google Gemini, Amazon Bedrock, Mistral, and Ollama, with audio and video available on a smaller subset.