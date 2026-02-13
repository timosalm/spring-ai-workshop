---
title: Structured Output
---

# Structured Output

In this module, you'll enhance the **chat** module to return structured JSON responses with both the answer and a category classification.

## Learning Objectives

- Understand how Spring AI handles output format instructions
- Use the `.entity()` method to parse responses into Java objects

## The Problem

AI models return text, but applications often need structured data:

```
Input:  "What CVEs are covered by my Premium subscription?"
Output: SupportChatResponse(category=SECURITY, answer="As a Premium customer, you have access to...")
```

## Define the Data Model

First, create an enum for the response categories in the chat package:

```editor:append-lines-to-file
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatResponseCategory.java
description: Add ChatResponseCategory enum
text: |
  package com.example.supportassistant.chat;

  public enum ChatResponseCategory {
      TECHNICAL,
      BILLING,
      SECURITY,
      UPGRADE,
      GENERAL
  }
```

Now create the structured response record:

```editor:append-lines-to-file
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/SupportChatResponse.java
description: Add SupportChatResponse record
text: |
  package com.example.supportassistant.chat;

  import com.fasterxml.jackson.annotation.JsonPropertyDescription;

  public record SupportChatResponse(
          @JsonPropertyDescription("The category of the support question: TECHNICAL, BILLING, SECURITY, UPGRADE, or GENERAL")
          ChatResponseCategory category,

          @JsonPropertyDescription("The helpful answer to the customer's question")
          String answer
  ) {}
```

The `@JsonPropertyDescription` annotations help the AI understand what each field should contain.

## Update the ChatService

Let's add a method to ChatService that returns structured output using the `.entity()` method:

```editor:select-matching-text
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatService.java
text: ".content()"
description: Add structured output conversion
cascade: true
```
```editor:replace-text-selection
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatService.java
hidden: true
text: .entity(SupportChatResponse.class)
cascade: true
```
```editor:select-matching-text
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatService.java
text: "public String chat"
hidden: true
cascade: true
```
```editor:replace-text-selection
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatService.java
hidden: true
text: public SupportChatResponse chat
```

Key points:
- The `.entity(SupportChatResponse.class)` method automatically handles JSON schema generation and parsing
- In the background, Spring AI adds format instructions to the prompt based on the record structure
- The `@JsonPropertyDescription` annotations provide additional context for the AI

## Update the endpoint return type

Update the ChatController to return the structured data

```editor:select-matching-text
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatController.java
text: "String supportChat"
description: Change return type to SupportChatResponse 
cascade: true
```
```editor:replace-text-selection
file: ~/sample-app/src/main/java/com/example/supportassistant/chat/ChatController.java
hidden: true
text: SupportChatResponse supportChat
```

## Test the Structured Endpoint

Start the application:

```terminal:execute
command: cd ~/sample-app && ./mvnw spring-boot:run
session: 2
```

Test with a security question:

```execute
http -b localhost:8080/chat/support query=="What CVEs are covered?" tier=="Premium"
```

Expected output:
```json
{
  "category": "SECURITY",
  "answer": "As a Premium customer, you have access to patches and updates for all CVEs..."
}
```

## Stop the Application

```terminal:interrupt
session: 2
```

## Summary

You've enhanced the chat module with structured output.

**Key Concepts:**
- The `.entity(Class)` method converts AI responses directly into Java objects
- `@JsonPropertyDescription` provides hints to the AI about field contents

Next, you'll learn about embeddings and build a RAG system to answer questions from documentation!
