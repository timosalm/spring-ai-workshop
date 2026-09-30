# Tool Calling

The foundations section also explained what tool calling is and why it matters. A model can reason but it cannot act, so you give it tools. The model never runs anything itself. It only proposes a call, your application executes the matching code, and the result goes back so the model can continue. This section shows how Spring AI lets you expose ordinary Java methods as tools, and how it runs the request, execute, and respond loop for you.

## Defining a Tool

The most natural way to define a tool in Spring AI is to write an ordinary method and mark it with `@Tool`. Here are two tools for our support assistant, one that retrieves information and one that takes an action.

```java
class SupportTools {

    @Tool(description = "Get the current status of a customer's order by its ID")
    String getOrderStatus(@ToolParam(description = "The order ID, e.g. A-1234") String orderId) {
        return orderRepository.findStatus(orderId);
    }

    @Tool(description = "Open a support ticket for a customer and return the ticket number")
    String openTicket(@ToolParam(description = "Short summary of the problem") String summary) {
        return ticketService.create(summary).number();
    }
}
```

There is nothing special about these methods, because they are regular Java that does whatever you want. What the annotations add is the **description the model reads**. As you saw in the foundations section, the description is the contract. The model decides whether and how to call a tool based entirely on its name, its description, and its parameter descriptions, so treat these texts as carefully as the prompt itself.

Two annotations do the work.

- **`@Tool`** marks the method as a tool. Its `description` tells the model what the tool does. The name of the tool defaults to the method name but can be overridden, and it must be unique within the set you provide.
- **`@ToolParam`** describes a parameter so the model knows what to put there. It can also mark a parameter optional with `required = false`, because parameters are required by default.

From these, Spring AI generates the **JSON schema** of the tool inputs that the model needs, so you never write that by hand. You describe your method, and the framework derives the contract the model sees.

## Registering Tools on the ChatClient

Tools attach to a `ChatClient` call the same fluent way everything else does. Hand the client an instance of your tools class with `.tools(...)`, and prompt as usual.

```java
String answer = chatClient.prompt()
    .user("What's the status of order A-1234, and open a ticket if it's delayed?")
    .tools(new SupportTools())
    .call()
    .content();
```

If a set of tools should be available on every call a client makes, register them as defaults on the builder instead, exactly like a default system prompt.

```java
ChatClient chatClient = builder
    .defaultTools(new SupportTools())
    .build();
```

The `.tools()` method is flexible about what it accepts, so you can pass `@Tool` annotated objects like the one above, ready-made `ToolCallback` instances, or `ToolCallbackProvider`s. However you supply them, the model sees the same thing, a list of named and described tools with a schema that it may choose to call.

## The Framework Runs the Loop for You

Look again at that example. The request of the user might need *two* tool calls, first to check the status and then to open a ticket if needed, and after each one the model needs the result before it can decide what to do next. This is the tool calling loop from the foundations section. Yet your code is a single `.call()`, so who runs that back and forth?

This is where the **advisor** mechanism returns. When you attach tools to a `ChatClient`, Spring AI registers a **`ToolCallingAdvisor`** into the chain automatically. That advisor manages the entire lifecycle. It sends the request, sees when the model asks for a tool, executes the matching method, feeds the result back, and repeats until the model produces a final answer instead of another tool request.

Because this is just an advisor, it composes with everything else on the client. Your support assistant can carry a RAG `QuestionAnswerAdvisor` *and* tools at the same time, so it grounds answers in documentation while it is also able to act, all from the same prompt chain. And because it is registered for you, you rarely think about it. You can switch the automatic registration off globally with `spring.ai.chat.client.tool-calling.enabled=false`, or per call, on the rare occasion when you want to run the loop yourself.

## Passing Data the Model Should Not See With `ToolContext`

The foundations section explained why some tool inputs must never come from the model. The id of the current user, the tenant they belong to, or an auth token are values your application controls, not values you would want the model to guess or be able to influence. For this, Spring AI provides **`ToolContext`**. You attach extra data to the call, and your tool reads it from the context instead of receiving it as a parameter that the model supplies.

```java
class SupportTools {

    @Tool(description = "Get the current status of a customer's order by its ID")
    String getOrderStatus(String orderId, ToolContext toolContext) {
        String tenantId = (String) toolContext.getContext().get("tenantId");
        return orderRepository.findStatus(orderId, tenantId);
    }
}
```

```java
String answer = chatClient.prompt()
    .user("What's the status of order A-1234?")
    .tools(new SupportTools())
    .toolContext(Map.of("tenantId", "acme"))
    .call()
    .content();
```

The key guarantee is that **`ToolContext` data is never sent to the model**.

## Building Tools Programmatically

Annotated methods are the common case, but a tool is really just an implementation of the **`ToolCallback`** interface. Each `ToolCallback` pairs a **`ToolDefinition`**, which holds the name, the description, and the input schema the model sees, with the code to run when the model calls it. `@Tool` simply produces one of these for you. Sometimes you want to build one directly, for example when the logic already lives in a bean, when you cannot annotate the method because it is third party code, or when you assemble tools at runtime. Spring AI gives you two ways to do that.

### From an existing method with `MethodToolCallback`

`MethodToolCallback` turns any method into a tool without the `@Tool` annotation. You describe the tool in a `ToolDefinition`, and point the callback at the method and at the object to invoke it on. The input schema is derived from the parameters of the method for you.

```java
Method createTicket = ReflectionUtils.findMethod(SupportTicketService.class, "createTicket");

ToolCallback createTicketTool = MethodToolCallback.builder()
    .toolDefinition(ToolDefinition.builder(createTicket)
        .description("Create a support ticket for a Spring or Tanzu Spring question")
        .build())
    .toolMethod(createTicket)
    .toolObject(supportTicketService)
    .build();
```

This is exactly what `@Tool` builds behind the scenes. The `ToolDefinition` carries what the model reads, and `toolMethod` together with `toolObject` tell Spring AI which method to call and on which instance.

### From a plain function with `FunctionToolCallback`

When the logic is a standalone function rather than a method on a bean, use `FunctionToolCallback`. A function takes a single input object and returns a result, so you first define that input type and describe its fields. The `@JsonPropertyDescription` annotations play the same role for a function input that `@ToolParam` plays for method parameters, because they tell the model what each field is for.

```java
record CreateTicketRequest(
        @JsonPropertyDescription("Brief summary of the issue") String summary,
        @JsonPropertyDescription("The category of the issue") SupportCategory category,
        @JsonPropertyDescription("The priority of the support ticket") SupportTicket.Priority priority) {
}
```

The function itself maps that input to a result. Here it saves a new ticket and returns it.

```java
Function<CreateTicketRequest, SupportTicket> createTicket = request ->
        ticketRepository.save(new SupportTicket(request.summary(), request.category(), request.priority()));

ToolCallback createTicketTool = FunctionToolCallback
    .builder("createTicket", createTicket)
    .description("Create a support ticket for a Spring or Tanzu Spring question")
    .inputType(CreateTicketRequest.class)
    .build();
```

The `inputType(CreateTicketRequest.class)` call is what lets Spring AI derive the JSON schema for the input, just as it reads the parameters of a method for `@Tool`. There are `builder` overloads for a `BiFunction` when you also need the `ToolContext`, and for `Supplier` and `Consumer` when the tool takes or returns nothing.

Both approaches produce the same kind of `ToolCallback` that you get from an annotated method, and you pass either one to `.tools(...)` in the same way. The model cannot tell which style you used.

## When You Need More Control

The defaults handle most applications, but a few knobs are worth knowing about for when you need them.

- **Returning a result directly** By default every tool result goes back to the model for a final answer in natural language. Sometimes you want the raw tool output returned straight to your caller instead, which skips that extra round trip to the model. Setting `returnDirect = true` on a `@Tool` does exactly that.
- **Handling failures** When a tool throws, Spring AI wraps it in a `ToolExecutionException`. By default the error *message* is sent back to the model so it can recover or explain the problem gracefully. You can instead let failures propagate as exceptions with `spring.ai.tools.throw-exception-on-error=true` when you would rather handle them in your own code, or implement a custom `ToolExecutionExceptionProcessor`.
- **Controlling the loop yourself** The advisor that is registered for you is one of several execution strategies. Both `ChatClient` and `ChatModel` support user controlled tool execution, where you detect the tool calls in the `ChatResponse` and execute them yourself.

Keep in mind the safety guidance from the foundations section. The model decides which tools to call and with which arguments, and user input can steer it, so treat a tool call as untrusted input to your own code. Validate the arguments, scope what each tool is permitted to do, and use `ToolContext` rather than model parameters for anything security sensitive.
