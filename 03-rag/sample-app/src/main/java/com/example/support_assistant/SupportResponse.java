package com.example.support_assistant;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

record SupportResponse(
        @JsonPropertyDescription("The category of the support question: TECHNICAL, BILLING, SECURITY, UPGRADE, or GENERAL")
        Category category,

        @JsonPropertyDescription("The helpful answer to the customer's question")
        String answer
) {
    enum Category {
        TECHNICAL,
        BILLING,
        SECURITY,
        UPGRADE,
        GENERAL
    }
}
