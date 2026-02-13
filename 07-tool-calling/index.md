---
title: Tool Calling
---

In this section, you'll build the **tools** module that connects the AI to external systems using function/tool calling.

## Learning Objectives

- Understand how tool calling works
- Create tools using the `@Tool` annotation
- Let the AI decide when to use tools
- Persist tool results to a database

## How Tool Calling Works

Tool calling (also known as function calling) allows the AI to interact with external systems. Instead of just generating text, the AI can request to execute specific functions when needed.

The flow works like this:

1. You define tools (functions) with descriptions the AI can understand
2. User sends a message to the AI
3. AI analyzes the request and decides if a tool is needed
4. If yes, AI returns a structured tool call request (not text)
5. Your application executes the tool and returns the result
6. AI receives the result and generates the final response

```
User: "What time is it in Tokyo?"
    ↓
AI: "I need to call getCurrentDateTime(timezone='Asia/Tokyo')"
    ↓
Your code: Executes the function → "2024-01-15 14:30:00 JST"
    ↓
AI: "The current time in Tokyo is 2:30 PM JST."
```

This pattern enables AI assistants to perform real actions like querying databases, calling APIs, or creating records.

## Create a Simple Date/Time Tool

Let's start with a simple tool to understand the basics. This tool returns the current date and time in a specified timezone.

Create the tools module directory:

```terminal:execute
command: mkdir -p ~/sample-app/src/main/java/com/example/supportassistant/tools
session: 1
description: Create a DateTimeTool
cascade: true
```

```editor:append-lines-to-file
file: ~/sample-app/src/main/java/com/example/supportassistant/tools/DateTimeTool.java
hidden: true
text: |
  package com.example.supportassistant.tools;

  import org.springframework.ai.tool.annotation.Tool;
  import org.springframework.ai.tool.annotation.ToolParam;
  import org.springframework.stereotype.Component;

  import java.time.ZoneId;
  import java.time.ZonedDateTime;
  import java.time.format.DateTimeFormatter;

  @Component
  public class DateTimeTool {

      @Tool(description = "Get the current date and time in a specific timezone. Use this when the user asks about the current time or date.")
      public String getCurrentDateTime(
              @ToolParam(description = "The timezone, e.g., 'America/New_York', 'Europe/London', 'Asia/Tokyo'")
              String timezone) {
          ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timezone));
          return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
      }
  }
```

Key annotations:
- `@Tool(description = "...")` - Marks a method as callable by the AI. The description helps the AI understand when to use this tool.
- `@ToolParam(description = "...")` - Describes each parameter so the AI knows what values to provide.

## Create a Support Ticket tool

For the support ticket  tool, we'll use Spring Data JDBC with an H2 in-memory database. Add the required dependencies:

```editor:select-matching-text
file: ~/sample-app/pom.xml
text: "</dependencies>"
description: Add Spring Data JDBC and H2 dependencies
before: 0
after: 0
cascade: true
```
```editor:replace-text-selection
file: ~/sample-app/pom.xml
hidden: true
text: |2
          <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-data-jdbc</artifactId>
          </dependency>

          <dependency>
              <groupId>com.h2database</groupId>
              <artifactId>h2</artifactId>
              <scope>runtime</scope>
          </dependency>
      </dependencies>
```

### Configure the Database

Add the H2 database configuration:

```editor:select-matching-text
file: ~/sample-app/src/main/resources/application.yaml
text: "spring:"
description: Add H2 database configuration
before: 0
after: 0
cascade: true
```
```editor:replace-text-selection
file: ~/sample-app/src/main/resources/application.yaml
hidden: true
text: |
  spring:
    datasource:
      url: jdbc:h2:mem:supportdb;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
      driver-class-name: org.h2.Driver
```

Create the database schema:

```editor:append-lines-to-file
file: ~/sample-app/src/main/resources/schema.sql
description: Create database schema for tickets
text: |
  CREATE TABLE IF NOT EXISTS support_ticket (
      id BIGINT AUTO_INCREMENT PRIMARY KEY,
      ticket_id VARCHAR(20) NOT NULL UNIQUE,
      summary VARCHAR(255) NOT NULL,
      category VARCHAR(50) NOT NULL,
      priority VARCHAR(20) NOT NULL,
      status VARCHAR(20) NOT NULL,
      created_at TIMESTAMP NOT NULL
  );
```

### Create the Ticket Entity

Create the entity class for support tickets:

```editor:append-lines-to-file
file: ~/sample-app/src/main/java/com/example/supportassistant/tools/SupportTicket.java
description: Create SupportTicket entity
text: |
  package com.example.supportassistant.tools;

  import org.springframework.data.annotation.Id;
  import org.springframework.data.relational.core.mapping.Table;

  import java.time.LocalDateTime;

  @Table("support_ticket")
  public record SupportTicket(
          @Id Long id,
          String ticketId,
          String summary,
          String category,
          String priority,
          String status,
          LocalDateTime createdAt
  ) {
      public SupportTicket(String ticketId, String summary, String category, String priority) {
          this(null, ticketId, summary, category, priority, "OPEN", LocalDateTime.now());
      }
  }
```

### Create the Ticket Repository

Create a Spring Data JDBC repository:

```editor:append-lines-to-file
file: ~/sample-app/src/main/java/com/example/supportassistant/tools/SupportTicketRepository.java
description: Create SupportTicketRepository
text: |
  package com.example.supportassistant.tools;

  import org.springframework.data.repository.CrudRepository;
  import org.springframework.stereotype.Repository;

  import java.util.List;
  import java.util.Optional;

  @Repository
  public interface SupportTicketRepository extends CrudRepository<SupportTicket, Long> {
      Optional<SupportTicket> findByTicketId(String ticketId);
      List<SupportTicket> findByStatus(String status);
      List<SupportTicket> findByCategory(String category);
  }
```

### Create the Ticket Creation Tool

Now create the tool that allows the AI to create and list support tickets.

```editor:append-lines-to-file
file: ~/sample-app/src/main/java/com/example/supportassistant/tools/TicketTool.java
description: Create TicketTool with database persistence
text: |
  package com.example.supportassistant.tools;

  import org.springframework.ai.tool.annotation.Tool;
  import org.springframework.ai.tool.annotation.ToolParam;
  import org.springframework.stereotype.Component;

  import java.util.List;
  import java.util.concurrent.atomic.AtomicInteger;

  @Component
  public class TicketTool {

      private final SupportTicketRepository ticketRepository;
      private final AtomicInteger ticketCounter = new AtomicInteger(1000);

      public TicketTool(SupportTicketRepository ticketRepository) {
          this.ticketRepository = ticketRepository;
      }

      @Tool(description = "Create a new support ticket. Use this when the user explicitly requests to create, open, or file a support ticket.")
      public TicketResult createTicket(
              @ToolParam(description = "Brief summary of the issue (max 100 chars)")
              String summary,

              @ToolParam(description = "Category: TECHNICAL, BILLING, SECURITY, UPGRADE, or GENERAL")
              String category,

              @ToolParam(description = "Priority: LOW, MEDIUM, HIGH, or CRITICAL")
              String priority) {

          String ticketId = "TSE-" + ticketCounter.incrementAndGet();

          SupportTicket ticket = new SupportTicket(ticketId, summary, category.toUpperCase(), priority.toUpperCase());
          SupportTicket saved = ticketRepository.save(ticket);

          return new TicketResult(
                  saved.ticketId(),
                  saved.summary(),
                  saved.category(),
                  saved.priority(),
                  saved.status(),
                  saved.createdAt().toString(),
                  "Ticket created successfully"
          );
      }

      @Tool(description = "List all open support tickets. Use this when the user wants to see their tickets or check ticket status.")
      public List<SupportTicket> listOpenTickets() {
          return ticketRepository.findByStatus("OPEN");
      }

      public record TicketResult(
              String ticketId,
              String summary,
              String category,
              String priority,
              String status,
              String createdAt,
              String message
      ) {}
  }
```

Key points:
- The tool uses constructor injection to get the `SupportTicketRepository`
- `createTicket` persists the ticket to the H2 database
- `listOpenTickets` allows the AI to query existing tickets
- The AI will use these tools based on the user's intent

### Create the Tools Controller

Create a controller that exposes endpoints using these tools:

```editor:append-lines-to-file
file: ~/sample-app/src/main/java/com/example/supportassistant/tools/ToolsController.java
description: Create ToolsController
text: |
  package com.example.supportassistant.tools;

  import org.springframework.ai.chat.client.ChatClient;
  import org.springframework.web.bind.annotation.*;

  import java.util.List;

  @RestController
  @RequestMapping("/tools")
  public class ToolsController {

      private final ChatClient chatClient;
      private final DateTimeTool dateTimeTool;
      private final TicketTool ticketTool;
      private final SupportTicketRepository ticketRepository;

      public ToolsController(
              ChatClient chatClient,
              DateTimeTool dateTimeTool,
              TicketTool ticketTool,
              SupportTicketRepository ticketRepository) {
          this.chatClient = chatClient;
          this.dateTimeTool = dateTimeTool;
          this.ticketTool = ticketTool;
          this.ticketRepository = ticketRepository;
      }

      @GetMapping("/chat")
      public String chatWithTools(@RequestParam String message) {
          return chatClient.prompt()
                  .system("""
                          You are the Support Assistant with access to tools.
                          Use the available tools when appropriate to help the user.
                          Execute tool calls directly without asking for confirmation.
                          Always be helpful and provide context with your answers.
                          """)
                  .user(message)
                  .tools(dateTimeTool, ticketTool)
                  .call()
                  .content();
      }

      @GetMapping("/tickets")
      public List<SupportTicket> getAllTickets() {
          return (List<SupportTicket>) ticketRepository.findAll();
      }
  }
```

The `.tools(dateTimeTool, ticketTool)` method registers the tools with the ChatClient. The AI will automatically:
- Analyze the user's message
- Decide if any tools are needed
- Call the appropriate tool with extracted parameters
- Use the tool's result to generate a response

## Test the Tools

Start the application:

```terminal:execute
command: cd ~/sample-app && ./mvnw spring-boot:run
session: 2
```

Test the date/time tool:

```execute
http -b "localhost:8080/tools/chat?message=What+time+is+it+in+Tokyo?"
```

Test ticket creation:

```execute
http -b "localhost:8080/tools/chat?message=Please+create+a+high+priority+technical+ticket+about+my+Spring+Boot+application+crashing+on+startup"
```

Verify tickets were persisted to the database:

```execute
http localhost:8080/tools/tickets
```

Test listing tickets through the AI:

```execute
http -b "localhost:8080/tools/chat?message=Show+me+my+open+tickets"
```

Test a conversation that uses multiple tools:

```execute
http -b "localhost:8080/tools/chat?message=What+time+is+it+in+London+and+please+create+a+low+priority+general+ticket+asking+about+documentation+updates"
```

## Understanding Tool Execution Flow

When you call the `/tools/chat` endpoint, here's what happens:

1. **User Request**: "Create a high priority technical ticket about my app crashing"
2. **AI Analysis**: AI recognizes this requires the `createTicket` tool
3. **Tool Call**: AI generates: `createTicket(summary="Application crashing on startup", category="TECHNICAL", priority="HIGH")`
4. **Execution**: Spring AI invokes your `@Tool` method with these parameters
5. **Database**: Ticket is persisted to H2 database
6. **Result**: Tool returns `TicketResult` with the ticket details
7. **Response**: AI generates a friendly response: "I've created ticket TSE-1001 for your application crash issue..."

## Stop the Application

```terminal:interrupt
session: 2
```

## Summary

You've built a tool-enabled AI assistant that can interact with a database:

| Tool | Purpose |
|------|---------|
| `DateTimeTool` | Get current time in any timezone |
| `TicketTool` | Create and list support tickets (persisted to database) |

**Key Concepts:**
- `@Tool` and `@ToolParam` annotations define callable functions with descriptions
- `.tools(...)` registers tools with the ChatClient
- AI autonomously decides when and how to call tools based on user intent
- Tools can interact with databases, APIs, and other external systems
- Tool results are seamlessly integrated into AI responses