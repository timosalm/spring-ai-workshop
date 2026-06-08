Artificial intelligence (AI) is becoming increasingly essential to modern applications. While AI encompasses many different techniques, the current industry focuses on Generative AI (GenAI) due to the latest advancements in large language models (LLMs).

Traditionally, Python has been the dominant language for integrating AI capabilities into applications. However, for Java developers adopting Generative AI, the Spring AI project offers an attractive solution that enables the seamless development of enterprise-grade applications while keeping pace with the rapidly evolving AI landscape.

## What is Spring AI?

Spring AI abstracts complex interactions with various AI providers providing REST APIs, such as OpenAI, Anthropic, Microsoft, Google, Amazon, and even local LLMs. Its model-agnostic nature allows for easy switching between models, and, as usual in Spring, you still have access to functionalities and configurations unique to a particular model.
The framework automatically converts AI model output into Java objects, ensuring type safety across your application and provides other fundamental features like multimodality, AI-related observability, and model response evaluation testing.

Additionally, Spring AI covers more advanced AI patterns, such as Tool Calling, Retrieval-Augmented Generation (RAG), and the Model Context Protocol (MCP), to provide context to LLMs.

Spring AI also addresses **agentic AI patterns**, in which models reason, plan, and act over multiple steps rather than producing a single response. The project continuously adds support for emerging patterns such as LLM-as-judge evaluation (using a model to assess the quality of another model's output) and the Tool Search pattern (where the model discovers available tools on-demand rather than receiving all definitions upfront, keeping the context window lean). These patterns reflect the rapidly evolving state of applied AI, and Spring AI's active development ensures Java developers stay at the cutting edge.

Spring AI is built upon the core building blocks of the Spring Framework and other Spring projects like Spring Data for integrating vector databases. Spring Boot simplifies and speeds up the development of AI-powered features through autoconfiguration.


Spring AI is a framework that brings AI capabilities to the Java and Spring ecosystem. It provides a **consistent, portable programming model** across AI providers — the same code works with OpenAI, Anthropic, Google Gemini, Ollama, and others.

## What You Will Learn

This course gives you everything you need to build your first AI-enabled application or agent. You will start with Generative AI fundamentals and key concepts, then see how Spring AI puts those concepts into practice, so you have a solid foundation before writing a single line of code.

In the **hands-on labs**, you will incrementally build a support assistant for VMware Tanzu Spring. You will apply each concept to a realistic application that can answer questions from documentation, create support tickets, and maintain conversation history across turns.

The following section starts with the core fundamentals of Generative AI that everything else builds on.