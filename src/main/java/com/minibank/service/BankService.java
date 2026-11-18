package com.minibank.service;

import com.minibank.exception.AccountNotFoundException;
import com.minibank.exception.InsufficientFundsException;
import com.minibank.model.Account;
import com.minibank.model.Client;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Properties;

public class BankService {

    private String url;
    private String username;
    private String password;

    public BankService() {
        // Загружаем настройки из db.properties
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("db.properties")) {
            Properties prop = new Properties();
            if (input == null) {
                // Фолбэк, если файл не найден
                url = "jdbc:postgresql://localhost:5432/minibank";
                username = "postgres";
                password = "postgres";
            } else {
                prop.load(input);
                url = prop.getProperty("url");
                username = prop.getProperty("username");
                password = prop.getProperty("password");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    public Client registerClient(String name, String email) {
        String sql = "INSERT INTO clients (name, email) VALUES (?, ?) RETURNING id";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Client(rs.getLong("id"), name, email);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error registering client", e);
        }
        throw new RuntimeException("Failed to register client");
    }

    public Account openAccount(Long clientId, String accountNumber) {
        // Проверяем, существует ли клиент
        getClient(clientId);

        String sql = "INSERT INTO accounts (client_id, account_number, balance) VALUES (?, ?, ?) RETURNING id";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, clientId);
            pstmt.setString(2, accountNumber);
            pstmt.setBigDecimal(3, BigDecimal.ZERO);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Account(rs.getLong("id"), clientId, accountNumber, BigDecimal.ZERO);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error opening account", e);
        }
        throw new RuntimeException("Failed to open account");
    }

    public Account getAccount(Long accountId) {
        String sql = "SELECT id, client_id, account_number, balance FROM accounts WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, accountId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Account(
                        rs.getLong("id"),
                        rs.getLong("client_id"),
                        rs.getString("account_number"),
                        rs.getBigDecimal("balance")
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching account", e);
        }
        // Выбрасываем кастомное исключение для 404
        throw new AccountNotFoundException("Account not found: " + accountId);
    }

    public Client getClient(Long clientId) {
        String sql = "SELECT id, name, email FROM clients WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, clientId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Client(rs.getLong("id"), rs.getString("name"), rs.getString("email"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching client", e);
        }
        // Для клиента тоже можно использовать AccountNotFoundException (или создать ClientNotFoundException)
        throw new AccountNotFoundException("Client not found: " + clientId);
    }

    public void transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        // Валидация суммы (переносим из хендлера)
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }

        Account from = getAccount(fromAccountId);

        if (from.getBalance().compareTo(amount) < 0) {
            // Выбрасываем кастомное исключение для 409
            throw new InsufficientFundsException("Insufficient funds on account " + fromAccountId);
        }

        // Списание (до проверки получателя — баг сохранен!)
        updateBalance(fromAccountId, from.getBalance().subtract(amount));
        recordTransaction(fromAccountId, amount.negate(), "TRANSFER_OUT");

        // Если получателя нет — деньги пропадают (баг)
        Account to = getAccount(toAccountId);

        updateBalance(toAccountId, to.getBalance().add(amount));
        recordTransaction(toAccountId, amount, "TRANSFER_IN");
    }

    public void updateBalance(Long accountId, BigDecimal newBalance) {
        String sql = "UPDATE accounts SET balance = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBigDecimal(1, newBalance);
            pstmt.setLong(2, accountId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating balance", e);
        }
    }

    private void recordTransaction(Long accountId, BigDecimal amount, String type) {
        String sql = "INSERT INTO transactions (account_id, amount, type, transaction_date) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, accountId);
            pstmt.setBigDecimal(2, amount);
            pstmt.setString(3, type);
            pstmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error recording transaction", e);
        }
    }
}
