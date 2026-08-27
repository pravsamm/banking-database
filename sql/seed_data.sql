-- Seed data: six fictional clients and a small set of accounts, cards, loans and
-- transactions. All names, addresses and card numbers are fictional.
--
-- Client 1 has a checking account (joint with client 2), a savings account, a debit
-- card, a credit card and a mortgage, so it exercises every path in the app.
-- Client 6 has no products at all, to cover empty/new user testing

INSERT INTO client VALUES (1, 'Dana Whitfield',  '88 Ridgeway Ave, Philadelphia PA');
INSERT INTO client VALUES (2, 'Alex Pereira',   '9 Lincoln Ave, Los Angeles CA');
INSERT INTO client VALUES (3, 'Sam Whitman',   '7 8th Ave, Los Angeles CA');
INSERT INTO client VALUES (4, 'John Miller',   '42 Chestnut St, Bethlehem PA');
INSERT INTO client VALUES (5, 'Maria Lopez',   '301 4th Ave, Easton PA');
INSERT INTO client VALUES (6, 'Evelyn Harper',   '1921 Willow Creek Ln, Lansdale PA');


INSERT INTO branch VALUES (10, '1 Center Sq, Bethlehem PA',  'Y');
INSERT INTO branch VALUES (11, '450 Hamilton Blvd, Allentown PA', 'Y');
INSERT INTO branch VALUES (12, 'Bustleton Pharmacy, Philadelphia PA',  'N');   -- ATM only

INSERT INTO vendor VALUES (100, 'The Goosemen');
INSERT INTO vendor VALUES (101, 'Wawa');
INSERT INTO vendor VALUES (102, 'Amazon');
INSERT INTO vendor VALUES (103, 'Lehigh Bookstore');
INSERT INTO vendor VALUES (104, 'Sheetz');
INSERT INTO vendor VALUES (105, 'Walmart');

INSERT INTO account VALUES (1001, 2500.00, 0.010, DATE '2024-01-15', 'CHECKING');
INSERT INTO account VALUES (1002,  340.25, 0.010, DATE '2024-06-02', 'CHECKING');
INSERT INTO account VALUES (1003, 5200.00, 0.010, DATE '2025-02-11', 'CHECKING');
INSERT INTO account VALUES (1004,  610.75, 0.010, DATE '2025-09-30', 'CHECKING');
INSERT INTO account VALUES (2001, 15000.00, 0.035, DATE '2024-02-10', 'SAVINGS');
INSERT INTO account VALUES (2002,  1200.00, 0.030, DATE '2025-05-22', 'SAVINGS');
INSERT INTO checking VALUES (1001, 5.00);
INSERT INTO checking VALUES (1002, 0.00);
INSERT INTO checking VALUES (1003, 5.00);
INSERT INTO checking VALUES (1004, 0.00);
INSERT INTO savings  VALUES (2001, 500.00, 25.00);
INSERT INTO savings  VALUES (2002, 250.00, 15.00);

--joint account owner
INSERT INTO owns VALUES (1, 1001);
INSERT INTO owns VALUES (2, 1001);
INSERT INTO owns VALUES (2, 1002);
INSERT INTO owns VALUES (3, 1003);
INSERT INTO owns VALUES (4, 1004);
INSERT INTO owns VALUES (1, 2001);
INSERT INTO owns VALUES (5, 2002);

--debit cards
INSERT INTO card VALUES ('4000111122223333', DATE '2028-05-31', 'DEBIT', 1);
INSERT INTO card VALUES ('4000222233334444', DATE '2027-11-30', 'DEBIT', 3);
INSERT INTO card VALUES ('4000333344445555', DATE '2029-02-28', 'DEBIT', 4);
INSERT INTO card VALUES ('5100123412341234', DATE '2028-03-31', 'CREDIT', 1);
INSERT INTO card VALUES ('5100234523452345', DATE '2027-08-31', 'CREDIT', 2);
INSERT INTO card VALUES ('5100345634563456', DATE '2029-04-30', 'CREDIT', 5);
INSERT INTO debit_card VALUES ('4000111122223333', 500.00, 1001);
INSERT INTO debit_card VALUES ('4000222233334444', 750.00, 1003);
INSERT INTO debit_card VALUES ('4000333344445555', 300.00, 1004);
INSERT INTO credit_card VALUES ('5100123412341234', 0.219,  5000.00,  650.00,  400.00);
INSERT INTO credit_card VALUES ('5100234523452345', 0.249,  2000.00, 1100.50,  900.00);
INSERT INTO credit_card VALUES ('5100345634563456', 0.189,  8000.00,    0.00,    0.00);


--loans
INSERT INTO loan VALUES (500, 285000.00, 0.062, 1745.00, 'SECURED'); 
INSERT INTO loan VALUES (501, 195000.00, 0.058, 1230.00, 'SECURED');
INSERT INTO loan VALUES (502,   8000.00, 0.109,  180.00, 'UNSECURED');
INSERT INTO secured_loan VALUES (500, 82.50);
INSERT INTO secured_loan VALUES (501, 74.00);
INSERT INTO unsecured_loan VALUES (502, 690);
INSERT INTO borrows VALUES (1, 500);
INSERT INTO borrows VALUES (2, 501);
INSERT INTO borrows VALUES (3, 501);
INSERT INTO borrows VALUES (4, 502);

--collateral
INSERT INTO collateral VALUES (900, 345000.00, 500);
INSERT INTO collateral VALUES (901, 260000.00, 501);


--purchases
INSERT INTO bank_transaction VALUES (1,  62.40, TIMESTAMP '2026-08-03 12:20:00', 'PURCHASE', NULL);
INSERT INTO purchase VALUES (1, 101, '4000111122223333');
INSERT INTO bank_transaction VALUES (2, 214.99, TIMESTAMP '2026-08-04 15:05:00', 'PURCHASE', NULL);
INSERT INTO purchase VALUES (2, 102, '5100123412341234');
INSERT INTO bank_transaction VALUES (3,  27.15, TIMESTAMP '2026-08-05 09:40:00', 'PURCHASE', NULL);
INSERT INTO purchase VALUES (3, 104, '4000222233334444');

--loan payments
INSERT INTO bank_transaction VALUES (4, 1745.00, TIMESTAMP '2026-08-01 10:00:00', 'LOAN_PAYMENT', 10);
INSERT INTO loan_payment VALUES (4, 500);
INSERT INTO bank_transaction VALUES (5,  180.00, TIMESTAMP '2026-08-06 17:30:00', 'LOAN_PAYMENT', NULL);
INSERT INTO loan_payment VALUES (5, 502);

--credit card statement payments
INSERT INTO bank_transaction VALUES (6, 400.00, TIMESTAMP '2026-08-05 19:00:00', 'STMNT_PAYMENT', NULL);
INSERT INTO stmnt_payment VALUES (6, '5100123412341234');


--transfer funds
INSERT INTO bank_transaction VALUES (7, 500.00, TIMESTAMP '2026-08-06 08:45:00', 'TRANSFER', NULL);
INSERT INTO transfer VALUES (7, 2001, 1001);

--Deposits/withdrawals
INSERT INTO bank_transaction VALUES (14,  40.00, TIMESTAMP '2026-08-08 21:15:00', 'ACCT_ACTIVITY', 12);
INSERT INTO account_activity VALUES (14, 1001, 'WITHDRAWAL');
INSERT INTO bank_transaction VALUES (15, 300.00, TIMESTAMP '2026-08-09 11:10:00', 'ACCT_ACTIVITY', 10);
INSERT INTO account_activity VALUES (15, 1003, 'DEPOSIT');
INSERT INTO bank_transaction VALUES (16,  75.00, TIMESTAMP '2026-08-10 14:50:00', 'ACCT_ACTIVITY', 12);
INSERT INTO account_activity VALUES (16, 1002, 'WITHDRAWAL');


SELECT setval(pg_get_serial_sequence('bank_transaction','transaction_id'), 1000, false);