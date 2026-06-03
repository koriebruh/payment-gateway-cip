ALTER TABLE outbox_events RENAME COLUMN topic TO event_type;
ALTER TABLE outbox_events ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE outbox_events ADD COLUMN published_at TIMESTAMP;
ALTER TABLE outbox_events ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
