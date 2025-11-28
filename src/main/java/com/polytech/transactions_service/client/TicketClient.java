package com.polytech.transactions_service.client;

import com.polytech.transactions_service.dto.TicketDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

// "ticket-service" est le nom enregistré dans Eureka
@FeignClient(name = "tickets-service")
public interface TicketClient {
    
    // Récupérer les infos d'un ticket (Prix, Statut)
    // NOTE: Vous devez vous assurer que TicketController a bien un @GetMapping("/{id}")
    @GetMapping("/tickets/{id}")
    TicketDto getTicketById(@PathVariable("id") UUID ticketId);

    // Marquer un ticket comme vendu
    @PostMapping("/tickets/{id}/buy")
    void markTicketAsSold(@PathVariable("id") UUID ticketId);

    @PostMapping("/tickets/{id}/reserve")
    void reserveTicket(@PathVariable("id") UUID ticketId);

    @PostMapping("/tickets/{id}/release")
    void releaseTicket(@PathVariable("id") UUID ticketId);
}