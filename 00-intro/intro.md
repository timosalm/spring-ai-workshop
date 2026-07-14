Artificial intelligence (AI) has become an essential part of modern applications. While AI covers many different techniques, most of today's attention is on Generative AI (GenAI), driven by recent advances in large language models (LLMs).

Python has traditionally been the go-to language for adding AI capabilities to applications. For Java developers, however, the Spring AI project offers a compelling alternative: it lets you build enterprise-grade AI applications with familiar tools while keeping pace with a rapidly evolving AI landscape.

## What is Spring AI?

Spring AI is a framework that brings AI capabilities to the Java and Spring ecosystem. It abstracts away the complexity of working with different AI providers, such as OpenAI, Anthropic, Microsoft, Google, Amazon, and even locally running LLMs. Because it is model-agnostic, you can switch between models with minimal effort, and, as usual in Spring, you still have access to the features and configuration options of each specific model.
The framework can automatically convert AI model output into Java objects, giving you type safety across your application, and it provides other fundamental features like multimodality, AI-related observability, and testing support for evaluating model responses.

Beyond the basics, Spring AI supports more advanced patterns for providing context to LLMs, such as Tool Calling, Retrieval-Augmented Generation (RAG), and the Model Context Protocol (MCP).

Spring AI also embraces **agentic AI patterns**, in which models reason, plan, and act over multiple steps rather than producing a single response. The project keeps adding support for emerging patterns, such as LLM-as-judge evaluation (using a model to assess the quality of another model's output) and Tool Search (where the model discovers available tools on demand instead of receiving all definitions upfront, keeping the context window lean). Applied AI is evolving fast, and Spring AI's active development helps Java developers stay at the cutting edge.

Spring AI builds on the core concepts of the Spring Framework and integrates with other Spring projects, like Spring Data for working with vector databases. With Spring Boot's autoconfiguration, you can develop AI-powered features faster and with less boilerplate.

## What You Will Learn

This course gives you everything you need to build your first AI-enabled application or agent. You will start with the fundamentals and key concepts of Generative AI and then see how Spring AI puts them into practice, so you have a solid foundation before writing a single line of code.

In the **hands-on labs**, you will build an AI-powered support assistant step by step, applying each concept to a realistic application that can answer questions from documentation, create support tickets, and remember the conversation across turns.

Let's get started with the core fundamentals of Generative AI that everything else builds on.