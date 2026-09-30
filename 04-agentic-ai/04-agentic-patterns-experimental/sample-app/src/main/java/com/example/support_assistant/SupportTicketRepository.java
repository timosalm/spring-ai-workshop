package com.example.support_assistant;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
interface SupportTicketRepository extends ListCrudRepository<SupportTicket, Long> {
    List<SupportTicket> findByStatus(String status);
    List<SupportTicket> findByCategory(String category);
}