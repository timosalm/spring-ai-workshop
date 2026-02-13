package com.example.supportassistant.mock;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Registry of predefined responses for the mock OpenAI service.
 * Provides deterministic, Tanzu-themed responses for workshop exercises.
 */
@Profile("mock")
@Component
public class MockResponseRegistry {

    private final List<ResponsePattern> patterns = new ArrayList<>();

    public MockResponseRegistry() {
        initializePatterns();
    }

    private void initializePatterns() {
        // Tanzu Spring - General
        patterns.add(new ResponsePattern(
            Pattern.compile("(?i).*(what is|about|tell me).*tanzu.*spring.*(enterprise|runtime).*"),
            """
            Tanzu Spring is Broadcom's commercial offering that provides enterprise-grade support \
            for Spring applications. It includes:

            - **24/7 Production Support**: Round-the-clock assistance from Spring experts
            - **CVE Patches**: Priority access to security patches, often before public release
            - **Long-term Support (LTS)**: Extended support for Spring Boot versions beyond community EOL
            - **Spring Health Assessment**: Expert analysis of your Spring applications
            - **Tanzu Spring Runtime**: Curated, tested Spring dependencies

            With Tanzu Spring, organizations get peace of mind knowing their Spring applications \
            are backed by the creators of the Spring Framework."""
        ));

        // Support offerings
        patterns.add(new ResponsePattern(
            Pattern.compile("(?i).*(support|help|assistance).*tanzu.*spring.*"),
            """
            Tanzu Spring support includes:

            1. **Technical Support**: 24/7 access to Spring experts via support portal
            2. **CVE Response**: Priority notification and patches for security vulnerabilities
            3. **Upgrade Guidance**: Expert assistance with Spring Boot upgrades
            4. **Performance Tuning**: Help optimizing your Spring applications
            5. **Architecture Review**: Best practices consultation

            Support tickets are categorized by severity:
            - **Critical (P1)**: Production down, immediate response
            - **High (P2)**: Major functionality impacted, 4-hour response
            - **Medium (P3)**: Non-critical issues, 8-hour response
            - **Low (P4)**: General questions, 24-hour response"""
        ));

        // Billing questions
        patterns.add(new ResponsePattern(
            Pattern.compile("(?i).*(billing|invoice|payment|cost|price|subscription).*"),
            """
            I understand you have a billing-related question. Here's what I can help with:

            - **Subscription Status**: Check your current Tanzu Spring subscription
            - **Invoice History**: View past invoices in the customer portal
            - **Payment Methods**: Update payment information securely
            - **Plan Upgrades**: Learn about additional support tiers

            For specific billing inquiries, I recommend creating a support ticket with category \
            'BILLING' so our finance team can assist you directly."""
        ));

        // CVE / Security
        patterns.add(new ResponsePattern(
            Pattern.compile("(?i).*(cve|security|vulnerabilit|patch).*"),
            """
            Tanzu Spring provides comprehensive CVE (Common Vulnerabilities and Exposures) coverage:

            **What's Included:**
            - Priority notification of security vulnerabilities affecting Spring
            - Patches often available before public disclosure
            - Backported security fixes for LTS versions
            - Security advisories with remediation guidance

            **Recent CVE Examples:**
            - CVE-2024-38816: Spring Framework path traversal - Patched in 5.3.39, 6.0.23, 6.1.12
            - CVE-2024-38809: Spring Framework DoS vulnerability - Patched in 6.1.12

            Enterprise customers receive these patches through the Tanzu Spring Runtime distribution."""
        ));

        // Spring Boot versions
        patterns.add(new ResponsePattern(
            Pattern.compile("(?i).*(latest|current|version|release).*spring.*boot.*"),
            """
            Here are the current Spring Boot versions:

            **Latest Releases:**
            - Spring Boot 3.4.1 (Current GA) - Released January 2025
            - Spring Boot 3.3.7 (Maintenance) - LTS through Tanzu Spring
            - Spring Boot 3.2.12 (Maintenance)

            **Tanzu Spring Runtime Versions:**
            Enterprise customers have access to curated, tested distributions with extended support.

            **Upgrade Recommendation:**
            If you're on Spring Boot 2.x, we strongly recommend planning your upgrade to 3.x. \
            Tanzu Spring includes upgrade assistance and compatibility testing."""
        ));

        // Technical - Spring Cloud
        patterns.add(new ResponsePattern(
            Pattern.compile("(?i).*spring.*cloud.*"),
            """
            Spring Cloud provides tools for building distributed systems and microservices:

            **Key Components:**
            - **Spring Cloud Config**: Centralized configuration management
            - **Spring Cloud Netflix**: Service discovery, circuit breakers (Eureka, Hystrix legacy)
            - **Spring Cloud Gateway**: API gateway with routing and filtering
            - **Spring Cloud Sleuth/Micrometer**: Distributed tracing
            - **Spring Cloud Stream**: Event-driven microservices with Kafka/RabbitMQ

            **Tanzu Spring Coverage:**
            All Spring Cloud components are covered under enterprise support, including \
            assistance with architecture decisions and troubleshooting distributed systems issues."""
        ));

        // Greeting / Who are you
        patterns.add(new ResponsePattern(
            Pattern.compile("(?i)^(hi|hello|hey|greetings).*|.*(who are you|introduce yourself).*"),
            """
            Hello! I'm the Support Assistant, an AI-powered helper for Broadcom Tanzu Spring customers.

            I can help you with:
            - Questions about Tanzu Spring features and support
            - Technical guidance on Spring Boot and Spring Cloud
            - Information about CVE patches and security updates
            - Support ticket creation and management
            - Subscription and billing inquiries

            How can I assist you today?"""
        ));

        // Tool calling simulation - Weather (demo)
        patterns.add(new ResponsePattern(
            Pattern.compile("(?i).*(weather|temperature|forecast).*"),
            "TOOL_CALL:get_weather"
        ));

        // Tool calling simulation - Time
        patterns.add(new ResponsePattern(
            Pattern.compile("(?i).*(what time|current time|what's the time|time is it).*"),
            "TOOL_CALL:get_current_time"
        ));

        // Tool calling simulation - Create ticket
        patterns.add(new ResponsePattern(
            Pattern.compile("(?i).*(create|open|submit|file).*(ticket|case|issue).*"),
            "TOOL_CALL:create_ticket"
        ));

        // Tool calling simulation - Search/Latest info
        patterns.add(new ResponsePattern(
            Pattern.compile("(?i).*(search|look up|find|latest news|recent).*"),
            "TOOL_CALL:web_search"
        ));
    }

    /**
     * Find the best matching response for the given prompt.
     */
    public String findResponse(String prompt) {
        for (ResponsePattern pattern : patterns) {
            if (pattern.pattern().matcher(prompt).find()) {
                return pattern.response();
            }
        }
        // Default fallback response
        return getDefaultResponse();
    }

    /**
     * Check if the prompt should trigger a tool call.
     */
    public boolean isToolCall(String prompt) {
        String response = findResponse(prompt);
        return response.startsWith("TOOL_CALL:");
    }

    /**
     * Get the tool name for a tool call response.
     */
    public String getToolName(String response) {
        if (response.startsWith("TOOL_CALL:")) {
            return response.substring("TOOL_CALL:".length());
        }
        return null;
    }

    /**
     * Get a simulated tool result.
     */
    public String getToolResult(String toolName, String arguments) {
        return switch (toolName) {
            case "get_weather" -> """
                {"location": "San Francisco", "temperature": "18°C", "condition": "Partly cloudy", "humidity": "65%"}""";
            case "get_current_time" -> String.format(
                "{\"datetime\": \"%s\", \"timezone\": \"UTC\"}",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );
            case "create_ticket" -> """
                {"ticket_id": "TSE-12345", "status": "CREATED", "message": "Support ticket created successfully"}""";
            case "web_search" -> """
                {"results": [
                    {"title": "Spring Boot 3.4.1 Released", "url": "https://spring.io/blog/2025/01/spring-boot-3-4-1"},
                    {"title": "Spring Framework 6.2 GA", "url": "https://spring.io/blog/2024/11/spring-framework-6-2"}
                ]}""";
            default -> "{\"error\": \"Unknown tool\"}";
        };
    }

    public String getDefaultResponse() {
        return """
            I'm the Support Assistant, here to help with your Tanzu Spring questions.

            I can assist with:
            - Tanzu Spring features and support options
            - Spring Boot and Spring Cloud technical questions
            - CVE patches and security updates
            - Support ticket management
            - Billing and subscription inquiries

            Please let me know how I can help you today!""";
    }

    /**
     * Generate deterministic embedding vector based on text hash.
     * Uses the text's hash to create a reproducible 1536-dimensional vector.
     */
    public float[] generateEmbedding(String text) {
        float[] embedding = new float[1536];
        int hash = text.hashCode();
        Random random = new Random(hash);

        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (random.nextFloat() * 2) - 1; // Range: -1 to 1
        }

        // Normalize the vector
        float magnitude = 0;
        for (float v : embedding) {
            magnitude += v * v;
        }
        magnitude = (float) Math.sqrt(magnitude);

        for (int i = 0; i < embedding.length; i++) {
            embedding[i] /= magnitude;
        }

        return embedding;
    }

    private record ResponsePattern(Pattern pattern, String response) {}
}
