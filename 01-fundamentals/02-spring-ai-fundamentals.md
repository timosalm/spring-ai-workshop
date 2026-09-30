# Spring AI Fundamentals: ChatModel and ChatClient

For all their sophistication, the large language models you work with are reached the same way as any other cloud service, over HTTP. You send a JSON request that contains your prompt and some options to the endpoint of a provider, and you get back a JSON response with the generated text, the token counts, and some metadata. Anthropic, Mistral, OpenAI, and Ollama all follow this same request and response model, with their own provider specific details.

This is good news, because integrating with REST APIs is exactly what Spring has always been good at. The ecosystem already gives you HTTP clients, JSON serialization, connection pooling, retries, and externalized configuration. In principle you could call a model yourself with a `RestClient`, build the request body by hand, parse the response, and manage your API key.

You *could*, but you would quickly find yourself reimplementing a lot of plumbing.

- Building and parsing the specific JSON schema of each provider by hand
- Rewriting that code every time you switch or add a provider
- Wiring up streaming, retries, error handling, and observability yourself
- Mapping raw responses into domain objects, keeping conversation history, and so on

This is the gap Spring AI fills. It builds on the same HTTP foundations but gives you a **purpose built abstraction for talking to models**, so you describe *what* you want to ask instead of *how* to format the HTTP call. Switching providers becomes a configuration change rather than a rewrite.

If you have used Spring Data, this will feel familiar. Spring Data gives you one consistent programming model over different data stores, and Spring AI gives you one consistent programming model over different AI providers and models, while you can still reach provider specific options when you need them.

Spring AI delivers that programming model through two abstractions that complement each other. The low level `ChatModel` offers a direct API that handles the HTTP call, serializes your request, and maps the raw JSON of the provider back into Java objects. The higher level `ChatClient` is built on top of it for easier everyday use and for more advanced capabilities.

## Dependencies and Configuration

Before you write any code you need the right starter on your classpath. Spring AI ships a dedicated Spring Boot starter per provider, so you add the one that matches the model you want to talk to. For OpenAI this is the following.

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

Also import the **Spring AI BOM** so every Spring AI artifact resolves to one consistent and compatible version, which also lets you leave out the explicit versions on the starters above.

```xml
<!-- Maven: pom.xml -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>2.0.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

```groovy
// Gradle: build.gradle
dependencies {
    implementation platform('org.springframework.ai:spring-ai-bom:2.0.1')
}
```

The starter pulls in the provider implementation together with the Spring Boot autoconfiguration, which creates the related beans for you.

The rest is configuration, and it lives in `application.properties` or `application.yml` just like any other Spring Boot setting. Each provider has its own property namespace such as `spring.ai.openai.*` or `spring.ai.ollama.*`, but they all expose similar kinds of settings. Here is the OpenAI example.

```properties
# Authentication
spring.ai.openai.api-key=${OPENAI_API_KEY}
# The endpoint, point it at a mock, a gateway, or a compatible API when needed
spring.ai.openai.base-url=https://api.openai.com
# The model to use, for example gpt-5.5 or gpt-5.4-mini
spring.ai.openai.chat.model=gpt-5.6-sol
# Request parameters such as sampling randomness. Lower is more deterministic, higher more creative
spring.ai.openai.chat.temperature=0.7
spring.ai.openai.chat.reasoning-effort=none
```

Because the model and the other options live outside your code, you can tune the behavior or switch models without touching a single class.

### Mixing providers in one application

Each provider has its own namespace and its own starter, so you can use several at once, for example one provider for chat and another for image generation. You add both starters and configure both. When more than one starter on the classpath can serve the same model type, the `spring.ai.model.*` properties decide which one handles it.

```properties
spring.ai.model.chat=ollama
spring.ai.model.image=openai
```

You can also vary providers across environments with Spring profiles, for example a local model in `application-dev.properties` and a hosted one in `application-prod.properties`. And when you need full control, you can switch the autoconfiguration off and wire the model beans yourself.

## The low level ChatModel API

`ChatModel` is the foundational interface that every chat provider implements, such as `OpenAiChatModel`, `AnthropicChatModel`, and `OllamaChatModel`. It is deliberately minimal. You hand it a `Prompt` and it returns a `ChatResponse`.

```java
ChatResponse response = chatModel.call(new Prompt("Tell me about Spring AI"));
String text = response.getResult().getOutput().getText();
```

Thanks to the autoconfiguration the starter provides, this `ChatModel` is already a Spring bean, so you can inject it into any component and call it as shown above without constructing it yourself.

For convenience, `call()` is also overloaded to accept a plain `String`, which Spring AI wraps in a `Prompt` for you. This string based overload also simplifies the other end. Instead of a `ChatResponse` that you have to unwrap, it hands you the response content straight back as a `String`, so the two lines above collapse into a single call.

```java
String text = chatModel.call("Tell me about Spring AI");
```

### Prompts, messages, and roles

That shortcut hides what a `Prompt` really is, though. Under the hood a `Prompt` holds an ordered list of `Message` objects, and each message carries one of the three roles the underlying APIs define, "system", "user", or "assistant". These roles structure the conversation and shape how the model reads each message.

The "system" role sets the overall behavior and tone of the model, usually at the start of a conversation, and Spring AI represents it with `SystemMessage`. The "user" role carries the human side of the conversation, so questions, instructions, and input, and it maps to `UserMessage`. The "assistant" role represents the answers of the model, and when you include one in a prompt it provides an earlier turn of the conversation as context. Spring AI represents this role with `AssistantMessage`.

Since `Prompt` also accepts a list of `Message` objects, you can compose them explicitly.

```java
Prompt prompt = new Prompt(List.of(
    new SystemMessage("You are a support agent for the Spring framework. Answer clearly and always include a link to the relevant official docs when one exists, never inventing URLs."),
    new UserMessage("Tell me about Spring AI")));
ChatResponse response = chatModel.call(prompt);
```

### Images and other media in a prompt

A `UserMessage` does not have to be text only. Many modern models are **multimodal**, which means they can take in more than one kind of content at once, most commonly text together with images, and some also accept audio or video. For a support assistant this is useful right away, because a user can attach a screenshot of an error dialog and ask what went wrong instead of trying to put it into words.

Spring AI supports this with the `media` field of `UserMessage`. The text stays in the usual content, and images, audio, or other attachments go alongside it as one or more **`Media`** objects. Each `Media` pairs the raw content, either a `Resource` or a `URI`, with a `MimeType` that tells the provider what kind of data it is. Media is only allowed on user messages, because it represents human input, so system and assistant messages stay text only.

```java
var screenshot = new ClassPathResource("/error-dialog.png");

var userMessage = UserMessage.builder()
    .text("What does this error mean, and how do I fix it?")
    .media(new Media(MimeTypeUtils.IMAGE_PNG, screenshot))
    .build();

ChatResponse response = chatModel.call(new Prompt(userMessage));
```

Which modalities actually work depends on the provider and on the specific model. Image understanding is supported most widely and is offered on the vision capable models from providers such as OpenAI, Anthropic, Google Gemini, Amazon Bedrock, Mistral, and Ollama, while audio and video are available on a smaller set.

### Reusable prompts with `PromptTemplate`

In practice, prompts are rarely fixed strings, because they depend on runtime data. The `PromptTemplate` of Spring AI lets you write a message with `{placeholder}` variables and fill them in at call time, which keeps your prompts reusable instead of built by string concatenation.

```java
PromptTemplate promptTemplate = PromptTemplate.builder()
        .template("Tell me about {topic}")
        .variables(Map.of("topic", "Spring AI"))
        .build();
ChatResponse response = chatModel.call(promptTemplate.create());
```

If the default placeholder syntax conflicts with your data, you can [configure a custom template renderer](https://docs.spring.io/spring-ai/reference/api/prompt.html#_using_a_custom_template_renderer) that uses a different delimiter.

### Changing the model and other settings with `ChatOptions`

A `Prompt` is more than its messages, though. It also carries a set of **`ChatOptions`** such as the model name and `maxTokens`. You can define these as defaults in configuration and then override them on an individual call, so you can change the model for one request without changing anything globally.

**Note.** Since Spring AI 2.0 the low level `ChatModel` API requires provider specific options. Use the builder of the provider, such as `OpenAiChatOptions.builder()`, instead of the portable `ChatOptions.builder()`.

```java
ChatResponse response = chatModel.call(new Prompt(
    "Tell me about Spring AI",
    OpenAiChatOptions.builder().model("gpt-5.4-mini").build()));
```

### What comes back in the `ChatResponse`

The `ChatResponse` that comes back wraps one or more **`Generation`** objects. A `Generation` is a single candidate completion, so the message of the assistant together with metadata such as its finish reason. Most requests return exactly one, which is why the `getResult()` shortcut above is so common, but you can ask a model to produce several alternatives.

Beyond the generated text, the `ChatResponse` also exposes metadata about the call through `getMetadata()`. This includes details such as the model that served the request and, more importantly, `getUsage()`, which gives you the **token counts** for the prompt and for the completion. Providers bill per token, so these counts are the foundation for cost monitoring and budgeting.

### Blocking and streaming calls

The `call()` method shown so far is blocking, because it waits for the whole completion before it returns. Models generate text token by token, so you can also consume the response as a stream and display each piece the moment it is produced. This is what powers the typewriter effect in chatbots, where words appear progressively, and it improves the perceived responsiveness of longer answers a lot. For this, Spring AI provides the companion `StreamingChatModel` interface, whose `stream()` method returns a reactive `Flux<ChatResponse>` of partial responses.

### The same pattern beyond chat

This low level pattern is not limited to chat. Spring AI defines an equivalent model interface for every modality it supports, such as **`ImageModel`** for image generation, plus `EmbeddingModel`, `AudioTranscriptionModel`, and others. They all share the same shape, a request object in and a response object out, so once you understand `ChatModel` the rest of the family feels familiar.

So `ChatModel` is the portable contract that hides the REST API of each vendor behind a single Java interface. It works directly with `Prompt` and `ChatResponse` objects and has no opinion about message composition, defaults, or cross-cutting concerns. Use it when you want full and explicit control.

## The recommended, fluent ChatClient API

`ChatClient` is the higher level API designed for everyday use. It wraps a `ChatModel` and adds a fluent builder, so you compose a prompt, invoke the model, and shape the response in a single readable chain.

```java
String answer = chatClient.prompt()      // start building a request
    .user("Tell me about Spring AI")     // add the user's message
    .call()                              // send the request (blocking)
    .content();                          // extract the response text
```

Just as with `ChatModel`, you do not build a `ChatClient` from scratch. The autoconfiguration of the starter provides a ready to use `ChatClient.Builder` bean for you to inject. What the builder adds on top is a place to configure defaults that apply to every call made through that client, such as a default system prompt.

A common pattern is to assemble one configured `ChatClient` as a `@Bean`, so those defaults live in a single place and the rest of your code only injects the finished client.

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

You are not limited to a single client, though. The `ChatClient.Builder` is just a bean, so you can inject it wherever you need it and `build()` a separate `ChatClient` per class or per use case, each with its own system prompt and options.

Like the `ChatModel` API, the `ChatClient` supports both blocking and streaming from the same `prompt()` chain. Use `.call()` to wait for the complete answer, or `.stream()` to receive a reactive `Flux<>` that emits tokens as they are produced.

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

Calling `.content()` is a shortcut that returns the String content of the response. When you need more information, ask for the full `ChatResponse` instead.

```java
ChatResponse response = chatClient.prompt()
    .user(query)
    .call()
    .chatResponse();
```

The multimodal input from the previous section works here as well. The user step of the chain has its own builder, so you attach an image next to the text without constructing a `UserMessage` yourself.

```java
String answer = chatClient.prompt()
    .user(u -> u
        .text("What does this error mean, and how do I fix it?")
        .media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/error-dialog.png")))
    .call()
    .content();
```

## From Text to Java Objects with Structured Output

So far every response has come back as plain text. That is fine for a chatbot, but in an enterprise application you usually want to *do* something with the answer of the model, such as store it, validate it, render it in a UI, or pass it to other business logic. Free form text is a poor fit for that, which is why structured output is one of the most important features of Spring AI. You will probably use it in almost every real application.

### A short excursus on prompt engineering

How would you get structured data out of a model without framework support? The only way to influence a model is through the prompt, so the answer lies in **prompt engineering**. It is the practice of phrasing and structuring prompts to steer the model towards the output you want.

One of the most effective techniques is **few-shot prompting**. Instead of only describing the output you want, you show the model a few examples of it. Models are excellent at continuing patterns, so a few sample question and answer pairs in the right format make the model follow that format for the real question. You can use this to make a model respond with JSON.

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

This works, and few-shot prompting stays a valuable technique for steering model behavior far beyond formatting. For structured data, though, it leaves the tedious parts to you. You write the format instructions by hand, you have to keep the examples in sync with your Java types, and you still get back a raw `String` that you must deserialize yourself, with no guarantee that the model followed the format.

### Letting Spring AI handle it with `.entity(...)`

This is exactly the boilerplate that the structured output support of Spring AI takes away. Instead of returning raw text, `.call()` can map the output of the model directly onto a Java type through `.entity(...)`.

```java
enum SupportCategory { TECHNICAL, BILLING, SECURITY, GENERAL }

record SupportResponse(SupportCategory category, String answer) {}

SupportResponse answer = chatClient.prompt()
    .user("Tell me about Spring AI")
    .call()
    .entity(SupportResponse.class);
```

Behind the scenes Spring AI does the same thing you just did by hand. It appends format instructions to your prompt that tell the model to respond as JSON matching the structure of your type, except that these instructions are generated from the Java type itself, and then it deserializes the result into the object for you. You get structured, type safe data that your application can use directly, with no manual parsing and no brittle string handling. The boundary between AI code and the rest of your Spring application disappears, because the model becomes just another collaborator that returns domain objects.

The `.entity(...)` method is not limited to flat records. It also handles nested types and collections such as a `List` of your domain objects.

### Native structured output

The approach above is **prompt based**. Spring AI appends the format instructions to your prompt and trusts the model to follow them. It works with every model, but it is still only a request, so a model can sometimes return text that does not parse.

Many providers now offer a stronger guarantee called **native structured output**. Instead of asking in the prompt, Spring AI sends the JSON schema of your type to the structured output API of the provider, and the provider constrains the model so the response is always valid JSON that matches the schema. This is more reliable, and it keeps the format instructions out of the prompt. OpenAI, Anthropic, Google Gemini, Mistral, and Ollama all support it on their newer models, each through its own API.

Because that support varies by provider and by model, it is **not enabled by default**. There are also provider specific limitations to keep in mind. The native mode of OpenAI, for example, does not allow a top level array, so return a record that wraps the `List` instead of a `List` directly. And not every Ollama model honors the schema reliably.

You enable it in one of two ways.

The first way goes through the `.entity(...)` spec.

```java
SupportResponse answer = chatClient.prompt()
    .user("Tell me about Spring AI")
    .call()
    .entity(SupportResponse.class, spec -> spec
        .useProviderStructuredOutput());
```

The second way uses directly the Advisors API, which the structured output support of Spring AI is built on and which a later section of this module covers in detail. You can configure it as a default on the `ChatClient` bean, or attach it to a single request.

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
