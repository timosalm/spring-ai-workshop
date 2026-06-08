# Spring AI Support Assistant — Tool Calling Walkthrough

Continues from `INSTRUCTIONS-2.md`. Your starting point is a RAG-backed Support Assistant: `ChatClient` with a default system prompt, a `QuestionAnswerAdvisor` plugged into the chain, and a Markdown knowledge base indexed at startup.

In this part you'll teach the model to **act**, not just answer. Using Spring AI's tool-calling support, the assistant will be able to file new support tickets and read existing ones from a relational database. The LLM decides when to call each tool based on the user's request, runs it, and feeds the result back into the response.

## 1. Add the persistence dependencies

You need Spring Data JDBC to persist tickets, plus a database driver.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jdbc</artifactId>
</dependency>
```

### Default: H2 in-memory

If you're **not** using pgvector for the knowledge base, add H2 as a runtime driver:

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
```

### PostgreSQL attendees

If you already added pgvector in INSTRUCTIONS-2, you already have the PostgreSQL driver and a running database via Docker Compose. **Skip the H2 dependency** — `spring-boot-starter-data-jdbc` will pick up the existing `DataSource`.

## 2. Configure the datasource (H2 only)

Append to `application.properties`:

```properties
spring.datasource.url=jdbc:h2:mem:supportdb;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
spring.datasource.driver-class-name=org.h2.Driver
```

The two H2 flags make column matching case-insensitive so the snake-case column names match the camelCase record components Spring Data infers.

PostgreSQL users already have their datasource wired through `spring.docker.compose.*` from INSTRUCTIONS-2 — nothing to add here.

## 3. Create the schema

Create `src/main/resources/schema.sql`. Spring Boot runs it automatically against the configured datasource on startup.

### H2

```sql
CREATE TABLE IF NOT EXISTS support_ticket (
      id BIGINT AUTO_INCREMENT PRIMARY KEY,
      summary VARCHAR(255) NOT NULL,
      category VARCHAR(50) NOT NULL,
      priority VARCHAR(20) NOT NULL,
      status VARCHAR(20) NOT NULL,
      created_at TIMESTAMP NOT NULL);
```

### PostgreSQL

```sql
CREATE TABLE IF NOT EXISTS support_ticket (
      id BIGSERIAL PRIMARY KEY,
      summary VARCHAR(255) NOT NULL,
      category VARCHAR(50) NOT NULL,
      priority VARCHAR(20) NOT NULL,
      status VARCHAR(20) NOT NULL,
      created_at TIMESTAMP NOT NULL);
```

Both columns use the same names — only the auto-increment syntax differs.

## 4. The `SupportTicket` entity

Create `src/main/java/com/example/support_assistant/SupportTicket.java`:

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

A few things worth pointing out:

- **`@PersistenceCreator`** on the compact canonical constructor — disambiguates the two constructors so Spring Data definitely uses the 6-arg one when reading rows.
- **Convenience constructor** for tool calls — the LLM only needs to supply `summary`, `category`, `priority`. `id` is `null` (DB-generated), `status` defaults to `OPEN`, `createdAt` to now.
- **`withId(...)` wither** — Spring Data calls it after the INSERT to thread the DB-generated id back into a new record instance.
- **Nested enums** for `Status` and `Priority` — they're tightly coupled to the ticket, so they live with it.

## 5. The repository

Create `src/main/java/com/example/support_assistant/SupportTicketRepository.java`:

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

`ListCrudRepository` gives us `save`, `findAll`, `findById`, etc. The two derived methods cover the queries we want to expose as tools.

## 6. The tool-bearing service

This is where Spring AI's tool calling shows up. Annotate methods with `@Tool` and the framework will:

1. Generate a JSON schema describing the tool from the method signature.
2. Pass those schemas to the model with each call.
3. When the model emits a tool call, invoke the method with the model-supplied arguments.
4. Feed the return value back into the model so it can finish its answer.

Create `src/main/java/com/example/support_assistant/SupportTicketService.java`:

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

Two annotations do the heavy lifting:

- **`@Tool(description = "...")`** — what the tool does, in the model's voice. This is the most important text in the whole step. The model decides whether to call the tool based on it, so be explicit about the trigger (here: "explicitly requests to create, open, or file a support ticket").
- **`@ToolParam(description = "...")`** — describes each parameter. The model uses these to fill the arguments correctly. Enum types are turned into the model's available choices automatically.

## 7. Register the tools on the `ChatClient` chain

Inject `SupportTicketService` into `SupportAssistantService` and pass it to `.tools(...)`:

```java
private final SupportTicketService supportTicketService;

SupportAssistantService(ChatClient chatClient, VectorStore vectorStore, SupportTicketService supportTicketService) {
    this.chatClient = chatClient;
    this.vectorStore = vectorStore;
    this.supportTicketService = supportTicketService;
}
```

And in `generateResponse`, add `.tools(supportTicketService)` to the chain:

```java
return chatClient.prompt()
        .user(u -> u
                .text("Answer the following question with a short, well-structured explanation: {question}")
                .param("question", query))
        .advisors(ragAdvisor)
        .tools(supportTicketService)
        .call()
        .entity(SupportResponse.class);
```

`.tools(Object)` registers every `@Tool`-annotated method on the bean. Spring AI handles the multi-turn dance — emit schemas → model returns tool calls → execute → feed results back → final answer — transparently.

## 8. Try it out

Restart and ask the assistant to file a ticket:

```bash
curl -G "http://localhost:8080/api/1.0/chat" \
     --data-urlencode "query=Please open a ticket: Trial request for Tanzu Spring. Treat it as high priority."
```

You should see a response confirming the ticket was created. Enabling `logging.level.org.springframework.ai=debug` shows the `createTicket` call with the model's chosen arguments.

Then list the open tickets through the assistant:

```bash
curl -G "http://localhost:8080/api/1.0/chat" \
     --data-urlencode "query=Show me all open support tickets."
```å

The model picks `retrieveOpenTickets`, gets the rows from the DB, and returns them as part of the response.

Mix it with RAG by asking something that's both informational and operational:

```bash
curl -G "http://localhost:8080/api/1.0/chat" \
     --data-urlencode "query=Does Tanzu Spring provide support for Spring Boot 2.7? If yes, open a ticket to request a trial of Tanzu Spring"
```

The advisor pulls the SLA from the knowledge base; the tool call (if the model judges it warranted) files the ticket.

---

## Recap

| Step | What changed |
|------|--------------|
| 1 | Add `spring-boot-starter-data-jdbc` (+ H2 driver if not using pgvector's Postgres) |
| 2 | Configure H2 datasource (Postgres attendees: nothing to add) |
| 3 | `schema.sql` creates `support_ticket` (H2 vs Postgres only differ on auto-increment syntax) |
| 4 | `SupportTicket` record with nested enums, `@PersistenceCreator`, and `withId(...)` |
| 5 | `SupportTicketRepository extends ListCrudRepository<SupportTicket, Long>` |
| 6 | `SupportTicketService` with `@Tool` / `@ToolParam` methods |
| 7 | `.tools(supportTicketService)` on the `ChatClient` chain |