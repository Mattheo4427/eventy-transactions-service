package com.polytech.transactions_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentValidatedEvent {
    private UUID transactionId;
    private UUID buyerId;
    private UUID vendorId;
    private Double amount;        // Montant total payé par l'acheteur
    private Double vendorAmount;  // Montant net à créditer au vendeur
}