package com.polytech.transactions_service.repository;

import com.polytech.transactions_service.model.Transaction;
import com.polytech.transactions_service.model.enums.PaymentMethod;
import com.polytech.transactions_service.model.enums.PaymentStatus;
import com.polytech.transactions_service.model.enums.TransactionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("Transaction Repository Tests")
class TransactionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void findByBuyerId_ShouldReturnList() {
        // Given
        UUID buyerId = UUID.randomUUID();
        createTransaction(buyerId);
        createTransaction(buyerId);
        createTransaction(UUID.randomUUID()); // Autre buyer

        // When
        List<Transaction> results = transactionRepository.findByBuyerId(buyerId);

        // Then
        assertThat(results).hasSize(2);
    }

    @Test
    void findByTicketId_ShouldReturnTransaction() {
        // Given
        UUID ticketId = UUID.randomUUID();
        Transaction t = createTransaction(UUID.randomUUID());
        t.setTicketId(ticketId);
        entityManager.persist(t);
        entityManager.flush();

        // When
        List<Transaction> results = transactionRepository.findByTicketId(ticketId);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTicketId()).isEqualTo(ticketId);
    }

    private Transaction createTransaction(UUID buyerId) {
        Transaction t = Transaction.builder()
                .buyerId(buyerId)
                .ticketId(UUID.randomUUID())
                .totalAmount(50.0)
                .platformFee(5.0)
                .vendorAmount(45.0)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .paymentStatus(PaymentStatus.PAID)
                .status(TransactionStatus.COMPLETED)
                .transactionDate(LocalDateTime.now())
                .build();
        return entityManager.persist(t);
    }
}