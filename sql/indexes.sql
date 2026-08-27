--recent activity join
CREATE INDEX IF NOT EXISTS idx_activity_accountnum ON account_activity (accountnum);

--newest first ordering
CREATE INDEX IF NOT EXISTS idx_txn_timestamp_desc ON bank_transaction (txn_timestamp DESC);

--purchase history by card
CREATE INDEX IF NOT EXISTS idx_purchase_card ON purchase (card_number);

--card lookup by client
CREATE INDEX IF NOT EXISTS idx_card_client ON card (client_id);

ANALYZE account_activity, bank_transaction, purchase, card;