---
title: Prompt Engineering
---

In this module, you'll enhance the chat module with prompt engineering techniques: templates, system prompts, and message roles.

## Learning Objectives

- Use prompt templates with placeholders
- Configure system prompts to define assistant behavior
- Understand message roles (system, user, assistant)

## Message Roles

LLMs understand different message roles:

| Role | Purpose | Example |
|------|---------|---------|
| **System** | Sets behavior and context | "You are a helpful Spring expert" |
| **User** | The human's input | "How do I configure Spring Security?" |
| **Assistant** | Model's previous responses | Used for conversation history |

## Create a Prompt Template

First, let's create a prompt template file. Templates use `{placeholder}` syntax:

```terminal:execute
command: mkdir -p ~/sample-app/src/main/resources/prompts
session: 1
description: Add system prompt template file
cascade: true
```

```editor:append-lines-to-file
file: ~/sample-app/src/main/resources/prompts/support-system.st
hidden: true
text: |
  You are the Support Assistant, an AI-powered helper for Broadcom Tanzu Spring customers.

  Your responsibilities:
  - Answer questions about Tanzu Spring support offerings
  - Provide information about Spring Boot, Spring Framework, and Spring Cloud
  - Help with CVE and security-related inquiries
  - Assist with support ticket creation and management
  - Guide customers on billing and subscription questions

  Guidelines:
  - Be professional and helpful
  - If you don't know something, say so honestly
  - For urgent production issues, recommend creating a P1 support ticket
  - Always mention relevant documentation links when helpful

  Current context:
  - Customer tier: {customerTier}
```

## Create ChatService with System Prompt

Let's create a service that uses this system prompt:

```editor:append-lines-to-file
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatService.java
description: Add ChatService with system prompt
text: |
  package com.example.supportassistant.chat;

  import org.springframework.ai.chat.client.ChatClient;
  import org.springframework.ai.chat.prompt.PromptTemplate;
  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.core.io.Resource;
  import org.springframework.stereotype.Service;

  import java.util.Map;

  @Service
  public class ChatService {

      private final ChatClient chatClient;

      @Value("classpath:/prompts/support-system.st")
      private Resource systemPromptResource;

      public ChatService(ChatClient chatClient) {
          this.chatClient = chatClient;
      }

      public String chat(String userQuery, String customerTier) {
          return chatClient.prompt()
                  .system(sys -> sys
                        .text(systemPromptResource)
                        .param("customerTier", customerTier))
                  .user(userQuery)
                  .call()
                  .content();
      }
  }
```

Key points:
- `@Value("classpath:/prompts/...")` loads the template file
- `.system(Consumer<PromptSystemSpec>)` sets the system prompt and replaces placeholders with values

Internally, the `ChatClient` uses the `PromptTemplate` class to handle the user and system text and replace the variables with the values provided at runtime relying on a given TemplateRenderer implementation.


## Add Endpoint Using the Service

Update the ChatController to use the new service.

```editor:select-matching-text
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatController.java
text: "public ChatController"
description: Update ChatController to use the new service
before: 0
after: 0
cascade: true
```

```editor:replace-text-selection
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatController.java
cascade: true
hidden: true
text: |2
      private final ChatService chatService;

      public ChatController(ChatClient chatClient, ChatService chatService) {
          this.chatService = chatService;
```

```editor:insert-lines-before-line
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatController.java
line: 53
hidden: true
text: |2

      @GetMapping("/support")
      public String supportChat(
              @RequestParam String query,
              @RequestParam(defaultValue = "Standard") String tier) {

          return chatService.chat(query, tier);
      }
```

## Test the Support Endpoint

Start the application:

```terminal:execute
command: cd ~/sample-app && ./mvnw spring-boot:run
session: 2
```

Test with different contexts:

```execute
http -b localhost:8080/chat/support query=="What CVEs are covered?" tier=="Premium"
```

```execute
http -b localhost:8080/chat/support query=="How do I upgrade to Spring Boot 3?" tier=="Standard"
```

## Few-Shot Prompting

There are various prompt engineering techniques available to improve LLM results, such as zero-shot, few-shot, chain-of-thought, and self-consistency prompting. For classification tasks, few-shot prompting provides examples. Let's create a topic classifier:

```editor:append-lines-to-file
file: ~/sample-app/src/main/resources/prompts/topic-classifier.st
description: Add few-shot prompting example
text: |
  Add a classification for the query to the answer based on these categories:
  - TECHNICAL: Questions about code, configuration, or implementation
  - BILLING: Questions about invoices, payments, or subscriptions
  - SECURITY: Questions about CVEs, vulnerabilities, or patches
  - UPGRADE: Questions about version upgrades or migrations
  - GENERAL: Other questions

  Examples:
  Query: "My Spring Boot app won't start after adding a new dependency"
  Category: TECHNICAL
  Answer: "Please provide me the stack trace"

  Query: "When will I receive my invoice for Q4?"
  Category: BILLING
  Answer: "You will receive your invoice in the second week of Q1"

  Query: "Is there a patch for the latest Log4j vulnerability?"
  Category: SECURITY
  Answer: "Yes, there is a patch available!"

  Query: "How do I migrate from Spring Boot 2.7 to 3.2?"
  Category: UPGRADE
  Answer: "For commercial customers a solution called Spring Application Advisor is available"
```

Configure the classifier system prompt:

```editor:insert-lines-before-line
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatService.java
line: 18
description: Configure classifier system prompt
cascade: true
text: |2

      @Value("classpath:/prompts/topic-classifier.st")
      private Resource classifierPrompt;
```
```editor:insert-lines-before-line
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatService.java
line: 31
hidden: true
text: |2
                  .system(classifierPrompt)
```


Restart and test:

```terminal:interrupt
session: 2
```

```terminal:execute
command: cd ~/sample-app && ./mvnw spring-boot:run
session: 2
```

```execute
http -b localhost:8080/chat/support query=="What CVEs are covered?" tier=="Premium"
```

```execute
http -b localhost:8080/chat/support query=="How do I upgrade to Spring Boot 3?" tier=="Standard"
```

## Stop the Application

```terminal:interrupt
session: 2
```

## Summary

You've learned key prompt engineering techniques:

| Technique | Purpose | Example |
|-----------|---------|---------|
| **System Prompt** | Define assistant behavior | "You are the Support Assistant..." |
| **Templates** | Reusable prompts with placeholders | `{customerTier}` |
| **Roles** | Structure the conversation | system, user, assistant |
| **Prompt Engineering** | Design effective prompts to guide model outputs | Few-Shot Prompting classification example |

Next, you'll learn to parse AI responses into structured Java objects!

