package com.example.support_assistant;

import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("support_ticket")
record SupportTicket(@Nullable @Id Long id, String summary, SupportCategory category, Priority priority,
                     Status status, LocalDateTime createdAt) {

    @PersistenceCreator
    SupportTicket { }

    SupportTicket(String summary, SupportCategory category, Priority priority) {
        this(null, summary, category, priority, Status.OPEN, LocalDateTime.now());
    }

    SupportTicket withId(Long id) {
        return new SupportTicket(id, summary, category, priority, status, createdAt);
    }

    enum Status {
        OPEN, IN_PROGRESS, CLOSED
    }

    enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}