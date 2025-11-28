package com.polytech.transactions_service.controller;

import com.polytech.transactions_service.dto.CreateTransactionRequest;
import com.polytech.transactions_service.model.Transaction;
import com.polytech.transactions_service.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Initier un achat.
     * Retourne la transaction ET le clientSecret Stripe nécessaire au front.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTransaction(
            @RequestBody CreateTransactionRequest request,
            @AuthenticationPrincipal Jwt principal) {

        String buyerId = principal.getSubject();

        // 1. Initier la transaction (Création en base + Création PaymentIntent Stripe)
        Transaction tx = transactionService.createTransaction(buyerId, request.getTicketId());

        // 2. Récupérer le clientSecret Stripe via le service
        // (Le PaymentIntent ID est stocké dans tx.getPaymentToken())
        String clientSecret = transactionService.getStripeClientSecret(tx.getPaymentToken());

        // 3. Construire la réponse composite
        Map<String, Object> response = new HashMap<>();
        response.put("transaction", tx);            // Données métier (montant, dates...)
        response.put("transactionId", tx.getId());  // ID pratique pour le front
        response.put("clientSecret", clientSecret); // LA clé pour Stripe React Native

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Confirmer un achat (Simule le retour positif du paiement).
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<Transaction> confirmTransaction(@PathVariable UUID id) {
        // Dans un vrai scénario, on vérifierait ici que l'appel vient d'un service de confiance (Webhook Stripe)
        // ou que l'utilisateur a bien payé.
        Transaction tx = transactionService.completeTransaction(id);
        return ResponseEntity.ok(tx);
    }

    /**
     * Obtenir l'historique de l'utilisateur connecté.
     */
    @GetMapping("/history")
    public ResponseEntity<List<Transaction>> getMyHistory(@AuthenticationPrincipal Jwt principal) {
        UUID userId = UUID.fromString(principal.getSubject());
        return ResponseEntity.ok(transactionService.getUserHistory(userId));
    }

    @GetMapping("/sales")
    public ResponseEntity<List<Transaction>> getMySales(@AuthenticationPrincipal Jwt principal) {
        UUID userId = UUID.fromString(principal.getSubject());
        return ResponseEntity.ok(transactionService.getUserSales(userId));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.getTransaction(id));
    }

    // GET /transactions/admin/all
    @GetMapping("/admin/all")
    // L'annotation @PreAuthorize est optionnelle si SecurityConfig gère déjà le path, mais c'est une bonne sécurité supplémentaire
    // @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        return ResponseEntity.ok(transactionService.getAllTransactions());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelTransaction(@PathVariable UUID id) {
        transactionService.cancelTransaction(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/fail")
    public ResponseEntity<Void> failTransaction(@PathVariable UUID id) {
        transactionService.failTransaction(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/maintenance/backfill-vendors")
    public ResponseEntity<Void> backfillVendors() {
        transactionService.backfillVendorIds();
        return ResponseEntity.ok().build();
    }
}