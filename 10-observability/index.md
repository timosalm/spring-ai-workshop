---
title: Observability (Optional)
---

# Observability

{{< note >}}
This is an **optional advanced module**. You can skip to the workshop summary if needed.
{{< /note >}}

In this module, you'll learn how to monitor your Spring AI applications in production.

## Why Observability Matters for AI

AI applications have unique monitoring needs:

| Concern | Why It Matters |
|---------|---------------|
| **Token Usage** | Directly impacts costs |
| **Latency** | AI calls can be slow |
| **Error Rates** | Rate limits, timeouts |
| **Quality** | Are responses accurate? |

## Spring Boot Actuator

Spring AI integrates with Spring Boot Actuator for metrics:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

## AI-Specific Metrics

Spring AI exposes metrics via Micrometer:

| Metric | Description |
|--------|-------------|
| `gen_ai.client.token.usage` | Token counts (input/output) |
| `gen_ai.client.operation.duration` | Call latency |

## Viewing Metrics

With the application running, you can check metrics:

```execute
curl -s localhost:8080/actuator/metrics | jq '.names | map(select(startswith("gen_ai")))'
```

## Token Usage Tracking

Track token usage to manage costs:

```execute
curl -s localhost:8080/actuator/metrics/gen_ai.client.token.usage | jq
```

This shows:
- `prompt_tokens` - Input tokens sent
- `completion_tokens` - Output tokens received

## Calculating Costs

With token counts, estimate costs:

```
Cost = (prompt_tokens × input_price) + (completion_tokens × output_price)

Example (GPT-4o):
Input: 1000 tokens × $0.0000025 = $0.0025
Output: 500 tokens × $0.00001 = $0.005
Total: $0.0075
```

## Production Monitoring Stack

For production, consider:

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Spring AI  │────▶│ Prometheus  │────▶│   Grafana   │
│   Metrics   │     │  (Scrape)   │     │ (Dashboard) │
└─────────────┘     └─────────────┘     └─────────────┘
```

## Key Dashboards

Build dashboards showing:

1. **Token Usage Over Time** - Trend of consumption
2. **Cost Estimation** - Based on provider pricing
3. **Latency Percentiles** - P50, P95, P99
4. **Error Rates** - By error type
5. **Requests per Second** - Load patterns

## Best Practices

1. **Set Token Limits**: Prevent runaway costs
   ```yaml
   spring.ai.openai.chat.options.max-tokens: 1000
   ```

2. **Implement Caching**: Cache repeated queries
3. **Use Cheaper Models**: Route simple queries to cheaper models
4. **Alert on Anomalies**: Set up alerts for unusual patterns

## Summary

Observability for AI applications requires tracking:
- Token usage (cost)
- Latency (performance)
- Error rates (reliability)
- Response quality (effectiveness)

Spring AI's Micrometer integration makes this straightforward!
