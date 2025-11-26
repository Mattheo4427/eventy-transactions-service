package com.polytech.transactions_service.service;

import com.polytech.transactions_service.client.TicketClient;
import com.polytech.transactions_service.client.UserClient;
import com.polytech.transactions_service.dto.CreateTransactionRequest;
import com.polytech.transactions_service.dto.TicketDto;
import com.polytech.transactions_service.dto.UserDto;
import com.polytech.transactions_service.model.Transaction;
import com.polytech.transactions_service.model.enums.PaymentMethod;
import com.polytech.transactions_service.model.enums.TransactionStatus;
import com.polytech.transactions_service.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private TicketClient ticketClient;
    @Mock private UserClient userClient;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void createTransaction_ShouldSucceed() {
        // Arrange
        UUID ticketId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();

        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setTicketId(ticketId);
        request.setUserId(buyerId);
        request.setAmount(100.0);
        request.setPaymentMethod(PaymentMethod.CREDIT_CARD);

        // Mock Ticket Service Response
        TicketDto ticketDto = new TicketDto();
        ticketDto.setId(ticketId);
        ticketDto.setSalePrice(100.0);
        ticketDto.setStatus("AVAILABLE");
        when(ticketClient.getTicketById(ticketId)).thenReturn(ticketDto);

        // Mock User Service Response
        UserDto userDto = new UserDto();
        userDto.setId(buyerId);
        when(userClient.getUserById(buyerId)).thenReturn(userDto);

        // Mock Repository
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            t.setStatus(TransactionStatus.COMPLETED);
            return t;
        });

        // Act
        Transaction result = transactionService.createTransaction(String.valueOf(buyerId), ticketId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        // Vérifie que l'événement Kafka a été envoyé
        verify(kafkaTemplate).send(eq("ticket-sold"), any());
    }

    @Test
    void createTransaction_ShouldFail_WhenTicketNotAvailable() {
        // Arrange
        UUID ticketId = UUID.randomUUID();
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setTicketId(ticketId);
        request.setBuyerId(UUID.randomUUID());

        TicketDto ticketDto = new TicketDto();
        ticketDto.setStatus("SOLD"); // Ticket déjà vendu
        when(ticketClient.getTicketById(ticketId)).thenReturn(ticketDto);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> transactionService.createTransaction(String.valueOf(request.getBuyerId()), request.getTicketId()));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void getAllTransactions_ShouldReturnList() {
        when(transactionRepository.findAll()).thenReturn(List.of(new Transaction(), new Transaction()));

        List<Transaction> result = transactionService.getAllTransactions();

        assertThat(result).hasSize(2);
    }
}