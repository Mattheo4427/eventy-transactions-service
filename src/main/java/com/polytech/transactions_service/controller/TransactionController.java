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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Initier un achat.
     */
    @PostMapping
    public ResponseEntity<Transaction> createTransaction(
            @RequestBody CreateTransactionRequest request,
            @AuthenticationPrincipal Jwt principal) {
        
        String buyerId = principal.getSubject();
        Transaction tx = transactionService.initiateTransaction(buyerId, request.getTicketId());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(tx);
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
}