import java.sql.*;
import java.util.Scanner;

public class Bank {

    static Scanner sc = new Scanner(System.in);

    /** Reads a connection setting from the environment, falling back to a local default. */
    static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    static Connection conn;

    public static void main(String[] args) {
        String url      = envOr("BANK_DB_URL",  "jdbc:postgresql://localhost:5432/bankdb");
        String userid   = envOr("BANK_DB_USER", "bankapp");
        String password = envOr("BANK_DB_PASS", "bankapp");

        try (Connection c = DriverManager.getConnection(url, userid, password)) {
            conn = c;
            conn.setAutoCommit(false);
            System.out.println("Welcome to NSL26 Bank.");

            while (true) {
                System.out.println();
                System.out.println("--- Main Menu ---");
                System.out.println("1. Deposit or withdraw");
                System.out.println("2. Make a purchase with a card");
                System.out.println("3. Open a new account");
                System.out.println("4. View my accounts and recent activity");
                System.out.println("5. Pay a loan or credit card");
                System.out.println("0. Exit");
                int mcq = readInt("Choose an option: ");
                if (mcq == 0) {
                    System.out.println("You have exited the Menu.");
                    break;
                }
                try {
                    switch (mcq) {
                        case 1: depositWithdraw(); 
                        break;
                        case 2: purchase(); 
                        break;
                        case 3: openAccount(); 
                        break;
                        case 4: viewActivity(); 
                        break;
                        case 5: makePayment(); 
                        break;
                        default: System.out.println("Please choose 0-5.");
                    }
                } catch (SQLException e) {
                    System.out.println("Sorry, that operation failed: " + e.getMessage());
                    try { conn.rollback(); 
                    } catch (SQLException ignored) {}
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // 0 lists all clients, -1 goes back
    static int pickClient() throws SQLException {
        int clientId = readInt("Enter your client id (0 to list all clients, -1 to go back): ");
        if (clientId == -1) return -1;
        if (clientId == 0) {
            listClients();
            clientId = readInt("Client id (-1 to go back): ");
            if (clientId == -1) 
                return -1;
        }
        return clientId;
    }

    //deposit and withdrawals
    static void depositWithdraw() throws SQLException {
        int clientId = pickClient();
        if (clientId == -1) return;
        listAccountsOf(clientId);

        int acct = readInt("Account number (-1 to go back): ");
        if (acct == -1) return;
        String type = accountType(acct);
        if (type == null) {
            System.out.println("No such account.");
            return;
        }

        System.out.println("1. Deposit   2. Withdraw   -1. Back");
        int action = readInt("Choose: ");
        if (action != 1 && action != 2) return;
        double amount = readAmount("Amount: $");

        if (action == 1) {
            updateBalance(acct, amount);
            recordActivity(acct, amount, "DEPOSIT");
            conn.commit();
            System.out.printf("Deposited $%.2f. New balance: $%.2f%n", amount, balance(acct));
        } else {
            double bal = balance(acct);
            if (type.equals("CHECKING")) {
                if (amount > bal) {
                    System.out.println("DECLINED: you cannot overdraw your checking account.");
                    return;
                }
            } else {
                double[] mb = savingsRules(acct);
                if (amount > bal) {
                    System.out.println("DECLINED: insufficient funds.");
                    return;
                }
                if (bal - amount < mb[0]) {
                    System.out.printf("Warning: this drops you below the $%.2f minimum. A $%.2f penalty applies.%n",
                        mb[0], mb[1]);
                    System.out.print("Proceed anyway? (y/n): ");
                    if (!sc.nextLine().trim().toLowerCase().startsWith("y")) return;
                    if (bal - amount - mb[1] < 0) {
                        System.out.println("DECLINED: the balance cannot cover withdrawal and the penalty.");
                        return;
                    }
                    updateBalance(acct, -mb[1]);
                }
            }
            updateBalance(acct, -amount);
            recordActivity(acct, amount, "WITHDRAWAL");
            conn.commit();
            System.out.printf("Withdrew $%.2f. New balance: $%.2f%n", amount, balance(acct));
        }
    }

    // Interface: purchase with a card
    // debit: the linked checking account must cover it
    // credit: statement balance + the purchase must be under the limit
    static void purchase() throws SQLException {
        int clientId = pickClient();
        if (clientId == -1) return;

        System.out.println("Your cards:");
        boolean any = false;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT card_number, card_type FROM card WHERE client_id = ?")) {
            ps.setInt(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    any = true;
                    System.out.println("  " + rs.getString(1) + "  " + rs.getString(2));
                }
            }
        }
        if (!any) {
            System.out.println("  (no cards on file)");
            return;
        }

        System.out.print("Card number (-1 to go back): ");
        String cardNum = sc.nextLine().trim();
        if (cardNum.equals("-1")) return;

        String cardType = null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT card_type FROM card WHERE card_number = ? AND client_id = ?")) {
            ps.setString(1, cardNum);
            ps.setInt(2, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) cardType = rs.getString(1);
            }
        }
        if (cardType == null) {
            System.out.println("That card is not on file for this client.");
            return;
        }

        System.out.println("Vendors:");
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT vendor_id, name FROM vendor ORDER BY vendor_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                System.out.println("  " + rs.getInt(1) + "  " + rs.getString(2));
        }
        int vendorId = readInt("Vendor id (-1 to go back): ");
        if (vendorId == -1) return;
        if (!vendorExists(vendorId)) {
            System.out.println("No such vendor.");
            return;
        }

        double amount = readAmount("Purchase amount: $");

        if (cardType.equals("DEBIT")) {
            int acct = linkedChecking(cardNum);
            if (amount > balance(acct)) {
                System.out.println("DECLINED: not enough in the linked checking account.");
                return;
            }
            updateBalance(acct, -amount);
        } else {
            double[] cc = creditState(cardNum);   // [running_balance, credit_limit]
            if (cc[0] + amount > cc[1]) {
                System.out.println("DECLINED: this purchase would exceed the credit limit.");
                return;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE credit_card SET running_balance = running_balance + ? WHERE card_number = ?")) {
                ps.setDouble(1, amount);
                ps.setString(2, cardNum);
                ps.executeUpdate();
            }
        }

        long txnId = insertTransaction(amount, "PURCHASE");
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO purchase (transaction_id, vendor_id, card_number) VALUES (?, ?, ?)")) {
            ps.setLong(1, txnId);
            ps.setInt(2, vendorId);
            ps.setString(3, cardNum);
            ps.executeUpdate();
        }
        conn.commit();
        System.out.printf("Purchase of $%.2f approved.%n", amount);
    }

    // Interface: open a new account
    static void openAccount() throws SQLException {
        int clientId = pickClient();
        if (clientId == -1) return;
        if (!clientExists(clientId)) {
            System.out.println("No such client.");
            return;
        }

        System.out.println("1. Checking   2. Savings   -1. Back");
        int kind = readInt("Account type: ");
        if (kind != 1 && kind != 2) return;

        double opening = readAmount("Opening deposit: $");

        int acct;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(MAX(accountnum), 1000) + 1 FROM account");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            acct = rs.getInt(1);
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO account VALUES (?, ?, ?, CURRENT_DATE, ?)")) {
            ps.setInt(1, acct);
            ps.setDouble(2, opening);
            ps.setDouble(3, kind == 1 ? 0.010 : 0.030);
            ps.setString(4, kind == 1 ? "CHECKING" : "SAVINGS");
            ps.executeUpdate();
        }

        if (kind == 1) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO checking VALUES (?, 5.00)")) {
                ps.setInt(1, acct);
                ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO savings VALUES (?, 250.00, 25.00)")) {
                ps.setInt(1, acct);
                ps.executeUpdate();
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO owns VALUES (?, ?)")) {
            ps.setInt(1, clientId);
            ps.setInt(2, acct);
            ps.executeUpdate();
        }

        conn.commit();
        System.out.println("Opened " + (kind == 1 ? "checking" : "savings") +
            " account " + acct + " with $" + opening + ".");
        if (kind == 2)
            System.out.println("Savings terms: $250.00 minimum balance, $25.00 penalty.");
    }

    // Interface: view accounts + recent activity (for verifying wha went through)
    static void viewActivity() throws SQLException {
        int clientId = pickClient();
        if (clientId == -1) return;

        listAccountsOf(clientId);

        System.out.println("Recent deposits/withdrawals:");
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT t.transaction_id, a.activity_type, t.transaction_value, t.txn_timestamp, a.accountnum " +
                "FROM bank_transaction t JOIN account_activity a ON t.transaction_id = a.transaction_id " +
                "JOIN owns o ON a.accountnum = o.accountnum " +
                "WHERE o.client_id = ? ORDER BY t.txn_timestamp DESC FETCH FIRST 10 ROWS ONLY")) {
            ps.setInt(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    System.out.printf("  #%d  %-10s $%.2f  acct %d  %s%n",
                        rs.getLong(1), rs.getString(2), rs.getDouble(3),
                        rs.getInt(5), rs.getTimestamp(4).toString());
                }
                if (!any) System.out.println("  (none)");
            }
        }

        System.out.println("Recent card purchases:");
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT t.transaction_id, v.name, t.transaction_value, t.txn_timestamp " +
                "FROM bank_transaction t JOIN purchase p ON t.transaction_id = p.transaction_id " +
                "JOIN vendor v ON p.vendor_id = v.vendor_id " +
                "JOIN card c ON p.card_number = c.card_number " +
                "WHERE c.client_id = ? ORDER BY t.txn_timestamp DESC FETCH FIRST 10 ROWS ONLY")) {
            ps.setInt(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    System.out.printf("  #%d  %-18s $%.2f  %s%n",
                        rs.getLong(1), rs.getString(2), rs.getDouble(3),
                        rs.getTimestamp(4).toString());
                }
                if (!any) System.out.println("  (none)");
            }
        }
        System.out.println("Loans:");
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT l.loan_id, l.loan_type, l.loan_balance " +
                "FROM loan l JOIN borrows b ON l.loan_id = b.loan_id WHERE b.client_id = ?")) {
            ps.setInt(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    System.out.printf("  %d  %-9s  balance $%.2f%n",
                        rs.getInt(1), rs.getString(2), rs.getDouble(3));
                }
                if (!any) System.out.println("  (none)");
            }
        }

        System.out.println("Credit cards:");
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT cc.card_number, cc.running_balance, cc.credit_limit " +
                "FROM credit_card cc JOIN card c ON cc.card_number = c.card_number WHERE c.client_id = ?")) {
            ps.setInt(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    System.out.printf("  %s  owed $%.2f of $%.2f limit%n",
                        rs.getString(1), rs.getDouble(2), rs.getDouble(3));
                }
                if (!any) System.out.println("  (none)");
            }
        }
    }

    // Interface for payment on a loan or credit card
    static void makePayment() throws SQLException {
        int clientId = pickClient();
        if (clientId == -1) return;

        System.out.println("1. Loan payment   2. Credit card payment   -1. Back");
        int kind = readInt("Choose: ");
        if (kind != 1 && kind != 2) return;

        if (kind == 1) {
            System.out.println("Your loans:");
            boolean any = false;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT l.loan_id, l.loan_balance, l.monthly_payment " +
                    "FROM loan l JOIN borrows b ON l.loan_id = b.loan_id " +
                    "WHERE b.client_id = ?")) {
                ps.setInt(1, clientId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        any = true;
                        System.out.printf("  %d  balance $%.2f  monthly payment $%.2f%n",
                            rs.getInt(1), rs.getDouble(2), rs.getDouble(3));
                    }
                }
            }
            if (!any) {
                System.out.println("  (no loans on file)");
                return;
            }

            int loanId = readInt("Loan id (-1 to go back): ");
            if (loanId == -1) return;

            double owed = -1;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT l.loan_balance FROM loan l JOIN borrows b ON l.loan_id = b.loan_id " +
                    "WHERE l.loan_id = ? AND b.client_id = ?")) {
                ps.setInt(1, loanId);
                ps.setInt(2, clientId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) owed = rs.getDouble(1);
                }
            }
            if (owed < 0) {
                System.out.println("That loan is not on file for this client.");
                return;
            }

            double amount = readAmount("Payment amount: $");
            if (amount > owed) {
                System.out.printf("That is more than the remaining balance. Paying off the full $%.2f instead.%n", owed);
                amount = owed;
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE loan SET loan_balance = loan_balance - ? WHERE loan_id = ?")) {
                ps.setDouble(1, amount);
                ps.setInt(2, loanId);
                ps.executeUpdate();
            }
            long txnId = insertTransaction(amount, "LOAN_PAYMENT");
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO loan_payment (transaction_id, loan_id) VALUES (?, ?)")) {
                ps.setLong(1, txnId);
                ps.setInt(2, loanId);
                ps.executeUpdate();
            }
            conn.commit();
            System.out.printf("Payment of $%.2f applied. Loan balance is now $%.2f.%n", amount, owed - amount);

        } else {
            System.out.println("Your credit cards:");
            boolean any = false;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT cc.card_number, cc.running_balance, cc.balance_due " +
                    "FROM credit_card cc JOIN card c ON cc.card_number = c.card_number " +
                    "WHERE c.client_id = ?")) {
                ps.setInt(1, clientId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        any = true;
                        System.out.printf("  %s  owed $%.2f  due $%.2f%n",
                            rs.getString(1), rs.getDouble(2), rs.getDouble(3));
                    }
                }
            }
            if (!any) {
                System.out.println("  (no credit cards on file)");
                return;
            }

            System.out.print("Card number (-1 to go back): ");
            String cardNum = sc.nextLine().trim();
            if (cardNum.equals("-1")) return;

            double owed = -1;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT cc.running_balance FROM credit_card cc JOIN card c ON cc.card_number = c.card_number " +
                    "WHERE cc.card_number = ? AND c.client_id = ?")) {
                ps.setString(1, cardNum);
                ps.setInt(2, clientId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) owed = rs.getDouble(1);
                }
            }
            if (owed < 0) {
                System.out.println("That credit card is not on file for this client.");
                return;
            }
            if (owed == 0) {
                System.out.println("Nothing is owed on this card.");
                return;
            }

            double amount = readAmount("Payment amount: $");
            if (amount > owed) {
                System.out.printf("That is more than what is owed. Paying off the full $%.2f instead.%n", owed);
                amount = owed;
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE credit_card SET running_balance = running_balance - ?, " +
                    "balance_due = GREATEST(balance_due - ?, 0) WHERE card_number = ?")) {
                ps.setDouble(1, amount);
                ps.setDouble(2, amount);
                ps.setString(3, cardNum);
                ps.executeUpdate();
            }
            long txnId = insertTransaction(amount, "STMNT_PAYMENT");
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO stmnt_payment (transaction_id, card_number) VALUES (?, ?)")) {
                ps.setLong(1, txnId);
                ps.setString(2, cardNum);
                ps.executeUpdate();
            }
            conn.commit();
            System.out.printf("Payment of $%.2f applied. Card balance is now $%.2f.%n", amount, owed - amount);
        }
    }

    //helper methods

    static void listClients() throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT client_id, name FROM client ORDER BY client_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                System.out.println("  " + rs.getInt(1) + "  " + rs.getString(2));
        }
    }

    static void listAccountsOf(int clientId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT a.accountnum, a.account_type, a.balance " +
                "FROM account a JOIN owns o ON a.accountnum = o.accountnum " +
                "WHERE o.client_id = ?")) {
            ps.setInt(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("Your accounts:");
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    System.out.printf("  %d  %-8s  $%.2f%n",
                        rs.getInt(1), rs.getString(2), rs.getDouble(3));
                }
                if (!any) System.out.println("  (none found)");
            }
        }
    }

    static boolean clientExists(int clientId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM client WHERE client_id = ?")) {
            ps.setInt(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    static boolean vendorExists(int vendorId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM vendor WHERE vendor_id = ?")) {
            ps.setInt(1, vendorId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    static String accountType(int acct) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT account_type FROM account WHERE accountnum = ?")) {
            ps.setInt(1, acct);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    static double balance(int acct) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT balance FROM account WHERE accountnum = ?")) {
            ps.setInt(1, acct);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDouble(1);
            }
        }
    }

    static double[] savingsRules(int acct) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT min_balance, penalty FROM savings WHERE accountnum = ?")) {
            ps.setInt(1, acct);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new double[]{ rs.getDouble(1), rs.getDouble(2) };
            }
        }
    }

    static int linkedChecking(String cardNum) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT accountnum FROM debit_card WHERE card_number = ?")) {
            ps.setString(1, cardNum);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    static double[] creditState(String cardNum) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT running_balance, credit_limit FROM credit_card WHERE card_number = ?")) {
            ps.setString(1, cardNum);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new double[]{ rs.getDouble(1), rs.getDouble(2) };
            }
        }
    }

    static void updateBalance(int acct, double delta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE account SET balance = balance + ? WHERE accountnum = ?")) {
            ps.setDouble(1, delta);
            ps.setInt(2, acct);
            ps.executeUpdate();
        }
    }

    static long insertTransaction(double amount, String type) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO bank_transaction (transaction_value, txn_timestamp, transaction_type, branch_id) " +
                "VALUES (?, now(), ?, NULL)",
                new String[]{"transaction_id"})) {
            ps.setDouble(1, amount);
            ps.setString(2, type);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    static void recordActivity(int acct, double amount, String kind) throws SQLException {
        long txnId = insertTransaction(amount, "ACCT_ACTIVITY");
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO account_activity (transaction_id, accountnum, activity_type) VALUES (?, ?, ?)")) {
            ps.setLong(1, txnId);
            ps.setInt(2, acct);
            ps.setString(3, kind);
            ps.executeUpdate();
        }
    }

    static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please input an integer.");
            }
        }
    }

    static double readAmount(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                double v = Double.parseDouble(sc.nextLine().trim());
                if (v > 0) return Math.round(v * 100.0) / 100.0;
            } catch (NumberFormatException e) {
                // not a number, reprompt below
            }
            System.out.println("You must input a positive dollar amount, ex. 25.50");
        }
    }
}