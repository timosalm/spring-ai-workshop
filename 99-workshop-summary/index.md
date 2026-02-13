---
title: Workshop Summary
---

# Workshop Summary

Congratulations on completing the **Spring AI Advanced Workshop**!

## What You Built

Throughout this workshop, you built the **Support Assistant** - a comprehensive AI-powered application:

```
com.example.supportassistant/
├── chat/          ✅ Basic conversation with ChatClient
├── ticket/        ✅ Structured output parsing
├── knowledge/     ✅ RAG with vector search
├── tools/         ✅ Function/tool calling
├── mock/          (pre-built) Mock OpenAI service
└── config/        (pre-built) Configuration
```

## Key Concepts Covered

### Module 03: ChatClient Basics
- Fluent API for LLM interaction
- Blocking vs. streaming responses
- Response metadata and usage tracking

### Module 04: Prompt Engineering
- System prompts for behavior control
- Template files with placeholders
- Few-shot prompting for classification

### Module 05: Structured Output
- `BeanOutputConverter` for JSON parsing
- Java records as output schemas
- `@JsonPropertyDescription` for field hints

### Module 06: Embeddings & RAG
- Text embeddings for similarity search
- Vector stores for document indexing
- Context retrieval and augmentation

### Module 07: Tool Calling
- `@Tool` annotation for functions
- Parameter descriptions with `@ToolParam`
- AI-driven tool selection

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Support Assistant                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐  ┌───────────┐  │
│  │   Chat   │  │  Ticket  │  │ Knowledge │  │   Tools   │  │
│  │  Module  │  │  Module  │  │   Module  │  │   Module  │  │
│  └────┬─────┘  └────┬─────┘  └─────┬─────┘  └─────┬─────┘  │
│       │             │              │              │         │
│       └─────────────┴──────────────┴──────────────┘         │
│                          │                                   │
│                    ┌─────┴─────┐                            │
│                    │ ChatClient │                            │
│                    └─────┬─────┘                            │
│                          │                                   │
├──────────────────────────┼──────────────────────────────────┤
│                    ┌─────┴─────┐                            │
│                    │  OpenAI   │  ← Mock or Real            │
│                    │    API    │                            │
│                    └───────────┘                            │
└─────────────────────────────────────────────────────────────┘
```

## Production Considerations

### Moving Beyond Mock

For production, consider:
- Real OpenAI, Anthropic, or Azure OpenAI
- Self-hosted models via Ollama or vLLM
- Tanzu Platform for AI workloads

### Vector Store Options

Replace `SimpleVectorStore` with:
- PostgreSQL with pgvector
- Redis Vector Search
- Pinecone, Weaviate, Milvus

### Observability

Add monitoring for:
- Token usage and costs
- Response latency
- Error rates and types

## Continue Learning

### Optional Modules
- **08: MCP Integration** - Model Context Protocol
- **09: Observability** - Metrics and monitoring
- **10: Testing** - AI application testing

### Resources

**Documentation:**
- [Spring AI Reference](https://docs.spring.io/spring-ai/reference/)
- [Spring AI API Docs](https://docs.spring.io/spring-ai/docs/current/api/)

**Tanzu Resources:**
- [Tanzu Spring](https://tanzu.vmware.com/spring-enterprise)
- [Spring Office Hours](https://springofficehours.io)

**Community:**
- [Spring AI GitHub](https://github.com/spring-projects/spring-ai)
- [Spring Community Discord](https://discord.gg/spring)

## Thank You!

Thank you for participating in this workshop. We hope you're now equipped to build AI-powered applications with Spring AI!

{{< note >}}
🏆 *Congratulations* 🏆

👍 **You have completed the Spring AI Advanced Workshop!** 👍
{{< /note >}}
