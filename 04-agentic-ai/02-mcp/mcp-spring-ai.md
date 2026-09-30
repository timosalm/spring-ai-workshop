# MCP With Spring AI

You now know what MCP is and how it works as a protocol.

Spring AI supports both sides of it, so your application can be a client, a server, or both at once. Underneath it builds on the official MCP Java SDK, and Spring Boot starters together with autoconfiguration hide most of the low level details.

It is worth knowing where that SDK comes from. The Spring AI team is one of its main contributors and develops it together with Anthropic, so the official Java implementation of the protocol and the Spring integration on top of it are built by the same people. For you this means Java support arrives early rather than late, and the Spring layer fits the SDK closely instead of working around it.

One thing to keep in mind while you read this section is which revision of the specification you are working with. Spring AI speaks the `2025-06-18` revision, so everything below describes that behavior. The new `2026-07-28` revision has been released, but neither the MCP Java SDK nor Spring AI supports it yet, and the same is true for most other implementations across languages.

## Consuming Another Server as a Client

To call tools that live in someone else's server you add the client starter.

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```

This one starter covers stdio, Streamable HTTP, and the older SSE transport. If you prefer a reactive stack there is a `spring-ai-starter-mcp-client-webflux` variant.

You then declare the servers you want to reach. Each connection gets a name, and that name is yours to choose.

```properties
spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:8090
```

You can list as many connections as you like, and you can mix transports. A local server that should be launched as a child process is configured with a command and its arguments instead of a URL, and you can pass environment variables to it.

```properties
spring.ai.mcp.client.stdio.connections.server2.command=npx
spring.ai.mcp.client.stdio.connections.server2.args=-y,@modelcontextprotocol/server-filesystem,/tmp
```

If you already have a server list in the JSON format that Claude Desktop uses, you can point Spring AI at that file with `spring.ai.mcp.client.stdio.servers-configuration` instead of repeating everything in properties. One small thing to remember is that starting a command on Windows sometimes needs a `cmd.exe /c` wrapper, which the reference documentation explains in more detail.

The autoconfiguration now does the work. It creates one client per named connection, performs the handshake, discovers the remote tools, and gathers all of them into a single **`ToolCallbackProvider`** bean. You only have to pass that bean to your `ChatClient`.

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder, ToolCallbackProvider tools) {
    return builder
            .defaultTools(tools)
            .build();
}
```

From here on the remote tools behave exactly like the local ones you wrote with `@Tool`. The model sees them in the same list, the same `ToolCallingAdvisor` runs the loop, and your prompting code does not change at all. Local and remote tools can be combined freely, because the default tools on the bean and the tools passed per call are merged.

A handful of common settings apply to all connections at once.

```properties
spring.ai.mcp.client.name=support-assistant
spring.ai.mcp.client.version=1.0.0
spring.ai.mcp.client.request-timeout=30s
spring.ai.mcp.client.type=SYNC # The default
```

The `name` and `version` are what your application reports to every server during the handshake. The `request-timeout` defaults to twenty seconds, which is often too short for a tool that does real work, so it is worth reviewing.

The `type` chooses between the blocking and the reactive programming model, and it applies to all clients together, because mixing synchronous and asynchronous clients in one application is not supported. The default `SYNC` type suits an ordinary blocking application, while `ASYNC` suits a reactive application with non-blocking operations. The type also decides which handler methods get registered, because a `SYNC` client registers only the synchronous MCP annotated methods and ignores the asynchronous ones, and an `ASYNC` client does the opposite. A handler that does not match the client type is skipped. Setting `spring.ai.mcp.client.enabled` to `false` turns the whole integration off, which is useful for tests.

Two details about tool names are useful to know. Spring AI makes sure the names it passes to the model stay unique, so a tool keeps its original name as long as no other tool uses the same one, and it only gets a generated prefix when a second server offers a tool with that name. Characters that are not allowed in a tool name are replaced as well. You can take over this naming completely with your own `McpToolNamePrefixGenerator` bean. You can also decide which remote tools reach the model at all by contributing an `McpToolFilter` bean, which is a good idea when a server exposes far more tools than your use case needs.

Beyond tools, the client side can answer the requests a server may send back to it, and Spring AI exposes these through annotations on any bean.

```java
@Component
class McpClientHandlers {

    @McpSampling(clients = "server1")
    CreateMessageResult handleSampling(CreateMessageRequest request) {
        var answer = chatClient.prompt().user(request.toString()).call().content();
        return CreateMessageResult.builder(Role.ASSISTANT, answer, "gpt-5").build();
    }

    @McpElicitation(clients = "server1")
    ElicitResult handleElicitation(ElicitRequest request) {
        return ElicitResult.builder(ElicitResult.Action.ACCEPT).content(Map.of("message", request.message())).build();
    }
}
```

There are matching annotations for the other callbacks, namely `@McpLogging` for log messages from a server, `@McpProgress` for progress updates during a long running call, and `@McpToolListChanged`, `@McpResourceListChanged` and `@McpPromptListChanged` for the notifications a server sends when its offering changes. The `clients` attribute limits a handler to a named connection, and each handler can also return a `Mono` if you use the asynchronous client type.

Spring AI also lets you configure many aspects of the client behavior, from request timeouts to event handling and message processing, through `McpClientCustomizer` beans.

## Exposing Your Own Server

To offer your own capabilities you add a server starter. Which one you pick decides the transport.

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

The plain `spring-ai-starter-mcp-server` starter gives you a stdio server that a host launches locally, and `spring-ai-starter-mcp-server-webflux` is the reactive counterpart of the WebMVC one. Notice that a server needs no model provider starter at all, because it only exposes capabilities and never calls a model itself.

A little configuration names your server and selects the protocol mode.

```properties
spring.ai.mcp.server.name=spring-releases
spring.ai.mcp.server.version=1.0.0
spring.ai.mcp.server.protocol=STREAMABLE
```

The `protocol` property is worth understanding. `STREAMABLE` is the normal choice for a remote server. `SSE` is the older mode and is deprecated. `STATELESS` keeps no session state between requests, which makes the server easier to scale behind a load balancer, and it is the direction the protocol itself is heading.

Just like the client, the server has a `type` that picks the programming model, which you set with `spring.ai.mcp.server.type=SYNC` or `ASYNC`. The default `SYNC` server is built on `McpSyncServer` and suits straightforward request and response work, while the `ASYNC` server is built on `McpAsyncServer` and is optimized for non-blocking operations with Project Reactor. A `SYNC` server registers only the synchronous MCP annotated methods and an `ASYNC` server only the asynchronous ones, and any method that does not match the server type is quietly ignored.

Writing the capabilities is annotation driven and will feel familiar after the tool calling section. You mark a method with `@McpTool`, describe the parameters with `@McpToolParam`, and Spring AI generates the JSON schema and registers the tool with the protocol.

```java
@Service
class SpringReleasesInfoService {

    @McpTool(description = "Get all releases for a Spring project, including version and support status.")
    List<SpringRelease> fetchReleasesInfo(
            @McpToolParam(description = "The project slug, for example 'spring-boot'") String projectSlug) {
        // Call the Spring API and return the releases
    }
}
```

That is the whole server. There is no transport code, no JSON-RPC handling, and no schema written by hand. An annotation scanner runs by default and looks through your beans for the MCP annotations, so any bean that carries `@McpTool`, `@McpResource`, `@McpPrompt` or `@McpComplete` is picked up on its own. For each one Spring AI reads the method signature, generates the JSON schema, and registers the capability with the protocol. If two tools share a name, only the first one is kept. You never build a registry by hand, and this is very close to the server you will build in the lab.

The other primitives follow the same pattern. `@McpResource` exposes read only context, and the value in the annotation is the URI template a client uses to fetch it. A template can carry a variable in curly braces, and Spring AI binds it to a method parameter of the same name, so one method can serve a whole family of resources.

```java
@McpResource(uri = "config://{key}", name = "Configuration")
String configuration(String key) {
    return configRepository.get(key);
}
```

`@McpPrompt` exposes a reusable template that a client can offer to its user, and `@McpComplete` provides autocompletion suggestions for prompt arguments. Everything exists in a synchronous and an asynchronous form, and if you ever need full control you can use the MCP Java SDK directly.

One distinction is worth making explicit, because the names are similar. The `@Tool` annotation from the tool calling section exposes a method to the model **inside your own application**. The `@McpTool` annotation exposes a method **over the protocol to other applications**. They are independent, and a method can be published through both if you want it available in both places.

A `@Tool` method can still be served over MCP, it just needs a little wiring. Besides scanning for the MCP annotations, the autoconfiguration also collects any `ToolCallback` and `ToolCallbackProvider` beans in your context and converts them into MCP tools. So you wrap your `@Tool` object in a `MethodToolCallbackProvider` and expose it as a bean, and the server publishes it just like an `@McpTool`.

```java
@Bean
ToolCallbackProvider releaseTools(SpringReleasesInfoService service) {
    return MethodToolCallbackProvider.builder().toolObjects(service).build();
}
```

This was in fact the only way to build a Spring AI MCP server before the dedicated MCP annotations arrived, and you will still see it in older code and examples. The `@McpTool` family is the newer and more direct approach, because it registers a method with the protocol without the extra provider bean and gives you MCP specific details such as per parameter descriptions. Use the `ToolCallbackProvider` route mainly when you want to serve tools you already wrote with `@Tool` without duplicating them.

## Authorization (Experimental)

You already saw how authorization works in MCP as a protocol. Once a server is reachable over the network it plays the role of an OAuth resource server, a client discovers where to authenticate from a first failed request, and every following request carries a bearer token that is bound to that one server.

The Spring AI ecosystem addresses this with the [MCP Security](https://github.com/spring-ai-community/mcp-security) community project, which is built on top of Spring Security, so the building blocks are the ones you already know. It consists of three separate parts, and you add only the ones your application needs. The **server security** part turns your MCP server into an OAuth 2.0 resource server that validates the incoming token, and it also offers a simpler API key option for machine to machine calls. The **client security** part gives your MCP client the ability to obtain a token and attach it to every outgoing request, with a choice of OAuth flows for whose permissions should apply. The **authorization server** part is for the case where you want to issue the tokens yourself.

Each of the three parts comes in two variants. There is a Spring Boot module that configures everything from properties, and there is a lower level module for the cases where you want to wire the beans yourself. The Boot modules are the recommended option and are the ones shown below. One thing to check before you start is the version, because the `0.1.x` releases work with Spring AI `2.0.x`, while Spring AI `1.1.x` needs version `0.0.6`.

### Securing Your Server

On the server side you add the Boot module for server security.

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>mcp-server-security-spring-boot</artifactId>
    <version>0.1.13</version>
</dependency>
```

Then you point your application at the authorization server that issues the tokens.

```properties
spring.ai.mcp.server.name=my-mcp-server
spring.ai.mcp.server.protocol=STREAMABLE

# The issuer URI of the authorization server
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9000
```

That is the whole setup. As soon as the issuer URI is present, the autoconfiguration builds a `SecurityFilterChain` that secures every endpoint of your server, so each incoming request must carry a valid JWT. It also publishes the metadata endpoint that the specification expects, which is how a client finds out where it has to authenticate.

Because this is ordinary Spring Security, everything that follows works the way you are used to. The authenticated user is available through the usual `SecurityContextHolder`, and you can protect a single tool exactly like a service method with `@PreAuthorize`, as long as you turn on method security with `@EnableMethodSecurity`.

```java
@McpTool(description = "Create a support ticket for a customer.")
@PreAuthorize("hasAuthority('SCOPE_tickets.write')")
String createTicket(@McpToolParam(description = "The problem description") String description) {
    var user = SecurityContextHolder.getContext().getAuthentication().getName();
    return ticketService.create(user, description);
}
```

This also lets you protect only part of your server. If you permit all requests to `/mcp` in the filter chain and put `@PreAuthorize` on the tool methods, then `initialize` and `tools/list` stay public while `tools/call` still needs a token.

If you need more control you can use the lower level `mcp-server-security` module and write the filter chain by hand. You then add the `McpServerOAuth2Configurer` to your `HttpSecurity` and give it the issuer URI.

The same module also carries the **API key support**, where you provide an `ApiKeyEntityRepository` of valid keys and choose the header name that carries them, which is the simpler option for machine to machine calls without a user.

The server side comes with a few limitations that are worth knowing before you plan your setup. It only works with WebMVC based servers, so a reactive WebFlux server cannot be secured this way. It only accepts JWT tokens, so an authorization server that issues opaque tokens does not fit. And it does not support the deprecated SSE transport, which leaves you with `STREAMABLE` or `STATELESS` as the protocol.

### Securing Your Client

On the client side you add the Boot module for client security next to the normal MCP client starter.

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>mcp-client-security-spring-boot</artifactId>
    <version>0.1.13</version>
</dependency>
```

Your connections are declared exactly as before, plus a few properties for the authorization part.

```properties
spring.ai.mcp.client.type=SYNC
spring.ai.mcp.client.initialized=false
spring.ai.mcp.client.streamable-http.connections.my-mcp-server.url=http://localhost:8090

spring.ai.mcp.client.authorization.dynamic-client-registration.enabled=true
```

The `initialized=false` setting is important. Spring AI normally connects and lists the tools while the application starts, but at that moment there is no user and therefore no token, so you turn that off and let the connection happen later.

The `dynamic-client-registration` property decides how your client gets its OAuth credentials, and the flow is worth looking at once. With it turned on, your client calls the server without a token, the server answers with a `401` and a `WWW-Authenticate` header that points to its resource metadata, the module reads that metadata to find the authorization server, and it registers itself there right away. So your application ends up with a client id and a client secret without you ever creating a client by hand. If the server later answers with a `403` because a scope is missing, the module asks for that additional scope and tries again.

Every URL in that flow comes from the server your client is talking to, so the MCP Security module accepts only HTTPS for them. This prevents an attacker from pointing your application at an address it should never call, an attack known as server side request forgery. On your own machine everything runs over plain HTTP on localhost, and that check would block you immediately. Setting `spring.ai.mcp.client.authorization.dynamic-client-registration.allow-loopback-addresses` to `true` makes an exception for loopback addresses such as `localhost` and `127.0.0.1`, so use it for local development and never enable it in production.

Dynamic client registration is convenient, but you should not rely on it. Many authorization servers do not offer it at all, and many of those that do keep it switched off, because letting anyone register a client is not something every organization wants. That is also why the property is disabled by default. In that case you register your client at the authorization server once by hand and describe it with the well known Spring Security properties, and everything else keeps working the same way.

```properties
spring.security.oauth2.client.registration.my-mcp-server.client-id=my-client
spring.security.oauth2.client.registration.my-mcp-server.client-secret=my-secret
spring.security.oauth2.client.registration.my-mcp-server.authorization-grant-type=authorization_code
spring.security.oauth2.client.registration.my-mcp-server.scope=tickets.read,tickets.write
spring.security.oauth2.client.provider.my-mcp-server.issuer-uri=http://localhost:9000
```

The registration id should match the name of your MCP connection, because that is how the module matches the two by default. If your application has exactly one registration, that one is used for every connection anyway. Asking for a missing scope still works this way, so the only thing you lose is the automatic registration itself.

The last piece is a filter chain with the MCP client configurer.

```java
@Configuration
@EnableWebSecurity
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .with(McpClientOAuth2Configurer.mcpClientOAuth2(), Customizer.withDefaults())
                .build();
    }
}
```

With that in place the module registers everything that is needed to fetch a token and add it to every outgoing request. The configurer also accepts a few options instead of the defaults, for example `registerMcpOAuth2Client("my-mcp-server", "http://localhost:8090/mcp")` to assign a registration to one specific server.

Which flow you pick depends on whose permissions should apply. The authorization code flow is the one the specification describes, and it suits the case where every call happens with the permissions of the signed in user, so a user can only reach the tickets they are allowed to see. The client credentials flow suits background work where no person is involved and your application acts as itself. The hybrid flow is for applications that need both, for example listing tools as the application and calling them later as the user.

If you want to wire this yourself, the lower level `mcp-client-security` module gives you the building blocks.

The client side has its limitations too. Your own application has to be a servlet application, so a WebFlux one does not work, and only synchronous MCP clients are supported. The `initialized=false` property mentioned above is really a limitation as well, because Spring AI would otherwise connect at startup, at a point where no token exists yet. The transport side is less strict, because unlike the server part the client does support the deprecated SSE transport, with both the `HttpClient` and the `WebClient` based clients.

### Issuing the Tokens Yourself

If you do not already run an identity provider you can let your own application issue the tokens. Adding the `mcp-authorization-server-spring-boot` module gives you a working authorization server with no further configuration, because it builds on Spring Authorization Server and adds the pieces the MCP specification expects, such as dynamic client registration and resource indicators. You then only declare your clients and users in properties. Dynamic client registration is on by default and can be turned off with `spring.ai.mcp.authorizationserver.dynamic-client-registration.enabled=false`. A lower level `mcp-authorization-server` module exists as well for the case where you want to build the filter chain yourself.

### What to Expect

One point should be stated clearly. While **MCP Security is developed and maintained by the Spring AI team, it is still in an experimental phase and not part of Spring AI itself yet.**

The main reason is the protocol rather than the Spring code. MCP only finalized its authorization specification recently, and it is still changing, so the libraries built on top of it cannot be stable before the specification underneath them is.

The limitations listed for the server and the client side follow from that, and the authorization server has the same WebMVC restriction.

None of this means you should avoid MCP. It means you should expect changes, keep your security configuration in one place so it is easy to adjust, and be careful about which tools you expose to which callers. It is also worth remembering the general advice from the tool calling section. The model decides which tool to call and with which arguments, and user input can influence it, so treat every tool call as untrusted input, validate the arguments, and give each tool the narrowest permissions it needs.
