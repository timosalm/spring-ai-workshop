# A2A With Spring AI

You now know what A2A is and how agents use it to work together.

The protocol itself is implemented by the official [A2A Java SDK](https://github.com/a2aproject/a2a-java), which covers the data model, the protocol bindings, and a client for calling remote agents. For the server side there is a deeper integration into Spring AI with the experimental [spring-ai-a2a](https://github.com/spring-ai-community/spring-ai-a2a) project. For the client side there is no dedicated Spring AI integration yet, so you work with the plain Java SDK.

## Exposing Your Agent as an A2A Server

On the server side you add the autoconfiguration module of spring-ai-a2a. It is not managed by the Spring AI BOM, so you set the version yourself.

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-a2a-server-autoconfigure</artifactId>
    <version>0.3.0</version>
</dependency>
```

Then you provide two beans. The first one is the **`AgentCard`**, the description that other agents read before they talk to you.

```java
@Bean
AgentCard agentCard() {
    return new AgentCard.Builder()
            .name("Spring Support Assistant")
            .description("Answers questions about Spring and manages support tickets")
            .url("https://support-agent.example.com/")
            .version("1.0.0")
            .protocolVersion("0.3.0")
            .capabilities(new AgentCapabilities.Builder().streaming(false).build())
            .defaultInputModes(List.of("text/plain"))
            .defaultOutputModes(List.of("application/json"))
            .skills(List.of(new AgentSkill.Builder()
                    .id("spring_questions")
                    .name("Answer Spring questions")
                    .description("Explains Spring features based on the official documentation")
                    .tags(List.of("spring"))
                    .build()))
            .build();
}
```

These are the fields you know from the fundamentals. The agent accepts a question as plain text and answers with JSON, because it returns the same structured `SupportResponse` as your REST endpoint.

The second bean is the **`AgentExecutor`**, the part that does the work. The project ships a `DefaultAgentExecutor` that calls your `ChatClient`, so all you write is a small lambda that takes the text out of the incoming message and returns the answer.

```java
@Bean
AgentExecutor agentExecutor(ChatClient chatClient) {
    return new DefaultAgentExecutor(chatClient, (chat, context) -> {
        var question = DefaultAgentExecutor.extractTextFromMessage(context.getMessage());
        return chat.prompt().user(question).call().content();
    });
}
```

The `ChatClient` is the one you already use, with all its tools and advisors, so A2A is just one more way to reach your agent. The `DefaultAgentExecutor` takes care of the task lifecycle. It moves the task from **submitted** to **working**, adds your answer as an **artifact** with a text part, and marks the task as **completed**.

Text is the only thing it can send back, though. To return the `SupportResponse` as JSON, as the Agent Card promises, you implement the `AgentExecutor` interface yourself and drive the lifecycle with a `TaskUpdater` instance. 

```java
class CustomAgentExecutor implements AgentExecutor {

    ...

    @Override
	void execute(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
        var updater = new TaskUpdater(context, eventQueue);
        if (context.getTask() == null) updater.submit();
        updater.startWork();

        var question = DefaultAgentExecutor.extractTextFromMessage(context.getMessage());
        var response = chatClient.prompt().user(question).call().entity(SupportResponse.class);

        updater.addArtifact(List.of(new DataPart(Map.of(
                "category", response.category().name(),
                "answer", response.answer()))));
        updater.complete();
    }

    @Override
	public void cancel(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
        ...
    }

}
```

The difference is the `DataPart`. The client receives the category and the answer as separate JSON fields and does not have to parse any text.

The autoconfiguration does the rest. It serves the Agent Card at `/.well-known/agent-card.json` and accepts the JSON-RPC requests of the protocol at the root path, so it is common to give each agent its own context path, such as `server.servlet.context-path=/support`.

## Calling a Remote Agent as a Client

For the client side you add the client module of the A2A Java SDK.

```xml
<dependency>
    <groupId>org.a2aproject.sdk</groupId>
    <artifactId>a2a-java-sdk-client</artifactId>
    <version>${a2a-java-sdk.version}</version>
</dependency>
```

The client artifact includes the JSON-RPC transport. For gRPC or REST, add the corresponding transport dependencies `org.a2aproject.sdk:a2a-java-sdk-client-transport-grpc` or `org.a2aproject.sdk:a2a-java-sdk-client-transport-rest`.

Using the client takes three steps. You fetch the Agent Card of the remote agent, build a `Client` for it, and send a message.

```java
AgentCard agentCard = A2A.getAgentCard("https://support-agent.example.com");

Client client = Client.builder(agentCard)
        .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder())
        .addConsumer((event, card) -> {
            if (event instanceof TaskEvent taskEvent) {
                System.out.println("Task " + taskEvent.getTask().status().state());
                System.out.println("Artifacts " + taskEvent.getTask().artifacts());
            } else if (event instanceof MessageEvent messageEvent) {
                System.out.println("Message " + messageEvent.getMessage().parts());
            }
        })
        .build();

client.sendMessage(A2A.toUserMessage("Which Spring Boot versions are still supported?"));
```

`A2A.getAgentCard` reads the card from the well known path of the agent. The client then picks a transport that both sides support, here JSON-RPC. It does not return the answer of `sendMessage` directly. Instead it passes events to the consumers you register. A `TaskEvent` carries a task with its state and artifacts, and a `MessageEvent` carries a direct answer without a task.

To use a remote agent from Spring AI, you put this code into an ordinary `@Tool` method, for example `askBillingAgent`, and register it like any other tool with `.defaultTools(...)`. A `CompletableFuture` that the consumer completes turns the events back into a normal return value for the tool. Delegating work to another agent then looks like any other tool call, and the model decides on its own when to do it. This is the Subagents pattern from the previous sections, with the subagents running as separate services.

Two details matter in such a tool. Use a timeout when you wait for the answer, because the tool call blocks your model call until the remote agent answers. And react to more than the completed task, such as a failed task or a remote agent that needs more input.

## What to Expect

One point should be stated clearly. **spring-ai-a2a is a young, experimental project** developed and maintained by the Spring AI team.

Also check which revision of the A2A protocol you are working with. spring-ai-a2a `0.3.0` builds on the `0.3` line of the A2A Java SDK, so the Agent Card above declares the protocol version `0.3.0`. The client above uses the newer `1.x` line of the SDK, which speaks the `1.0` revision of the protocol and offers a separate compatibility client for agents that still speak `0.3`. 

And remember the advice from the tool calling section. The answer of a remote agent is input that you do not control, so treat it with the same care as the result of any other tool.
