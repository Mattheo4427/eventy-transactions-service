package com.polytech.transactions_service.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class CreateTransactionRequest {
    private UUID ticketId;
    // On pourrait ajouter ici paymentMethod si on voulait le choisir au début
}