CREATE TABLE transactions
(
    id                 UUID PRIMARY KEY,
    order_id           VARCHAR(255)   NOT NULL UNIQUE,
    channel            VARCHAR(50)    NOT NULL, -- Enum: MOBILE_BANKING, INTERNET_BANKING, ATM
    amount             DECIMAL(19, 2) NOT NULL,
    account            VARCHAR(50)    NOT NULL,
    currency           VARCHAR(10) DEFAULT 'IDR',
    payment_method     VARCHAR(50),
    status             VARCHAR(20)    NOT NULL, -- Enum: PENDING, SUCCESS, FAILED
    corebank_reference VARCHAR(255),
    biller_reference   VARCHAR(255),
    created_at         TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transactions_order_id ON transactions (order_id);
CREATE INDEX idx_transactions_status ON transactions (status);

-- Insert dummy transactions
INSERT INTO transactions (id, order_id, channel, amount, account, currency, payment_method, status, created_at,
                          updated_at)
VALUES (gen_random_uuid(), 'INV-001', 'MOBILE_BANKING', 250000.00, '1234567890', 'IDR', 'VIRTUAL_ACCOUNT', 'SUCCESS',
        NOW(), NOW()),
       (gen_random_uuid(), 'INV-002', 'INTERNET_BANKING', 150000.00, '0987654321', 'IDR', 'VIRTUAL_ACCOUNT', 'PENDING',
        NOW(), NOW()),
       (gen_random_uuid(), 'INV-003', 'ATM', 50000.00, '1122334455', 'IDR', 'QRIS', 'FAILED', NOW(), NOW());