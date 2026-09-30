package com.example.support_assistant;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class SupportTicketService {

    private final SupportTicketRepository ticketRepository;

    SupportTicketService(SupportTicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Tool(description = "Create a new support ticket. Use this when the user explicitly requests to create, open, or file a support ticket.")
    SupportTicket createTicket(
            @ToolParam(description = "Brief summary of the issue (max 100 chars)") String summary,
            @ToolParam(description = "The category of the issue") SupportCategory category,
            @ToolParam(description = "The priority of the support ticket") SupportTicket.Priority priority) {
        var ticket = new SupportTicket(summary, category, priority);
        return ticketRepository.save(ticket);
    }

    @Tool(description = "List all support tickets")
    List<SupportTicket> retrieveTickets() {
        return ticketRepository.findAll();
    }

    @Tool(description = "List all support tickets that are not yet resolved")
    List<SupportTicket> retrieveOpenTickets() {
        return ticketRepository.findByStatus("OPEN");
    }
}