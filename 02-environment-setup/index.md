---
title: Environment Setup
---

# Environment Setup

Let's verify your environment is ready and understand how the AI provider is configured.

## Project Structure

First, let's explore the starter project:

```terminal:execute
command: cd ~/sample-app && find . -type f -name "*.java" | head -20
session: 1
```

## AI Provider Options

This workshop supports multiple AI providers:

| Provider | Description | Cost | Profile | Configuration |
|----------|-------------|------|---------|---------------|
| **Mock** (default) | Built-in mock service | Free | `mock` | No API key needed |
| **OpenAI** | Real GPT-4 models | API costs | `openai` | Requires `OPENAI_API_KEY` |

### Understanding the Mock Service

The mock provider is enabled by default, providing deterministic responses perfect for learning.

```editor:select-matching-text
file: ~/sample-app/src/main/resources/application.yaml
text: "active:"
```

The mock service:
- Runs at `http://localhost:8080/mock/v1`
- Uses pattern matching to provide contextual, deterministic responses
- Simulates OpenAI's API, so the code you write works identically with real OpenAI

### Switching to Real OpenAI (Optional)

If you have an OpenAI API key, you can switch to the real API:

1. Set the environment variable:
```terminal:input
text: export OPENAI_API_KEY=
endl: false
session: 2
```

2. Change the active profile:
```terminal:execute
command: export SPRING_PROFILES_ACTIVE=openai
session: 2
```

{{< warning >}}
Using real OpenAI will incur API costs. The mock service is recommended for learning.
{{< /warning >}}

### Validate the AI Provider Configuration

Before we start building, let's run a test to verify the AI provider is correctly configured.

```terminal:execute
command: cd ~/sample-app && ./mvnw test -Dtest=AiProviderConfigurationTest -q
session: 2
```

{{< note >}}
The test should pass and show "AI Provider Configuration Test - SUCCESS" with the desired active profile.
{{< /note >}}

Expected output:
```
=================================================
AI Provider Configuration Test - SUCCESS
Active Profile: <mock or openai if configured>
Response received: Tanzu Spring is ...
=================================================
```

## Summary

You've verified that the AI provider (mock or openai) is properly configured

Now, you can start building your first AI-enabled application!