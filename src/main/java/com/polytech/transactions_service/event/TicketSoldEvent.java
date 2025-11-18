package com.polytech.transactions_service.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketSoldEvent {
    private UUID ticketId;
    private UUID transactionId;
    private String buyerId;
}