# Introduction

Artificial intelligence (AI) has become an essential part of modern applications. AI covers many different techniques, but most of the attention today goes to Generative AI (GenAI), driven by the recent progress in large language models (LLMs).

Python has long been the first choice for adding AI capabilities to an application. For Java developers the Spring AI project offers a compelling alternative. It lets you build enterprise grade AI applications with the tools you already know, while keeping pace with a field that changes quickly.

## What is Spring AI?

Spring AI is a framework that brings AI capabilities to the Java and Spring ecosystem. It hides the complexity of working with different AI providers such as OpenAI, Anthropic, Microsoft, Google, Amazon, and models you run yourself. Because it is model agnostic, you can switch between models with little effort, and, as usual in Spring, you still reach the features and configuration options of each specific model.

The framework can turn model output into Java objects for you, which gives you type safety across your application. It also provides other fundamental features such as multimodality, observability for AI calls, and testing support for evaluating model responses.

Beyond the basics, Spring AI supports more advanced ways of giving context to a model, such as tool calling, Retrieval Augmented Generation (RAG), and the Model Context Protocol (MCP).

Spring AI also embraces **agentic AI patterns**, where a model reasons, plans, and acts over several steps instead of producing a single response. The project keeps adding support for new patterns, such as LLM as a judge evaluation, where one model rates the quality of another model's output, and Tool Search, where the model discovers the available tools on demand instead of receiving every definition upfront, which keeps the context window lean. Applied AI moves fast, and the active development of Spring AI helps Java developers keep up.

Spring AI builds on the core concepts of the Spring Framework and works together with other Spring projects, such as Spring Data for vector databases. With the autoconfiguration of Spring Boot you can build AI powered features faster and with far less boilerplate.

## What You Will Learn

This course gives you everything you need to build your first AI enabled application or agent. You start with the fundamentals and key concepts of Generative AI and then see how Spring AI puts them into practice, so you have a solid foundation before you write a single line of code.

In the **hands-on labs** you build an AI powered support assistant step by step. Each concept is applied to a realistic application that answers questions from documentation, creates support tickets, and remembers the conversation across turns.

The next section starts with the core fundamentals of Generative AI that everything else builds on.
