package com.polytech.transactions_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polytech.transactions_service.config.SecurityConfig;
import com.polytech.transactions_service.dto.CreateTransactionRequest;
import com.polytech.transactions_service.model.Transaction;
import com.polytech.transactions_service.model.enums.PaymentMethod;
import com.polytech.transactions_service.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import(SecurityConfig.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    @DisplayName("POST /transactions - Should create transaction (Authentication with JWT)")
    void createTransaction_ShouldSucceed() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setTicketId(ticketId);
        // Note: request.setUserId(userId) est ignoré par le contrôleur qui utilise le token JWT,
        // mais on le met pour la cohérence de l'objet
        request.setUserId(userId);
        request.setAmount(50.0);
        request.setPaymentMethod(PaymentMethod.PAYPAL);

        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setTotalAmount(50.0);

        // CORRECTION ICI : On mock 'initiateTransaction' car c'est la méthode appelée par le contrôleur
        // Le contrôleur passe (String buyerId, UUID ticketId)
        when(transactionService.initiateTransaction(eq(userId.toString()), eq(ticketId)))
                .thenReturn(transaction);

        // Act & Assert
        mockMvc.perform(post("/transactions")
                        .with(csrf())
                        // On simule le token JWT avec le 'sub' égal à l'userId
                        .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                // Maintenant le corps ne sera plus vide
                .andExpect(jsonPath("$.totalAmount").value(50.0));
    }

    @Test
    @DisplayName("GET /transactions/admin/all - Should verify Admin role")
    void getAllTransactions_AsAdmin_ShouldSucceed() throws Exception {
        // Arrange
        when(transactionService.getAllTransactions()).thenReturn(List.of(new Transaction(), new Transaction()));

        // Act & Assert
        mockMvc.perform(get("/transactions/admin/all")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /transactions/admin/all - Should reject User role")
    void getAllTransactions_AsUser_ShouldForbidden() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/transactions/admin/all")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }
}