package com.polytech.transactions_service.dto;
import lombok.Data;
import java.util.UUID;

@Data
public class UserDto {
    private UUID id;
    private String email;
    private String status; // Pour vérifier s'il n'est pas SUSPENDED
}