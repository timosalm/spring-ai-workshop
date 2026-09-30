# Model Context Protocol (MCP) Lab

Your support assistant already uses tool calling. But the `SupportTicketService` tools run **in the same process** as the assistant. The **Model Context Protocol (MCP)** is a standard way for one process to expose tools, resources, and prompts, so that an AI application in another process can use them.

In this lab you build a small, separate **Spring Releases MCP server** that fetches live release data from `api.spring.io`. Then you connect the support assistant to it as an MCP **client**. After that, a question like "What is the latest release of Spring AI?" is answered from live data instead of the old training knowledge of the model. Finally you secure the MCP server with OAuth 2.0.

Read [Agentic AI Fundamentals](../01-agentic-ai-fundamentals.md), [Model Context Protocol (MCP)](mcp.md), and [MCP With Spring AI](mcp-spring-ai.md) first.

## Before You Start

This lab works with two projects in this folder.

- [`spring-releases-mcp-server/`](spring-releases-mcp-server/) is a freshly generated Spring Boot project for the MCP server.
- [`sample-app/`](sample-app/) is the support assistant after the observability lab.

All paths in this lab are relative to the folder of this lab, so they start with the name of the project. You work with three terminals.

- **Terminal 1** runs the support assistant in `sample-app/`.
- **Terminal 2** sends requests with `curl`.
- **Terminal 3** runs the MCP server in `spring-releases-mcp-server/`.

Both projects include DevTools, which restarts an application when your IDE compiles a changed class. After a change to a `pom.xml` or to a file in `src/main/resources` you stop the application with `Ctrl+C` and start it again. The security part needs **Docker**.

Every Java change shows the **complete file**, so you can replace the whole content of the file.

## Part 1. Expose Tools With an MCP Server

### 1. Look at the MCP Server Project

The MCP server is a **second, separate** Spring Boot application. It was generated with the [Spring Initializr](https://start.spring.io). You do not have to run this command, because the result is already in `spring-releases-mcp-server/`.

```bash
curl https://start.spring.io/starter.zip \
  -d dependencies=web,actuator,spring-ai-mcp-server,devtools \
  -d type=maven-project \
  -d groupId=com.example \
  -d artifactId=spring-releases-mcp-server \
  -d name=spring-releases \
  -d packageName=com.example.spring_releases \
  -d javaVersion=21 \
  -o spring-releases-mcp-server.zip
```

Because `web` is selected as well, the Initializr id `spring-ai-mcp-server` resolves to the WebMVC starter `spring-ai-starter-mcp-server-webmvc`. Open `spring-releases-mcp-server/pom.xml` and find it. That starter provides the Streamable HTTP transport. Unlike the support assistant, this server does not need a model provider starter such as OpenAI, Anthropic, Amazon Bedrock, or Ollama. It only *exposes* tools over the protocol and never calls an LLM itself.

The `application.properties` of the project already sets `server.port=8090`, so the server does not clash with the support assistant on port 8080.

### 2. Configure the Server

Spring AI can already run an MCP server with its defaults, but a few properties are worth setting yourself. They control the identity that the server shows to clients, the transport it speaks, and more.

Append the following lines to `spring-releases-mcp-server/src/main/resources/application.properties`.

```properties

spring.ai.mcp.server.name=${spring.application.name}
spring.ai.mcp.server.protocol=STREAMABLE
spring.ai.mcp.server.version=1.0.0

logging.level.io.modelcontextprotocol.server=DEBUG
```

The server will be reachable at `http://localhost:8090/mcp`. The `STREAMABLE` protocol matches what the support assistant will call. The server name `spring-releases` is what clients see when they inspect the connection. The debug logging lets you watch every JSON-RPC message that the server sends and receives. That helps while you learn the protocol, but you should switch it off in production.

### 3. The MCP Tool Service

Create `spring-releases-mcp-server/src/main/java/com/example/spring_releases/SpringRelease.java` with the following code. The record maps directly to what `api.spring.io` returns for a single release.

```java
package com.example.spring_releases;

record SpringRelease(String version, String status, boolean current) {
}
```

Now create the service that exposes the release lookup as an MCP tool. Create `spring-releases-mcp-server/src/main/java/com/example/spring_releases/SpringReleasesInfoService.java` with the following code.

```java
package com.example.spring_releases;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
class SpringReleasesInfoService {

    private static final Logger log = LoggerFactory.getLogger(SpringReleasesInfoService.class);

    private final RestClient client = RestClient.create("https://api.spring.io");

    @McpTool(description = "Get all releases for a Spring project, including version and support status.")
    List<SpringRelease> fetchReleasesInfo(
            @McpToolParam(description = "The project slug, e.g. 'spring-boot', 'spring-framework', 'spring-ai'") String projectSlug) {
        log.info("Fetch spring release info for project {} called", projectSlug);

        return client.get()
                .uri("/projects/{slug}/releases", projectSlug)
                .retrieve()
                .body(ReleasesResponse.class)
                .embedded()
                .releases();
    }

    private record ReleasesResponse(@JsonProperty("_embedded") Embedded embedded) {
        record Embedded(List<SpringRelease> releases) {
        }
    }
}
```

A few parts are worth a closer look.

- The **`@McpTool`** annotation is the MCP specific version of the `@Tool` annotation that you used in `SupportTicketService`. At startup the server scans every bean for methods that carry it and registers them with the protocol. The description is written for the model, because a client hands exactly this text to the LLM when it decides which tool fits a question.
- **`@McpToolParam`** is the MCP counterpart of `@ToolParam`. Spring AI builds the JSON schema of the tool from the method signature, and this text tells the model what a valid project slug looks like. You see that schema in a moment when you ask the server for its tool list.
- Everything below the annotations is plain Spring. The `RestClient` pulls live data from the public release API of Spring, and the method simply returns a `List<SpringRelease>`. Spring AI turns that list into JSON before it goes back over the protocol, so you never touch the wire format yourself. A production tool would add error handling, caching, and retries here.
- The nested records are only plumbing. `api.spring.io` wraps its results in an `_embedded` object, so these records exist only to unwrap the response for Jackson. They have nothing to do with MCP.

### 4. Run the Server

In **Terminal 3**, start the MCP server.

```bash
cd spring-releases-mcp-server
./mvnw spring-boot:run
```

You should see the embedded MCP server start on port 8090 and log one registered tool.

### 5. Test the MCP Server Directly

The Streamable HTTP transport speaks JSON-RPC over HTTP. The first request must be `initialize`. It returns the session id in the `Mcp-Session-Id` response header, and you reuse that header on the calls that follow.

In **Terminal 2**, open an MCP session and keep its session id in a variable.

```bash
SESSION_ID=$(curl -sS -D - -o /dev/null -X POST http://localhost:8090/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
          "protocolVersion": "2025-06-18",
          "capabilities": {},
          "clientInfo": { "name": "curl", "version": "1" }
        }
      }' | grep -i '^mcp-session-id:' | awk '{print $2}' | tr -d '\r')

echo "Session: $SESSION_ID"
```

List the tools that the server offers.

```bash
curl -sS -X POST http://localhost:8090/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "Mcp-Session-Id: $SESSION_ID" \
  -d '{
        "jsonrpc": "2.0",
        "id": 2,
        "method": "tools/list",
        "params": {}
      }'
```

You should see one entry. It is `fetchReleasesInfo`, with its description and the JSON schema for the `projectSlug` parameter.

Call the tool directly.

```bash
curl -sS -X POST http://localhost:8090/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "Mcp-Session-Id: $SESSION_ID" \
  -d '{
        "jsonrpc": "2.0",
        "id": 3,
        "method": "tools/call",
        "params": {
          "name": "fetchReleasesInfo",
          "arguments": { "projectSlug": "spring-ai" }
        }
      }'
```

You get back the current Spring AI releases from `api.spring.io`, wrapped in the content envelope of MCP.

## Part 2. Consume Tools With an MCP Client

The Spring Releases MCP server now runs on port 8090. Next you connect the support assistant to it as an MCP **client**. The model can then call the remote `fetchReleasesInfo` tool next to its ticket tools in the same process.

### 6. Connect to the MCP Server

Add the following dependency to `sample-app/pom.xml`, right after the `spring-ai-markdown-document-reader` dependency.

```xml
		<dependency>
			<groupId>org.springframework.ai</groupId>
			<artifactId>spring-ai-starter-mcp-client</artifactId>
		</dependency>
```

For every named connection in your configuration, the starter creates one MCP client. It then exposes a single `ToolCallbackProvider` bean that gathers every remote tool.

Now declare the connection itself. Append the following lines to `sample-app/src/main/resources/application.properties`.

```properties

spring.ai.mcp.client.streamable-http.connections.spring-releases.url=http://localhost:8090

# Verbose protocol logging while you're learning, drop these in production
logging.level.io.modelcontextprotocol.client=DEBUG
logging.level.io.modelcontextprotocol.spec=DEBUG
```

The `spring-releases` segment is the name of the connection, which you choose yourself.

### 7. Register the Remote Tools With the ChatClient

You could pass the remote tools into every `chatClient.prompt()` call. Here you register them once as default tools on the `ChatClient` bean instead.

Replace the content of `sample-app/src/main/java/com/example/support_assistant/SupportAssistantConfiguration.java` with the following code.

```java
package com.example.support_assistant;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;

@Configuration
public class SupportAssistantConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt,
                                 ChatMemory chatMemory,
                                 ToolCallbackProvider tools) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE),
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(tools)
                .build();
    }

    @ConditionalOnMissingBean(VectorStore.class)
    @Bean
    VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
```

Two things changed in the bean.

- There is a new **`ToolCallbackProvider tools`** parameter. The auto configuration of the MCP client contributes this bean for you, and it already gathers the tools of every connection in your configuration. All you have to do is inject it.
- **`.defaultTools(tools)`** hands those tools to the builder. Every call through this `ChatClient` can now reach the remote tools without any further wiring, which is why `SupportAssistantService` stays untouched.

The ticket tools are still added per call with `.tools(supportTicketService)` in `SupportAssistantService`. Default tools and per call tools are combined, so the model sees both groups in the same request.

### 8. Try It Out

In **Terminal 1**, export your key and start the support assistant.

```bash
cd sample-app
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

In the logs you see the MCP client connect to `http://localhost:8090/mcp` at startup. Now ask a question in **Terminal 2** that only the remote tool can answer.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=What is the latest stable release of Spring AI?"
```

In the logs of the assistant you see the model call `fetchReleasesInfo` with `{"projectSlug": "spring-ai"}`. The result is fed back into the model for the final answer, and the MCP server logs the call in Terminal 3.

If the model answers from the retrieved Tanzu documents instead and skips the tool, that is the RAG prompt pulling it towards the context. Phrasing the request as an action, as in the next query, calls the tool reliably.

Now try a query that uses both the remote MCP tool and a local tool in a single turn.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=What is the latest stable release of Spring AI? Please also open a high-priority ticket to request access to Spring Application Advisor to accelerate upgrading our application to that version."
```

The model calls `fetchReleasesInfo`, the remote MCP tool, and `createTicket`, the local tool, in one turn.

## Part 3. Secure the MCP Server With OAuth 2.0 (Optional)

Your MCP server is open. Anyone who can reach port 8090 can list its tools and call them, which is fine on your machine but not on a network.

The MCP specification answers this with OAuth 2.0. The server becomes an **OAuth 2.0 resource server**, so every request must carry a valid access token, and the client obtains that token from an **authorization server**.

In this part you add all three pieces. You run [Keycloak](https://www.keycloak.org) as the authorization server, you turn the Spring Releases MCP server into a resource server, and you give the support assistant the ability to fetch a token and attach it to every MCP call.

### 9. Run Keycloak as the Authorization Server

Keycloak is an identity service that speaks OpenID Connect. It ships as a single container image, and its development mode needs no database, so you run it with Docker Compose, the same way you ran the observability stack. This time the Compose file belongs to the MCP server project and not to the support assistant, because the MCP server talks to the authorization server while it starts and does not come up without it.

Keycloak keeps its configuration in a realm. You can hand a realm to the container as a JSON file, so every start begins with the same setup. Create `spring-releases-mcp-server/keycloak-realm.json` with the following content.

```json
{
  "realm": "spring",
  "enabled": true,
  "sslRequired": "none",
  "users": [
    {
      "username": "alice",
      "enabled": true,
      "email": "alice@jon.es",
      "emailVerified": true,
      "firstName": "Alice",
      "lastName": "Jones",
      "realmRoles": [
        "default-roles-spring"
      ],
      "credentials": [
        {
          "type": "password",
          "value": "password",
          "temporary": false
        }
      ]
    }
  ],
  "components": {
    "org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy": [
      {
        "name": "Trusted Hosts",
        "providerId": "trusted-hosts",
        "subType": "anonymous",
        "subComponents": {},
        "config": {
          "trusted-hosts": [
            "localhost"
          ],
          "host-sending-registration-request-must-match": [
            "false"
          ],
          "client-uris-must-match": [
            "true"
          ]
        }
      }
    ]
  }
}
```

The realm is called `spring`, and its name becomes part of the issuer URL further down. It holds one user, `alice`, with the password `password` and the email address `alice@jon.es`. You sign in as that user later. The `Trusted Hosts` policy allows clients from `localhost` to register themselves without an account, which you need for the dynamic client registration later in this part.

Next, the Compose file that runs Keycloak. Create `spring-releases-mcp-server/compose.yaml` with the following content.

```yaml
services:
  authorization-server:
    image: quay.io/keycloak/keycloak:26.4
    container_name: spring-releases-mcp-server-keycloak
    command: ["start-dev", "--import-realm"]
    environment:
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: admin
      KC_HEALTH_ENABLED: "true"
    volumes:
      - ./keycloak-realm.json:/opt/keycloak/data/import/realm.json:ro
    ports:
      - "5556:8080"
    healthcheck:
      test: ["CMD", "bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /realms/spring/protocol/openid-connect/certs HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && grep -q '\"keys\"' <&3"]
      interval: 5s
      timeout: 5s
      retries: 30
```

The health check matters more than it looks. It asks for the signing keys of the `spring` realm, so the container only reports healthy once Keycloak answers and the realm import has finished. Spring Boot waits for that before it continues to start your application. Without the health check the MCP server would try to read the Keycloak configuration too early and fail.

Spring Boot starts the Compose file for you when the `spring-boot-docker-compose` module is on the classpath. Add the following dependency to `spring-releases-mcp-server/pom.xml`, right after the `spring-boot-devtools` dependency.

```xml
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-docker-compose</artifactId>
			<scope>runtime</scope>
			<optional>true</optional>
		</dependency>
```

### 10. Turn the MCP Server Into a Resource Server

The server side needs a single dependency. `mcp-server-security-spring-boot` is the Spring Boot module of the [MCP Security](https://github.com/spring-ai-community/mcp-security) project. It pulls in Spring Security and the OAuth 2.0 resource server support, and it adds the auto configuration that wires them together for MCP.

Add the following dependency to `spring-releases-mcp-server/pom.xml`, right after the `spring-ai-starter-mcp-server-webmvc` dependency.

```xml

		<dependency>
			<groupId>org.springaicommunity</groupId>
			<artifactId>mcp-server-security-spring-boot</artifactId>
			<version>0.1.13</version>
		</dependency>
```

The MCP Security modules are not part of the Spring AI release train yet, so they carry their own version number instead of getting it from the Spring AI BOM.

Append the following lines to `spring-releases-mcp-server/src/main/resources/application.properties`.

```properties

spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:5556/realms/spring
spring.security.oauth2.resourceserver.jwt.principal-claim-name=email
```

The `issuer-uri` is the only value the server needs. At startup Spring Security reads the discovery document of Keycloak under that URL, finds the `jwks_uri` with the signing keys, and builds a decoder that validates the signature, the issuer, and the expiry of every incoming token.

By default the name of the authenticated user comes from the `sub` claim, which Keycloak fills with an internal identifier. With `principal-claim-name` you tell Spring Security to use the `email` claim instead, so the user shows up with a readable name.

### 11. Watch the Server Reject Anonymous Calls

Stop the support assistant in Terminal 1 with `Ctrl+C`, because it would fail against the secured server. Then stop the MCP server in Terminal 3 with `Ctrl+C` and start it again. This time it starts the Keycloak container through Docker Compose first, which takes a while on the first run.

```bash
./mvnw spring-boot:run
```

Once the server is up, you can look at the discovery document that Spring Security read at startup.

```bash
curl -s http://localhost:5556/realms/spring/.well-known/openid-configuration | jq
```

Now repeat the `initialize` call from the first part, without a token. The `-i` option prints the response headers.

```bash
curl -sS -i -X POST http://localhost:8090/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
          "protocolVersion": "2025-06-18",
          "capabilities": {},
          "clientInfo": { "name": "curl", "version": "1" }
        }
      }'
```

You get `401 Unauthorized` with this response header.

```text
WWW-Authenticate: Bearer resource_metadata=http://localhost:8090/.well-known/oauth-protected-resource/mcp
```

That header is the heart of MCP authorization. The failed request tells the client where to look next. Follow it.

```bash
curl -s http://localhost:8090/.well-known/oauth-protected-resource/mcp | jq
```

This protected resource metadata names the authorization server that issues tokens for this MCP server, which is your Keycloak realm. A client that knows nothing about the server can find its way to a token from here.

### 12. Protect a Single Tool

The filter chain already protects the whole endpoint. Because this is ordinary Spring Security, you can also protect a single tool, and you can read the caller inside the tool method.

Replace the content of `spring-releases-mcp-server/src/main/java/com/example/spring_releases/SpringReleasesInfoService.java` with the following code.

```java
package com.example.spring_releases;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@EnableMethodSecurity
@Service
class SpringReleasesInfoService {

    private static final Logger log = LoggerFactory.getLogger(SpringReleasesInfoService.class);

    private final RestClient client = RestClient.create("https://api.spring.io");

    @PreAuthorize("isAuthenticated()")
    @McpTool(description = "Get all releases for a Spring project, including version and support status.")
    List<SpringRelease> fetchReleasesInfo(
            @McpToolParam(description = "The project slug, e.g. 'spring-boot', 'spring-framework', 'spring-ai'") String projectSlug) {
        var user = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("Fetch spring release info for project {} called by {}", projectSlug, user);

        return client.get()
                .uri("/projects/{slug}/releases", projectSlug)
                .retrieve()
                .body(ReleasesResponse.class)
                .embedded()
                .releases();
    }

    private record ReleasesResponse(@JsonProperty("_embedded") Embedded embedded) {
        record Embedded(List<SpringRelease> releases) {
        }
    }
}
```

Three things changed.

- **`@EnableMethodSecurity`** switches on the checks for security annotations on methods.
- **`@PreAuthorize`** works on a tool method like on any other method of a Spring bean. This rule only asks for an authenticated caller, which the filter chain already guarantees. The interesting version checks what the token allows, for example `@PreAuthorize("hasAuthority('SCOPE_releases.read')")`. Keycloak writes the granted scopes into the access token, and Spring Security turns each one into an authority with a `SCOPE_` prefix, so a rule like that works once you add the scope to the realm.
- Inside the tool method you reach the authenticated user through the normal **`SecurityContextHolder`**. Because you set `principal-claim-name` to `email`, `getName()` returns the email address of the signed in user. A production tool would use this to look up what that user is allowed to see, instead of trusting an identifier that the model passed as an argument.

### 13. Give the MCP Client a Token

The support assistant now talks to a server that rejects it. Start it in Terminal 1 and watch what happens.

```bash
./mvnw spring-boot:run
```

The application fails to start with an `Authorization error when sending message`. Spring AI creates the MCP client from your properties at startup and connects to the server right away. At that moment no user is signed in, so there is no token to send. You fix this with a property further down.

Add the following dependency to `sample-app/pom.xml`, right after the `spring-ai-starter-mcp-client` dependency.

```xml
		<dependency>
			<groupId>org.springaicommunity</groupId>
			<artifactId>mcp-client-security-spring-boot</artifactId>
			<version>0.1.13</version>
		</dependency>
```

The `mcp-client-security-spring-boot` module brings its own auto configuration. When it finds exactly one OAuth 2.0 client registration, it wires the MCP transport so that every outgoing request carries the token of the current user.

Now add the configuration it needs. Append the following lines to `sample-app/src/main/resources/application.properties`.

```properties

spring.ai.mcp.client.initialized=false

spring.ai.mcp.client.authorization.dynamic-client-registration.enabled=true
# For development purposes, explicitly allows HTTP for loopback addresses (MCP Security enforces HTTPS for all URLs involved in the Dynamic Client Registration flow)
spring.ai.mcp.client.authorization.dynamic-client-registration.allow-loopback-addresses=true
```

`spring.ai.mcp.client.initialized=false` is the fix for the startup failure. The MCP client no longer connects while the application starts. It connects on the first request instead, and by then a user is signed in and there is a token to send.

The other two keys turn on **dynamic client registration**. You never write down a client id or a client secret. The assistant creates them for itself the first time it needs a token, and it takes the same steps that you took by hand in the previous step. The `401` points to the protected resource metadata. That document names the authorization server. The assistant then asks that server to register it, and it keeps the client id and the secret that it gets back. The MCP specification prefers this way, because a host can use a new server without any manual setup.

MCP Security allows only HTTPS for the URLs in this exchange, and `localhost` uses plain HTTP. The `allow-loopback-addresses` key removes that restriction for local addresses. Use it in development only.

The registration asks for the `authorization_code` grant. This means the assistant acts **on behalf of the signed in user**, which is the flow that the MCP specification describes, and it is the reason the identity of the user reaches the tool. For background work without a user you would pick `client_credentials` instead, so the application acts as itself.

### 14. Write the Security Configuration

Configure the `SecurityFilterChain` with the provided `McpClientOAuth2Configurer`. Replace the content of `sample-app/src/main/java/com/example/support_assistant/SupportAssistantConfiguration.java` with the following code.

```java
package com.example.support_assistant;

import org.springaicommunity.mcp.security.client.sync.config.McpClientOAuth2Configurer;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@Configuration
public class SupportAssistantConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt,
                                 ChatMemory chatMemory,
                                 ToolCallbackProvider tools) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE),
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(tools)
                .build();
    }

    @ConditionalOnMissingBean(VectorStore.class)
    @Bean
    VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .with(McpClientOAuth2Configurer.mcpClientOAuth2(), Customizer.withDefaults())
                .csrf(CsrfConfigurer::disable)
                .build();
    }
}
```

Look at the new `mcpSecurityFilterChain` bean.

- `auth.anyRequest().permitAll()` keeps the endpoints of the assistant itself open, so nobody has to sign in to use the support assistant without the tools of the MCP server.
- `McpClientOAuth2Configurer.mcpClientOAuth2()` does all the OAuth 2.0 wiring in a single line. Underneath it switches on the normal `oauth2Client` support of Spring Security for MCP, which runs the authorization code flow and keeps the token in the session of the user. When a request needs a token and there is none yet, Spring Security sends the user to Keycloak, remembers the original request, and repeats it after the login. On top of that the configurer adds the MCP specific part. It puts a `resource` parameter into the authorization request and into the token request, and that parameter names the MCP server the token is for.
- CSRF protection is disabled, because this REST API has no browser forms to protect.

### 15. Test the Whole Chain

Stop the support assistant in Terminal 1 if it still runs, and start it again, so it picks up the new dependency.

```bash
./mvnw spring-boot:run
```

The assistant now runs a browser flow on your behalf, so `curl` has to keep cookies and follow redirects. The options `-c` and `-b` share a cookie file, and `-L` follows every redirect. Because nobody is signed in yet, this first request ends on the Keycloak sign in page instead of on an answer. The command stores that page and extracts the URL of the login form from it.

```bash
curl -sS -c /tmp/cookies -b /tmp/cookies -L -G "http://localhost:8080/api/v1/chat" \
  --data-urlencode "query=What is the latest stable release of Spring AI? Please also open a high-priority ticket to request access to Spring Application Advisor to accelerate upgrading our application to that version." \
  -o /tmp/login.html

LOGIN_URL=$(grep -o 'action="[^"]*"' /tmp/login.html | head -1 | sed 's/action="//; s/"$//; s/&amp;/\&/g')

echo "Login URL: $LOGIN_URL"
```

Spring Security remembered your question while it sent you to Keycloak. Sign in as `alice` with the same cookie file.

```bash
curl -sS -c /tmp/cookies -b /tmp/cookies -L \
  -d "username=alice" \
  -d "password=password" \
  "$LOGIN_URL"
```

You get the answer as before. What happened in between is the interesting part. The chat request needed the remote tool, and the MCP client had neither a registered client nor a token. So it registered itself with Keycloak, and Spring Security sent `curl` to the sign in page. After you signed in, Keycloak sent `curl` back with a code, the assistant exchanged the code for a token, and then the original chat request ran again with that token attached.

You are signed in now, so a second question needs one command only.

```bash
curl -sS -c /tmp/cookies -b /tmp/cookies -L -G "http://localhost:8080/api/v1/chat" \
  --data-urlencode "query=What is the latest stable release of Spring AI? Please also open a high-priority ticket to request access to Spring Application Advisor to accelerate upgrading our application to that version."
```

Now look at Terminal 3, where the MCP server runs. You find a line like this one.

```text
Fetch spring release info for project spring-ai called by alice@jon.es
```

The identity of the end user travelled from Keycloak, through the support assistant, into the MCP server, and all the way into the tool method.

## Part 4. Make Security Opt-In With a Profile

You do not want to run Keycloak on every start from now on. The applications of the next labs therefore keep everything from Part 3 behind a Spring profile called `mcp-security`, in the same way the observability stack sits behind `local-observability`. Without that profile the MCP server stays open, the support assistant sends no token, and no Keycloak container has to run.

You can make this change yourself with the steps below, or skip them and continue with the applications of the next lab, which already contain it.

### 16. The MCP Server

Put the Keycloak service behind an `mcp-security` Docker Compose profile. Append the following lines to `spring-releases-mcp-server/compose.yaml`.

```yaml
    profiles:
      - mcp-security
```

Create `spring-releases-mcp-server/src/main/resources/application-mcp-security.properties` with the following content. It holds the security settings and starts the Compose profile.

```properties
spring.autoconfigure.exclude=

spring.docker.compose.enabled=true
spring.docker.compose.profiles.active=mcp-security

spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:5556/realms/spring
spring.security.oauth2.resourceserver.jwt.principal-claim-name=email
```

Replace the content of `spring-releases-mcp-server/src/main/resources/application.properties` with the following. Without the profile it turns off Docker Compose and the security auto configuration of Spring Boot. The empty `spring.autoconfigure.exclude=` in the profile file switches the security back on.

```properties
spring.application.name=spring-releases

spring.ai.mcp.server.name=${spring.application.name}
spring.ai.mcp.server.protocol=STREAMABLE
spring.ai.mcp.server.version=1.0.0
server.port=8090

logging.level.io.modelcontextprotocol.server=DEBUG

spring.docker.compose.enabled=false

# Turn off Spring Security unless the mcp-security profile is active
spring.autoconfigure.exclude=\
  org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,\
  org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration
```

The tool method has to work without a signed in user as well. Replace the content of `spring-releases-mcp-server/src/main/java/com/example/spring_releases/SpringReleasesInfoService.java` with the following code.

```java
package com.example.spring_releases;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@EnableMethodSecurity
@Service
class SpringReleasesInfoService {

    private static final Logger log = LoggerFactory.getLogger(SpringReleasesInfoService.class);

    private final RestClient client = RestClient.create("https://api.spring.io");

    @PreAuthorize("!@environment.matchesProfiles('mcp-security') or isAuthenticated()")
    @McpTool(description = "Get all releases for a Spring project, including version and support status.")
    List<SpringRelease> fetchReleasesInfo(
            @McpToolParam(description = "The project slug, e.g. 'spring-boot', 'spring-framework', 'spring-ai'") String projectSlug) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var user = authentication != null ? authentication.getName() : "anonymous";
        log.info("Fetch spring release info for project {} called by {}", projectSlug, user);

        return client.get()
                .uri("/projects/{slug}/releases", projectSlug)
                .retrieve()
                .body(ReleasesResponse.class)
                .embedded()
                .releases();
    }

    private record ReleasesResponse(@JsonProperty("_embedded") Embedded embedded) {
        record Embedded(List<SpringRelease> releases) {
        }
    }
}
```

The `@PreAuthorize` rule now only asks for an authenticated caller when the `mcp-security` profile is active, and the log line falls back to `anonymous` when there is no user.

### 17. The Support Assistant

Create `sample-app/src/main/resources/application-mcp-security.properties` with the following content. These are the three client properties from step 13.

```properties
spring.ai.mcp.client.initialized=false

spring.ai.mcp.client.authorization.dynamic-client-registration.enabled=true
# For development purposes, explicitly allows HTTP for loopback addresses (MCP Security enforces HTTPS for all URLs involved in the Dynamic Client Registration flow)
spring.ai.mcp.client.authorization.dynamic-client-registration.allow-loopback-addresses=true
```

Then remove the same three properties from the main configuration. Replace the content of `sample-app/src/main/resources/application.properties` with the following.

```properties
spring.application.name=support-assistant
spring.devtools.restart.enabled=true

spring.mvc.apiversion.use.path-segment=1
spring.mvc.apiversion.supported=1.0
spring.mvc.apiversion.default=1.0

spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.model=gpt-5.6-sol
spring.ai.openai.chat.reasoning-effort=none
spring.ai.openai.chat.temperature=0.7
spring.ai.openai.embedding.model=text-embedding-3-small

spring.datasource.url=jdbc:h2:mem:supportdb;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
spring.datasource.driver-class-name=org.h2.Driver

logging.level.org.springframework.ai=debug

management.endpoints.web.exposure.include=health,metrics,prometheus

# The observability stack is opt-in, enable it with the local-observability profile
management.otlp.metrics.export.enabled=false
management.otlp.tracing.export.enabled=false
management.otlp.logging.export.enabled=false
spring.docker.compose.enabled=false

spring.ai.mcp.client.streamable-http.connections.spring-releases.url=http://localhost:8090

# Verbose protocol logging while you're learning, drop these in production
logging.level.io.modelcontextprotocol.client=DEBUG
logging.level.io.modelcontextprotocol.spec=DEBUG
```

Finally, use the MCP filter chain only with the profile, and an open filter chain without it. Replace the content of `sample-app/src/main/java/com/example/support_assistant/SupportAssistantConfiguration.java` with the following code.

```java
package com.example.support_assistant;

import org.springaicommunity.mcp.security.client.sync.config.McpClientOAuth2Configurer;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@Configuration
public class SupportAssistantConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt,
                                 ChatMemory chatMemory,
                                 ToolCallbackProvider tools) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE),
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(tools)
                .build();
    }

    @ConditionalOnMissingBean(VectorStore.class)
    @Bean
    VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    @Profile("mcp-security")
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .with(McpClientOAuth2Configurer.mcpClientOAuth2(), Customizer.withDefaults())
                .csrf(CsrfConfigurer::disable)
                .build();
    }

    @Bean
    @Profile("!mcp-security")
    SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(CsrfConfigurer::disable)
                .build();
    }
}
```

Restart both applications without a profile, and the assistant talks to the open MCP server again without Keycloak. When you want the secured setup back, start both of them with the profile. First the MCP server in Terminal 3.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=mcp-security
```

Then the support assistant in Terminal 1.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=mcp-security
```

## Recap

Your assistant now reaches beyond its own process. It uses tools from a separate MCP server next to its own, and the MCP server can require an OAuth 2.0 token that carries the identity of the user all the way into the tool. The [`sample-app/`](../03-agentic-patterns/sample-app/) and [`spring-releases-mcp-server/`](../03-agentic-patterns/spring-releases-mcp-server/) of the agentic patterns lab contain the result of this lab. Next you scale the assistant to many tools with agentic patterns.
