package com.polytech.transactions_service.dto;

import lombok.Data;
import java.util.UUID;

// Ce DTO représente les infos minimales d'un ticket dont nous avons besoin
@Data
public class TicketDto {
    private UUID id;
    private UUID eventId;
    private Double salePrice;
    private String status; // "AVAILABLE", "SOLD", etc.
    private UUID vendorId;
}