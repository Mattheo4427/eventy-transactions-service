package com.polytech.transactions_service.service;

import com.polytech.transactions_service.client.TicketClient;
import com.polytech.transactions_service.client.UserClient;
import com.polytech.transactions_service.client.dto.TicketDto;
import com.polytech.transactions_service.dto.CreateTransactionRequest;
import com.polytech.transactions_service.dto.UserDto;
import com.polytech.transactions_service.event.PaymentValidatedEvent;
import com.polytech.transactions_service.event.TicketSoldEvent;
import com.polytech.transactions_service.model.Transaction;
import com.polytech.transactions_service.model.enums.PaymentStatus;
import com.polytech.transactions_service.model.enums.TransactionStatus;
import com.polytech.transactions_service.repository.TransactionRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TicketClient ticketClient;
    private final UserClient userClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Frais de plateforme fixés à 5%
    private static final double PLATFORM_FEE_PERCENTAGE = 0.05;




    @Transactional
    public Transaction createTransaction(CreateTransactionRequest request) {
        // 1. VÉRIFICATION ACHETEUR (Appel Synchrone User Service)
        try {
            UserDto buyer = userClient.getUserById(request.getUserId());
            // Vérification optionnelle du statut utilisateur
            // if ("SUSPENDED".equals(buyer.getStatus())) throw ...
        } catch (FeignException.NotFound e) {
            throw new IllegalArgumentException("Buyer not found: " + request.getUserId());
        }

        // 2. VÉRIFICATION BILLET (Appel Synchrone Ticket Service)
        TicketDto ticket;
        try {
            ticket = ticketClient.getTicketById(request.getTicketId());
        } catch (FeignException.NotFound e) {
            throw new IllegalArgumentException("Ticket not found: " + request.getTicketId());
        }

        // 3. VALIDATION MÉTIER
        // Le billet est-il disponible ?
        if (!"AVAILABLE".equalsIgnoreCase(ticket.getStatus())) {
            throw new IllegalStateException("Ticket is not available for sale (Status: " + ticket.getStatus() + ")");
        }

        // Le prix correspond-il ? (Sécurité anti-triche)
        // Note: request.getAmount() doit correspondre au prix de vente du billet
        if (ticket.getSalePrice() != null && request.getAmount() < ticket.getSalePrice()) {
            throw new IllegalArgumentException("Transaction amount mismatch");
        }

        // 4. CRÉATION TRANSACTION (PENDING)
        Transaction transaction = Transaction.builder()
                .buyerId(String.valueOf(request.getUserId()))
                .ticketId(request.getTicketId())
                .totalAmount(request.getAmount())
                .platformFee(request.getAmount() * PLATFORM_FEE_PERCENTAGE) // Ex: 5% frais
                .vendorAmount(request.getAmount() * (1 - PLATFORM_FEE_PERCENTAGE))
                .status(TransactionStatus.PENDING)
                .transactionDate(LocalDateTime.now())
                .paymentMethod(request.getPaymentMethod())
                .build();

        // ICI : Appeler un service de paiement externe (Stripe/PayPal)
        // Pour le MVP, on simule le succès immédiat
        transaction.setPaymentStatus(PaymentStatus.PAID);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setValidationDate(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(transaction);

        // 5. APPEL KAFKA (Asynchrone)
        TicketSoldEvent event = new TicketSoldEvent(ticket.getId(),transaction.getId() ,request.getUserId());
        kafkaTemplate.send("ticket-sold", event);

        PaymentValidatedEvent paymentEvent = PaymentValidatedEvent.builder()
                .transactionId(transaction.getId())
                .buyerId(request.getUserId())
                .vendorId(ticket.getVendorId())
                .amount(request.getAmount())
                .vendorAmount(savedTransaction.getVendorAmount())
                .build();

        kafkaTemplate.send("payment-validated", paymentEvent);
        return savedTransaction;
    }
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