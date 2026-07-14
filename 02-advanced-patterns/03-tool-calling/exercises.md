# Tool Calling — Hands-on Exercises

Your starting point is the [`sample-app/`](sample-app/), the RAG assistant from the previous lab: a `ChatClient` with a default system prompt, a `QuestionAnswerAdvisor` in the chain, and a Markdown knowledge base indexed at startup.

In this lab you teach the model to **act**, not just answer. With Spring AI's tool calling the assistant can file new support tickets and read existing ones from a database. The model decides when to call each tool, the framework runs it and feeds the result back.

## 1. Add the persistence dependencies

Tickets need to be stored somewhere. Use Spring Data JDBC plus the in-memory H2 database. Add to `pom.xml`:

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

## 2. Configure the datasource

Append to `application.properties`:

```properties
spring.datasource.url=jdbc:h2:mem:supportdb;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
spring.datasource.driver-class-name=org.h2.Driver
```

The two H2 flags make column matching case-insensitive, so snake_case column names match the camelCase record components Spring Data infers.

## 3. Create the schema

Spring Boot runs `schema.sql` automatically on startup. Create `src/main/resources/schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS support_ticket (
      id BIGINT AUTO_INCREMENT PRIMARY KEY,
      summary VARCHAR(255) NOT NULL,
      category VARCHAR(50) NOT NULL,
      priority VARCHAR(20) NOT NULL,
      status VARCHAR(20) NOT NULL,
      created_at TIMESTAMP NOT NULL);
```

## 4. The `SupportTicket` entity

Create `SupportTicket.java`:

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

- **`@PersistenceCreator`** on the canonical constructor tells Spring Data to use the 6-arg constructor when reading rows.
- The **convenience constructor** is what tool calls use — the model only supplies `summary`, `category`, and `priority`; `id` is `null` (the DB generates it), `status` starts `OPEN`, `createdAt` is now.
- **`withId(...)`** is called after the INSERT to put the generated id back.
- `category` reuses the `SupportCategory` enum from `SupportResponse`.

## 5. The repository

Create `SupportTicketRepository.java`:

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

`ListCrudRepository` gives you `save`, `findAll`, `findById`, and more. The two derived queries cover the lookups you'll expose as tools.

## 6. The tool service

Add the `@Tool` annotation to methods and Spring AI does four things for you: builds a JSON schema from the signature, sends it to the model, runs the method when the model asks, and feeds the result back.

Create `SupportTicketService.java`:

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

- **`@Tool(description = "...")`** is the most important text in the step. The model reads it to decide whether to call the tool, so say clearly *when* it should be used.
- **`@ToolParam(description = "...")`** describes each parameter so the model fills the arguments correctly. Spring AI turns enum types into the choices the model can pick from.

## 7. Register the tools on the `ChatClient`

Inject `SupportTicketService` into `SupportAssistantService` — add the field and update the constructor:

```java
private final VectorStore vectorStore;
private final SupportTicketService supportTicketService;

SupportAssistantService(ChatClient chatClient, VectorStore vectorStore, SupportTicketService supportTicketService) {
    this.chatClient = chatClient;
    this.vectorStore = vectorStore;
    this.supportTicketService = supportTicketService;
}
```

Register the tools on the call, next to the RAG advisor — add `.tools(supportTicketService)` right after `.advisors(ragAdvisor)` in `generateResponse`:

```java
    .advisors(ragAdvisor)
    .tools(supportTicketService)
```

`.tools(Object)` registers every `@Tool`-annotated method on the bean. Spring AI's auto-registered `ToolCallingAdvisor` runs the whole request-execute-respond loop for you.

To watch the steps in the logs, enable Spring AI debug logging in `application.properties`:

```properties
logging.level.org.springframework.ai=debug
```

## 8. Try it out

File a ticket:

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Open a high priority ticket to request a trial for VMware Tanzu Spring"
```

The response confirms the ticket was created; the debug logs show the `createTicket` call with the arguments the model chose.

List the open tickets through the assistant:

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Provide me an overview of all open support tickets"
```

Finally, combine RAG and tools in one turn:

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Does VMware Tanzu Spring provide support for Spring Boot 2.7? If yes, open a ticket to request a trial"
```

The advisor pulls the answer from the knowledge base, and if the model decides a ticket is warranted, the tool call files it.

## Recap

The assistant can now act on the world, grounded by RAG and driven by `@Tool` methods that the `ToolCallingAdvisor` orchestrates. Next you make the whole thing testable and observable.
