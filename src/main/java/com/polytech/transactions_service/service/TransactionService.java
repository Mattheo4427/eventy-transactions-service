package com.polytech.transactions_service.service;

import com.polytech.transactions_service.client.TicketClient;
import com.polytech.transactions_service.client.UserClient;
import com.polytech.transactions_service.dto.TicketDto;
import com.polytech.transactions_service.event.PaymentValidatedEvent;
import com.polytech.transactions_service.event.TicketSoldEvent;
import com.polytech.transactions_service.event.TransactionRefundedEvent;
import com.polytech.transactions_service.model.Transaction;
import com.polytech.transactions_service.model.enums.PaymentStatus;
import com.polytech.transactions_service.model.enums.TransactionStatus;
import com.polytech.transactions_service.repository.TransactionRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TicketClient ticketClient;
    private final UserClient userClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${stripe.api-key}")
    private String stripeApiKey;

    private static final double PLATFORM_FEE_PERCENTAGE = 0.05;

    /**
     * Étape 1: Initialiser la transaction et créer un PaymentIntent Stripe.
     * Retourne la transaction enrichie avec le clientSecret de Stripe.
     */
    @Transactional
    public Transaction createTransaction(String buyerId, UUID ticketId) {
        // Initialisation de Stripe avec la clé secrète
        Stripe.apiKey = stripeApiKey;

        // 1. Vérification du Ticket
        TicketDto ticket;
        try {
            ticket = ticketClient.getTicketById(ticketId);
        } catch (FeignException.NotFound e) {
            throw new IllegalArgumentException("Ticket not found: " + ticketId);
        }

        // 2. VERROUILLAGE (Réservation)
        // On tente de réserver le ticket immédiatement.
        // Si le ticket est déjà vendu ou réservé, TicketService renverra une erreur (409 ou 400),
        // ce qui fera échouer cette méthode et empêchera la création du paiement.
        try {
            ticketClient.reserveTicket(ticketId);
        } catch (FeignException e) {
            throw new IllegalStateException("Le ticket n'est plus disponible.");
        }
        /*
        if (!"AVAILABLE".equalsIgnoreCase(ticket.getStatus())) {
            throw new IllegalStateException("Ticket is not available for sale (Status: " + ticket.getStatus() + ")");
        }
        */
        // 2. Calculs Financiers
        double totalAmount = ticket.getSalePrice();
        double fees = Math.round(totalAmount * PLATFORM_FEE_PERCENTAGE * 100.0) / 100.0;
        double vendorNet = totalAmount - fees;
        // 3. Création de l'objet Transaction (PENDING)
        Transaction transaction = Transaction.builder()
                .buyerId(UUID.fromString(buyerId))
                .ticketId(ticketId)
                .vendorId(ticket.getVendorId())
                .totalAmount(totalAmount)
                .platformFee(fees)
                .vendorAmount(vendorNet)
                .status(TransactionStatus.PENDING)
                .paymentStatus(PaymentStatus.UNPAID)
                .transactionDate(LocalDateTime.now())
                .build();

        try {
            // 4. Appel à Stripe pour créer le PaymentIntent
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount((long) (totalAmount * 100)) // Stripe utilise les centimes (long)
                    .setCurrency("eur")
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
                    )
                    // Metadonnées utiles pour retrouver la transaction plus tard (webhook)
                    .putMetadata("transactionId", transaction.getId() != null ? transaction.getId().toString() : "new")
                    .putMetadata("ticketId", ticketId.toString())
                    .putMetadata("buyerId", buyerId)
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            // 5. Sauvegarde du token Stripe et du clientSecret (temporairement dans un champ ou renvoyé)
            // Note: Le clientSecret n'est pas stocké en BDD pour des raisons de sécu, mais renvoyé au front.
            // On stocke l'ID du PaymentIntent pour pouvoir vérifier le statut plus tard.
            transaction.setPaymentToken(paymentIntent.getId());

            // Astuce : On peut stocker le clientSecret dans un champ transient ou l'envoyer via un DTO dédié.
            // Ici, pour simplifier, on suppose que l'entité Transaction a un champ @Transient ou qu'on le retourne autrement.
            // Si votre modèle Transaction n'a pas de champ pour le clientSecret, vous devriez retourner un DTO composite.
            // Pour l'instant, on va supposer que le contrôleur gère l'envoi du secret, ou on l'ajoute au modèle.

            // Sauvegarde en base
            transaction = transactionRepository.save(transaction);

            // On "hacke" l'objet retourné pour inclure le clientSecret (nécessaire pour le front)
            // Idéalement, utilisez un TransactionResponseDto.
            // Ici, on va utiliser une méthode utilitaire ou un champ transient si vous l'ajoutez au modèle.
            // Pour l'exemple, je vais supposer que vous l'ajoutez au DTO de réponse dans le contrôleur
            // ou que vous l'ajoutez comme propriété temporaire.

            // Pour que votre code fonctionne directement, je vais retourner la transaction telle quelle,
            // mais il faudra récupérer le clientSecret depuis paymentIntent dans le contrôleur.
            return transaction; // Le contrôleur devra enrichir la réponse avec intent.getClientSecret()

        } catch (StripeException e) {
            ticketClient.releaseTicket(ticketId);
            log.error("Erreur Stripe lors de l'initialisation du paiement", e);
            throw new RuntimeException("Erreur de paiement: " + e.getMessage());
        }
    }

    /**
     * Méthode utilitaire pour récupérer le clientSecret d'une transaction en cours (si besoin de le renvoyer)
     */
    public String getStripeClientSecret(String paymentIntentId) {
        Stripe.apiKey = stripeApiKey;
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            return intent.getClientSecret();
        } catch (StripeException e) {
            throw new RuntimeException("Impossible de récupérer le secret Stripe", e);
        }
    }

    /**
     * Étape 2: Confirmation du paiement (Appelé par le front après succès Stripe ou par Webhook)
     */
    @Transactional
    public Transaction completeTransaction(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction introuvable"));

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            // Idempotence : si déjà complété, on renvoie juste l'objet
            if (transaction.getStatus() == TransactionStatus.COMPLETED) return transaction;
            throw new IllegalStateException("Statut invalide pour validation: " + transaction.getStatus());
        }

        // Vérification optionnelle auprès de Stripe pour être sûr que c'est payé
        // verifyStripePayment(transaction.getPaymentToken());
        TicketDto ticket = null;
        try {
            ticket = ticketClient.getTicketById(transaction.getTicketId());
        } catch (Exception e) {
            log.error("Impossible de récupérer le ticket {} pour la transaction {}", transaction.getTicketId(), transactionId);
            // On continue, mais le vendorId sera manquant (ou on throw pour annuler)
        }
        // 1. Mise à jour statut
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setPaymentStatus(PaymentStatus.PAID);
        transaction.setValidationDate(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(transaction);
        /*
        // 2. Changement de statut du ticket (Appel Synchrone)
        try {
            ticketClient.markTicketAsSold(transaction.getTicketId());
        } catch (Exception e) {
            log.error("Erreur lors du marquage du ticket comme VENDU. Transaction ID: " + transactionId, e);
            // On ne rollback pas la transaction financière, mais on log l'erreur critique
        }
        */
        // 3. Événements Kafka (Asynchrone) pour les autres services
        TicketSoldEvent soldEvent = new TicketSoldEvent(
                transaction.getTicketId(),
                transaction.getId(),
                transaction.getBuyerId()
        );
        kafkaTemplate.send("ticket-sold", soldEvent);

        PaymentValidatedEvent paymentEvent = PaymentValidatedEvent.builder()
                .transactionId(transaction.getId())
                .buyerId(transaction.getBuyerId())
                // Note: vendorId n'est pas stocké dans Transaction actuellement, il faudrait le récupérer du Ticket
                // ou l'ajouter au modèle Transaction lors de l'initiation.
                .vendorAmount(transaction.getVendorAmount())
                .amount(transaction.getTotalAmount())
                .vendorId(ticket.getVendorId())
                .build();
        kafkaTemplate.send("payment-validated", paymentEvent);

        return savedTransaction;
    }


    @Transactional
    public void cancelTransaction(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction introuvable"));

        if (transaction.getStatus() == TransactionStatus.PENDING) {
            // Par défaut, on considère que c'est une annulation utilisateur
            TransactionStatus finalStatus = TransactionStatus.CANCELED;

            // Vérification "Intelligente" via Stripe
            if (transaction.getPaymentToken() != null) {
                try {
                    Stripe.apiKey = stripeApiKey;
                    PaymentIntent intent = PaymentIntent.retrieve(transaction.getPaymentToken());

                    // Si l'objet PaymentIntent contient une erreur, c'est un ECHEC bancaire
                    if (intent.getLastPaymentError() != null) {
                        finalStatus = TransactionStatus.FAILED;
                        log.info("Transaction {} marquée FAILED (Erreur Stripe détectée: {})",
                                transactionId, intent.getLastPaymentError().getMessage());
                    }
                } catch (Exception e) {
                    log.warn("Impossible de vérifier le statut Stripe pour la transaction {}", transactionId, e);
                }
            }

            // 1. Mise à jour du statut (CANCELED ou FAILED selon l'analyse)
            transaction.setStatus(finalStatus);
            if (finalStatus == TransactionStatus.FAILED) {
                transaction.setPaymentStatus(PaymentStatus.UNPAID); // ou FAILED si dispo
            }
            transactionRepository.save(transaction);

            // 2. Libération du ticket
            try {
                ticketClient.releaseTicket(transaction.getTicketId());
                log.info("Ticket {} libéré.", transaction.getTicketId());
            } catch (Exception e) {
                log.error("Erreur non-bloquante libération ticket {}", transaction.getTicketId(), e);
            }
        }
    }

    @Transactional
    public void failTransaction(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction introuvable"));

        if (transaction.getStatus() == TransactionStatus.PENDING) {
            // 1. Statut FAILED
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setPaymentStatus(PaymentStatus.UNPAID); // Ou FAILED si vous l'avez dans l'enum
            transactionRepository.save(transaction);

            // 2. Libération du ticket (Même logique que l'annulation)
            try {
                ticketClient.releaseTicket(transaction.getTicketId());
                log.info("Ticket {} libéré suite à l'échec de paiement {}", transaction.getTicketId(), transactionId);
            } catch (Exception e) {
                log.error("Erreur non-bloquante libération ticket {}", transaction.getTicketId(), e);
            }
        }
    }

    @Transactional
    public void refundTransaction(UUID transactionId) {
        // 1. Récupérer la transaction
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction introuvable"));

        // Vérifier qu'elle est bien complétée
        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new IllegalStateException("Seules les transactions complétées peuvent être remboursées.");
        }

        // 2. Récupérer les infos du ticket (pour avoir le vendorId qui n'est pas dans Transaction)
        TicketDto ticket;
        try {
            ticket = ticketClient.getTicketById(transaction.getTicketId());
        } catch (Exception e) {
            throw new RuntimeException("Impossible de récupérer les infos du ticket pour le remboursement.");
        }

        // 3. Appel STRIPE (Remboursement réel de l'acheteur)
        try {
            Stripe.apiKey = stripeApiKey;
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(transaction.getPaymentToken()) // ID du paiement Stripe
                    .build();
            Refund refund = Refund.create(params);

            if (!"succeeded".equals(refund.getStatus())) {
                throw new RuntimeException("Le remboursement Stripe a échoué : " + refund.getStatus());
            }
        } catch (StripeException e) {
            log.error("Erreur Stripe Refund", e);
            throw new RuntimeException("Erreur Stripe : " + e.getMessage());
        }

        // 4. Mise à jour locale
        transaction.setStatus(TransactionStatus.REFUNDED);
        transaction.setPaymentStatus(PaymentStatus.REFUNDED);
        transactionRepository.save(transaction);

        // 5. Événement Kafka (Pour débiter le vendeur et annuler le ticket)
        TransactionRefundedEvent event = TransactionRefundedEvent.builder()
                .transactionId(transaction.getId())
                .ticketId(transaction.getTicketId())
                .vendorId(ticket.getVendorId())
                .vendorAmount(transaction.getVendorAmount())
                .build();

        kafkaTemplate.send("transaction-refunded", event);
        log.info("Transaction {} remboursée et événement Kafka envoyé.", transactionId);
    }

    public List<Transaction> getUserHistory(UUID userId) {
        return transactionRepository.findByBuyerId(userId);
    }

    public List<Transaction> getUserSales(UUID userId) {
        return transactionRepository.findByVendorId(userId);
    }

    public Transaction getTransaction(UUID id) {
        return transactionRepository.findById(id).orElseThrow();
    }

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    @Transactional
    public void backfillVendorIds() {
        List<Transaction> transactions = transactionRepository.findAll();
        for (Transaction tx : transactions) {
            if (tx.getVendorId() == null) {
                try {
                    TicketDto ticket = ticketClient.getTicketById(tx.getTicketId());
                    if (ticket != null && ticket.getVendorId() != null) {
                        tx.setVendorId(ticket.getVendorId());
                        transactionRepository.save(tx);
                        log.info("Backfilled vendorId for transaction {}", tx.getId());
                    }
                } catch (Exception e) {
                    log.error("Failed to backfill vendorId for transaction {}", tx.getId(), e);
                }
            }
        }
    }
}