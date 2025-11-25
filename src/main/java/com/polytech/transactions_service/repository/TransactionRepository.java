package com.polytech.transactions_service.repository;

import com.polytech.transactions_service.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    // Historique des achats d'un utilisateur
    List<Transaction> findByBuyerId(UUID buyerId);
    
    // Retrouver la transaction liée à un ticket spécifique
    List<Transaction> findByTicketId(UUID ticketId);
}