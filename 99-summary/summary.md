# Summary

Congratulations on completing the **Spring AI Introduction Course**!

## What You Built

Throughout this course you built a complete AI powered **support assistant** for VMware Tanzu Spring, one lab at a time.

- It answers questions through a versioned REST API with the fluent `ChatClient`, a system prompt from a file, and structured output into a Java record.
- Advisors log every request and keep the conversation history, so the assistant can follow up on earlier questions.
- RAG grounds the answers in your own Markdown documents, with the general knowledge of the model as a fallback.
- Tool calling lets the assistant create and list support tickets in a database.
- Tests check the answers with semantic assertions and with an LLM as judge.
- Metrics, token usage, and traces show what the assistant does, locally in Grafana.
- An MCP client connects the assistant to a separate Spring Releases MCP server, which can be secured with OAuth 2.0 and Keycloak.
- The Tool Search Tool keeps the prompt small, because the model only sees the tools that fit the request.
- Experimental patterns add a judge that rates every answer, an Agent Skill with its own script, a plan that the model writes down and works off, and questions to the user when information is missing.

## The Finished Applications

This folder contains the final state of all projects.

- [`sample-app/`](sample-app/) is the support assistant at the end of the agentic patterns lab.
- [`sample-app-experimental/`](sample-app-experimental/) is the support assistant at the end of the experimental agentic patterns lab.
- [`spring-releases-mcp-server/`](spring-releases-mcp-server/) is the Spring Releases MCP server that both assistants connect to.

Start the MCP server first, then one of the assistants, each in its own terminal.

```bash
cd spring-releases-mcp-server
./mvnw spring-boot:run
```

```bash
cd sample-app
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

Then ask a question.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=What is the latest stable release of Spring AI? Please also open a high-priority ticket to request help upgrading our application to that version."
```

Two optional profiles switch on the heavier parts, and both need Docker.

- `local-observability` on the assistant exports metrics and traces into a local Grafana stack at [http://localhost:3000](http://localhost:3000).
- `mcp-security` on the MCP server **and** on the assistant secures the MCP connection with OAuth 2.0 and starts Keycloak. The MCP lab shows how to sign in with `curl`.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local-observability
```

## Resources

- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Spring AI GitHub](https://github.com/spring-projects/spring-ai)
- [Spring AI Community](https://github.com/spring-ai-community)
