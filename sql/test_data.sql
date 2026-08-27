
-- The seed data is six clients and ten transactions, which is fine for exercising
-- the application but useless for measuring anything: at that size every plan is a
-- sequential scan and every query takes under a millisecond.

-- This adds 2,000 clients, 4,000 checking accounts and 800,000 deposit/withdrawal
-- transactions to further the scope of this project and model the daily operations of a mid-sized credit union
--


-- 2,000 extra clients, ids 10000+
INSERT INTO client (client_id, name, address)
SELECT 10000 + g,
       'Client ' || g,
       g || ' Example St, Bethlehem PA'
FROM generate_series(1, 2000) AS g;

-- 4,000 checking accounts, ids 100000+ (two per client)
INSERT INTO account (accountnum, balance, interest_rate, date_opened, account_type)
SELECT 100000 + g,
       round((random() * 20000)::numeric, 2),
       0.010,
       DATE '2024-01-01' + (random() * 900)::int,
       'CHECKING'
FROM generate_series(1, 4000) AS g;

INSERT INTO checking (accountnum, monthly_fee)
SELECT 100000 + g, 0.00 FROM generate_series(1, 4000) AS g;

INSERT INTO owns (client_id, accountnum)
SELECT 10000 + ((g - 1) / 2) + 1, 100000 + g
FROM generate_series(1, 4000) AS g;

-- 800,000 account-activity transactions spread over two years
INSERT INTO bank_transaction (transaction_value, txn_timestamp, transaction_type, branch_id)
SELECT round((random() * 900 + 5)::numeric, 2),
       TIMESTAMPTZ '2024-01-01 00:00:00+00' + (random() * 730 * 86400) * INTERVAL '1 second',
       'ACCT_ACTIVITY',
       NULL
FROM generate_series(1, 800000);

INSERT INTO account_activity (transaction_id, accountnum, activity_type)
SELECT t.transaction_id,
       100000 + (1 + floor(random() * 4000))::int,
       CASE WHEN random() < 0.5 THEN 'DEPOSIT' ELSE 'WITHDRAWAL' END
FROM bank_transaction t
WHERE t.transaction_id >= 1000
  AND t.transaction_type = 'ACCT_ACTIVITY'
  AND NOT EXISTS (SELECT 1 FROM account_activity a WHERE a.transaction_id = t.transaction_id);

ANALYZE client, account, checking, owns, bank_transaction, account_activity;
