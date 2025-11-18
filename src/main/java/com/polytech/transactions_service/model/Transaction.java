package com.polytech.transactions_service.model;

import com.polytech.transactions_service.model.enums.PaymentMethod;
import com.polytech.transactions_service.model.enums.PaymentStatus;
import com.polytech.transactions_service.model.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "transaction_id")
    private UUID id;

    // ID de l'acheteur (extrait du Token JWT)
    @Column(name = "buyer_id", nullable = false)
    private String buyerId; // String car c'est souvent un UUID Keycloak stocké en String

    // ID du ticket (référence vers Ticket Service)
    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    // --- Informations Financières ---
    
    @Column(name = "total_amount", nullable = false)
    private Double totalAmount; // Montant payé par l'acheteur (Prix + Frais)

    @Column(name = "platform_fee", nullable = false)
    private Double platformFee; // Commission de la plateforme

    @Column(name = "vendor_amount", nullable = false)
    private Double vendorAmount; // Montant reversé au vendeur

    // --- Informations Paiement ---

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;
    
    @Column(name = "payment_token")
    private String paymentToken; // ID de transaction externe (ex: Stripe ID)

    @Column(name = "refund_address")
    private String refundAddress; // Optionnel

    // --- Dates et État ---

    @CreationTimestamp
    @Column(name = "transaction_date", updatable = false)
    private LocalDateTime transactionDate;

    @Column(name = "validation_date")
    private LocalDateTime validationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status = TransactionStatus.PENDING;
}