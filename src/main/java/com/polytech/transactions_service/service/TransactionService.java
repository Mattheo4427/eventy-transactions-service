package com.polytech.transactions_service.service;

import com.polytech.transactions_service.client.TicketClient;
import com.polytech.transactions_service.client.dto.TicketDto;
import com.polytech.transactions_service.model.Transaction;
import com.polytech.transactions_service.model.enums.PaymentStatus;
import com.polytech.transactions_service.model.enums.TransactionStatus;
import com.polytech.transactions_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TicketClient ticketClient;

    // Frais de plateforme fixés à 5%
    private static final double PLATFORM_FEE_PERCENTAGE = 0.05;

    /**
     * Étape 1: L'utilisateur clique sur "Acheter".
     * On vérifie le ticket, on calcule le prix total et on crée une transaction "PENDING".
     */
    public Transaction initiateTransaction(String buyerId, UUID ticketId) {
        // 1. Appel Synchrone au Ticket Service via Feign
        TicketDto ticket = ticketClient.getTicketById(ticketId);

        // 2. Vérification disponibilité
        if (!"AVAILABLE".equals(ticket.getStatus())) {
            throw new IllegalStateException("Ce ticket n'est pas disponible à la vente.");
        }
        
        // 3. Calculs financiers
        double originalPrice = ticket.getSalePrice();
        double fees = Math.round(originalPrice * PLATFORM_FEE_PERCENTAGE * 100.0) / 100.0; // Arrondi 2 décimales
        double total = originalPrice + fees;

        // 4. Création de l'objet Transaction
        Transaction transaction = Transaction.builder()
                .buyerId(buyerId)
                .ticketId(ticketId)
                .totalAmount(total)
                .platformFee(fees)
                .vendorAmount(originalPrice)
                .status(TransactionStatus.PENDING)
                .paymentStatus(PaymentStatus.UNPAID)
                .build();

        return transactionRepository.save(transaction);
    }

    /**
     * Étape 2: Le paiement est confirmé (par le front ou un webhook Stripe).
     * On finalise la transaction et on verrouille le ticket.
     */
    @Transactional
    public Transaction completeTransaction(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction introuvable"));

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new IllegalStateException("Cette transaction ne peut pas être complétée (Statut actuel: " + transaction.getStatus() + ")");
        }

        // 1. Mise à jour statut Transaction
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setPaymentStatus(PaymentStatus.PAID);
        transaction.setValidationDate(LocalDateTime.now());
        
        // 2. Appel au Ticket Service pour changer le statut du ticket en SOLD
        // C'est crucial pour éviter qu'il soit acheté par quelqu'un d'autre
        ticketClient.markTicketAsSold(transaction.getTicketId());

        return transactionRepository.save(transaction);
    }

    public List<Transaction> getUserHistory(String userId) {
        return transactionRepository.findByBuyerId(userId);
    }
    
    public Transaction getTransaction(UUID id) {
        return transactionRepository.findById(id).orElseThrow();
    }
}