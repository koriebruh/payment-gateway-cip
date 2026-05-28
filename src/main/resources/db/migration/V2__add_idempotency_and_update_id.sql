ALTER TABLE transactions DROP CONSTRAINT transactions_pkey;
ALTER TABLE transactions ALTER COLUMN id TYPE VARCHAR(50) USING id::text;
UPDATE transactions SET id = 'TX-' || UPPER(SUBSTRING(id, 1, 8));
ALTER TABLE transactions ADD PRIMARY KEY (id);

ALTER TABLE transactions ADD COLUMN idempotency_key VARCHAR(255);
ALTER TABLE transactions ADD CONSTRAINT uk_transactions_idempotency_key UNIQUE (idempotency_key);
