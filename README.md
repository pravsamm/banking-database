Praveen Samuel -- SQL Database Project

This program models an SQL-based banking system with fictional client profiles. I built
it on Oracle for a database systems course and later ported it to PostgreSQL. The sql
folder contains the schema and all my original entries, so the database can be reset at
any time.

The profiles contain pre-loaded data which are unmodifiable within the program 
For example:
- Deleting existing bank account
- Creating new debit card and credit card connections, as well as modification of the credit limit
- Adding or removing clients, vendors, and branches
- Taking out new loans


Modifiable data includes:
- Account balances, through deposits and withdrawals
- Opening new checking and savings accounts under any client
- Debit card purchases directly draw from linked checking accounts, while credit card purchases add to the statement balance
- The make a payment window can be used to reduce loan balance and credit card balance

Additional Notes:
- Unlike debit card transactions, loan and credit card payments are treated as external payments rather than drawing from a bank account.
- When payments on credit cards and loans exceed the balance, the full amount is applied instead to prevent overpayment. 
- Checking accounts with pre-loaded debit cards can be used for purchases. (Client 1, Client 3, Client 4)
- Accounts with credit cards can carry a statement balance up to the credit limit (Client 1, Client 2, Client 5)
- View my accounts and recent activity (Option 4, Main Menu) provides account updates and proof of transaction for easy reference
- Client 6 is a newly joined client with no products yet


Performance Notes:
The recent activity screen looks up one client's deposits and withdrawals. With six
clients it is instant, so sql/test_data.sql loads 800,000 transactions to test it at
a realistic size.

There was no index on account_activity.accountnum, so Postgres had to read all
800,000 rows to find the ones for a single client in my original class project. Adding one index fixed that:

CREATE INDEX idx_activity_accountnum ON account_activity (accountnum);
Before: 41 ms
After: 4.8 ms

The index lets Postgres jump straight to the ~400 rows that match instead of reading
the whole table. Conceptually, Postgres builds the index as a B+ tree so instead of scanning all 800,000 rows it walks a few levels of the tree straight to the ones it needs.

However, the trade-off is the tree has to be updated with every insert.

How to run:

Requires Java 17 or later and PostgreSQL. I used Postgres.app on macOS.

1) Create the database:
createuser -s bankapp

createdb -O bankapp bankdb

2) Load the schema and starting data:
psql -d bankdb -f sql/schema.sql -f sql/seed_data.sql

If createuser and psql are not found, add Postgres.app's tools to your PATH:
sudo mkdir -p /etc/paths.d
echo /Applications/Postgres.app/Contents/Versions/latest/bin | sudo tee /etc/paths.d/postgresapp

Then open a new terminal.

3) Download the PostgreSQL JDBC driver into the project folder:
curl -L -o postgresql.jar https://jdbc.postgresql.org/download/postgresql-42.7.4.jar

4) Run:
java -jar bank.jar

The program connects to jdbc:postgresql://localhost:5432/bankdb as user bankapp.
Set BANK_DB_URL, BANK_DB_USER, or BANK_DB_PASS to point it somewhere else.

sql/test_data.sql and sql/indexes.sql are optional. test_data.sql adds 800,000
generated transactions, and indexes.sql adds the indexes used to measure query
performance against that volume.

To rebuild the jar:
javac -d build source_JDBC/Bank.java
jar cfm bank.jar Manifest.txt -C build .


Walkthrough:
1) Enter client id 1 (Dana Whitfield). Entering 0 lists all clients, and entering -1 goes back in any menu. 
2) Note the balance of checking account 1001.
3) Test checking account deposit or withdrawal: enter 1001 when prompted for account number and select 1 for deposit or 2 for withdrawal. Qualified withdrawals are positive values that do not exceed account balance. (Check with the my accounts and recent activity window or by re-entering the deposit/withdrawal interface)
4) Test savings account deposit/withdrawal: enter 2001 when prompted for account number and select 1 for deposit or 2 for withdrawal. Same integer rules apply, and dropping below a $500 balance triggers a $25.00 penalty. Balance must be able to cover the penalty post-withdrawal. (Check with the my accounts and recent activity window or by re-entering the deposit/withdrawl interface)
5) Test purchases: Client 1 owns both a credit card and a debit card. Test purchases using option 2 in the Main Menu. Choose one of the displayed cards by inputting the given card number (copy/paste number). The next prompt allows you to choose a vendor for the transaction by their vendor id. After making your selection, input a positive purchase amount. Debit transactions will extract funds from the linked checking account, and can be viewed in option 4 on the Main Menu. Credit will add to the statement balance, also viewable on Option 4 of the Main Menu.
6) Paying a loan or a credit card can be done via Option 5 on main Menu. Input a postive value for both, and it will be reduced to the full payment if it exceeds the balance. (ex. pay $1250 on loan 500, Client 1. Option 4 shows the loan balance reduced.)




SAMPLE OUTPUT:

Welcome to NSL26 Bank.

--- Main Menu ---
1. Deposit or withdraw
2. Make a purchase with a card
3. Open a new account
4. View my accounts and recent activity
5. Pay a loan or credit card
0. Exit
Choose an option: Enter your client id (0 to list all clients, -1 to go back): Your accounts:
  1001  CHECKING  $2500.00
  2001  SAVINGS   $15000.00
Account number (-1 to go back): 1. Deposit   2. Withdraw   -1. Back
Choose: Amount: $Deposited $250.00. New balance: $2750.00



Thanks for reading and testing my project.