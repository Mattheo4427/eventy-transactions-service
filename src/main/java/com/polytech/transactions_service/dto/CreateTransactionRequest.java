package com.polytech.transactions_service.dto;

import com.polytech.transactions_service.model.enums.PaymentMethod;
import lombok.Data;
import java.util.UUID;

@Data
public class CreateTransactionRequest {
    private UUID ticketId;
    private UUID userId;
    private double amount;
    private PaymentMethod paymentMethod;
    private UUID buyerId;
    // On pourrait ajouter ici paymentMethod si on voulait le choisir au début
}