package com.polytech.transactions_service.model.enums;

// --- Fichier 1: TransactionStatus.java ---
public enum TransactionStatus {
    PENDING,    // Créée, en attente de paiement
    COMPLETED,  // Payée et validée
    FAILED,     // Échec du paiement
    CANCELED,   // Annulée par l'utilisateur
    REFUNDED    // Remboursée
}