-- Création de la table transactions (Pluriel comme dans @Table)
CREATE TABLE transactions (
    transaction_id UUID PRIMARY KEY,
    
    -- IDs externes (UUID selon votre modèle Java)
    buyer_id UUID NOT NULL,
    ticket_id UUID NOT NULL,
    
    -- Montants (Double -> DOUBLE PRECISION)
    total_amount DOUBLE PRECISION NOT NULL,
    platform_fee DOUBLE PRECISION NOT NULL,
    vendor_amount DOUBLE PRECISION NOT NULL,
    
    -- Enums et Strings
    payment_method VARCHAR(50),
    payment_status VARCHAR(50),
    payment_token VARCHAR(255),
    refund_address VARCHAR(255),
    
    -- Dates (LocalDateTime -> TIMESTAMP)
    transaction_date TIMESTAMP,
    validation_date TIMESTAMP,
    
    -- Statut (Enum)
    status VARCHAR(50) NOT NULL
);

-- Index pour optimiser les recherches par acheteur et par ticket
CREATE INDEX idx_transactions_buyer ON transactions(buyer_id);
CREATE INDEX idx_transactions_ticket ON transactions(ticket_id);
CREATE INDEX idx_transactions_status ON transactions(status);