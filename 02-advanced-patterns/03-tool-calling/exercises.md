# Tool Calling Lab

In this lab you teach the model to **act**, not just answer. With the tool calling support of Spring AI, the support assistant can file new support tickets and read existing ones from a relational database. The model decides when to call each tool based on the request of the user, Spring AI runs the tool, and the result flows back into the answer.

Read [Foundations of RAG and Tool Calling](../01-foundations.md) and [Tool Calling](01-tool-calling.md) first.

## Before You Start

Your starting point is the [`sample-app/`](sample-app/) of this folder. It is the assistant from the RAG lab, a `ChatClient` with a default system prompt, a `QuestionAnswerAdvisor` in the chain, and a Markdown knowledge base that is indexed at startup. All paths in this lab are relative to `sample-app/`, and all commands run inside it.

As before you work with two terminals. **Terminal 1** runs the application, and **Terminal 2** sends requests with `curl`. DevTools restarts the application when your IDE compiles a changed class. After a change to `pom.xml` or to a file in `src/main/resources` you stop the application with `Ctrl+C` and start it again.

Every Java change shows the **complete file**, so you can replace the whole content of the file.

## 1. Add the Persistence Dependencies

The tickets need to be stored somewhere. This lab uses Spring Data JDBC together with the in memory H2 database.

Add the following dependencies to `pom.xml`, right after the `spring-ai-markdown-document-reader` dependency.

```xml

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jdbc</artifactId>
		</dependency>
		<dependency>
			<groupId>com.h2database</groupId>
			<artifactId>h2</artifactId>
			<scope>runtime</scope>
		</dependency>
```

## 2. Configure the Datasource

Append the following lines to `src/main/resources/application.properties`.

```properties

spring.datasource.url=jdbc:h2:mem:supportdb;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
spring.datasource.driver-class-name=org.h2.Driver
```

The two H2 flags make the column matching case insensitive, so the snake case column names match the camel case record components that Spring Data infers.

## 3. Create the Schema

Spring Boot runs a `schema.sql` file automatically against the configured datasource at startup.

Create `src/main/resources/schema.sql` with the following content.

```sql
CREATE TABLE IF NOT EXISTS support_ticket (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    summary VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL);
```

## 4. The SupportTicket Entity

Before the model can file tickets, you need some plain Spring Data building blocks. There is nothing specific to AI here yet, but the design choices matter for the tool calls later.

Create `src/main/java/com/example/support_assistant/SupportTicket.java` with the following code.

```java
package com.example.support_assistant;

import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("support_ticket")
record SupportTicket(@Nullable @Id Long id, String summary, SupportCategory category, Priority priority,
                     Status status, LocalDateTime createdAt) {

    @PersistenceCreator
    SupportTicket { }

    SupportTicket(String summary, SupportCategory category, Priority priority) {
        this(null, summary, category, priority, Status.OPEN, LocalDateTime.now());
    }

    SupportTicket withId(Long id) {
        return new SupportTicket(id, summary, category, priority, status, createdAt);
    }

    enum Status {
        OPEN, IN_PROGRESS, CLOSED
    }

    enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
```

A few things are worth pointing out.

- **`@PersistenceCreator`** on the compact canonical constructor tells Spring Data which constructor to use, so it always uses the one with six arguments when it reads rows.
- The **convenience constructor** is for the tool calls. The model only needs to provide `summary`, `category`, and `priority`. The `id` is `null` because the database creates it, `status` starts as `OPEN`, and `createdAt` is set to now.
- Spring Data calls the **`withId(...)`** method after the INSERT, to put the id that the database generated into a new record instance.
- `Status` and `Priority` are **nested enums**, because they are closely tied to the ticket. The `category` reuses the `SupportCategory` enum that you already know from the structured `SupportResponse`.

## 5. The Repository

Create `src/main/java/com/example/support_assistant/SupportTicketRepository.java` with the following code.

```java
package com.example.support_assistant;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
interface SupportTicketRepository extends ListCrudRepository<SupportTicket, Long> {
    List<SupportTicket> findByStatus(String status);
    List<SupportTicket> findByCategory(String category);
}
```

`ListCrudRepository` gives you `save`, `findAll`, `findById`, and more. The two derived query methods cover the lookups that you expose as tools.

## 6. Run the App

In **Terminal 1**, export your key and start the application. The first run downloads the new dependencies.

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

Wait for `Started SupportAssistantApplication` in the logs. Then check in **Terminal 2** that everything is wired up, including the new datasource.

```bash
curl http://localhost:8080/actuator/health
```

You should see `{"status":"UP"}`. Keep the application running.

## 7. Implement the Service That Provides the Tools

Now you use the tool calling support of Spring AI. You put the `@Tool` annotation on your methods, and the framework does four things for you.

1. It reads the method signature and builds a JSON schema that describes the tool.
2. It sends these schemas to the model with every call.
3. When the model asks to call a tool, it runs the method with the arguments that the model provided.
4. It sends the return value back to the model, so the model can finish its answer.

Create `src/main/java/com/example/support_assistant/SupportTicketService.java` with the following code.

```java
package com.example.support_assistant;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class SupportTicketService {

    private final SupportTicketRepository ticketRepository;

    SupportTicketService(SupportTicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Tool(description = "Create a new support ticket. Use this when the user explicitly requests to create, open, or file a support ticket.")
    SupportTicket createTicket(
            @ToolParam(description = "Brief summary of the issue (max 100 chars)") String summary,
            @ToolParam(description = "The category of the issue") SupportCategory category,
            @ToolParam(description = "The priority of the support ticket") SupportTicket.Priority priority) {
        var ticket = new SupportTicket(summary, category, priority);
        return ticketRepository.save(ticket);
    }

    @Tool(description = "List all support tickets")
    List<SupportTicket> retrieveTickets() {
        return ticketRepository.findAll();
    }

    @Tool(description = "List all support tickets that are not yet resolved")
    List<SupportTicket> retrieveOpenTickets() {
        return ticketRepository.findByStatus("OPEN");
    }
}
```

Two annotations do most of the work.

- The description in **`@Tool(description = "...")`** is written for the model, not for you, and it is the most important text in this step. The model reads it to decide whether to call the tool, so it says clearly that the user has to ask for a ticket. The name of the tool comes from the method name.
- Each parameter gets its own **`@ToolParam(description = "...")`**, which the model reads to fill in the arguments. For the two enum types Spring AI puts the possible values into the schema, so the model is told to pick one of them.

## 8. Register the Tools on the ChatClient

Inject the `SupportTicketService` into the `SupportAssistantService` and register its tools on the call. Replace the content of `src/main/java/com/example/support_assistant/SupportAssistantService.java` with the following code.

```java
package com.example.support_assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
class SupportAssistantService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final SupportTicketService supportTicketService;

    @Value("classpath:/prompts/rag-prompt.st")
    private Resource ragPromptResource;

    SupportAssistantService(ChatClient chatClient, VectorStore vectorStore, SupportTicketService supportTicketService) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.supportTicketService = supportTicketService;
    }

    SupportResponse generateResponse(String query, String conversationId) {
        var ragSearchRequest = SearchRequest.builder().topK(4).similarityThreshold(0.4).build();
        var promptTemplate = PromptTemplate.builder().resource(ragPromptResource).build();
        var ragAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(ragSearchRequest)
                .promptTemplate(promptTemplate)
                .build();

        return chatClient.prompt()
                .user(u -> u
                        .text("Answer the following question with a short, well-structured explanation: {question}")
                        .param("question", query))
                .advisors(ragAdvisor)
                .tools(supportTicketService)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .entity(SupportResponse.class);
    }
}
```

`.tools(supportTicketService)` registers every method of the bean that carries the `@Tool` annotation. Spring AI handles all the steps for you. It sends the schemas, the model returns tool calls, the auto registered `ToolCallingAdvisor` runs them and sends the results back, and the model gives the final answer. You do not write any of this loop yourself.

To see these steps in the logs, turn on debug logging for Spring AI. Append the following lines to `src/main/resources/application.properties`.

```properties

logging.level.org.springframework.ai=debug
```

Restart the application in Terminal 1 with `Ctrl+C` and `./mvnw spring-boot:run`, so it picks up the new logging level.

## 9. Try It Out

Ask the assistant to file a ticket.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Open a high priority ticket to request a trial for VMware Tanzu Spring"
```

The response confirms that the ticket was created. The logs in Terminal 1 show the `createTicket` call with the arguments that the model chose.

Then list the open tickets through the assistant.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Provide me an overview of all open support tickets"
```

The model picks `retrieveOpenTickets`, gets the rows from the database, and returns them in the response.

Finally, combine this with RAG. Ask a question that needs both an answer and an action.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Does VMware Tanzu Spring provide support for Spring Boot 2.7? If yes, open a ticket to request a trial"
```

The advisor pulls the answer from the knowledge base. If the model decides that a ticket is needed, the tool call files it.

## Recap

The assistant can now act on the world, grounded by RAG and driven by `@Tool` methods that the `ToolCallingAdvisor` runs for you. The [`sample-app/`](../../03-production-ready-features/01-testing/sample-app/) of the testing lab contains the result of this lab. Next you make the whole application testable and observable.
