package com.minibank.service;

import com.minibank.exception.AccountNotFoundException;
import com.minibank.exception.InsufficientFundsException;
import com.minibank.model.Account;
import com.minibank.model.Client;
import com.minibank.model.Transaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

public class BankService implements AutoCloseable {
    private final EntityManagerFactory factory;

    public BankService() {
        Properties db = new Properties();
        Path file = Path.of("db.properties");

        try (InputStream input = Files.newInputStream(file)) {
            db.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать db.properties", e);
        }

        String url = db.getProperty("url");
        String username = db.getProperty("username");
        String password = db.getProperty("password");

        if (url == null || username == null || password == null) {
            throw new IllegalStateException(
                    "В db.properties должны быть указаны url, username и password"
            );
        }

        factory = Persistence.createEntityManagerFactory("minibank", Map.of(
                "jakarta.persistence.jdbc.url", url,
                "jakarta.persistence.jdbc.user", username,
                "jakarta.persistence.jdbc.password", password
        ));
    }

    public Client registerClient(String name, String email) {
        return inTransaction(em -> {
            Client client = new Client(null, name, email);
            em.persist(client);
            return client;
        });
    }

    public Account openAccount(Long clientId, String accountNumber) {
        return inTransaction(em -> {
            Account account = new Account(findClient(em, clientId), accountNumber);
            em.persist(account);
            return account;
        });
    }

    public Client getClient(Long clientId) {
        try (EntityManager em = factory.createEntityManager()) {
            return findClient(em, clientId);
        }
    }

    public Account getAccount(Long accountId) {
        try (EntityManager em = factory.createEntityManager()) {
            return findAccount(em, accountId);
        }
    }

    public List<Transaction> getTransactions(Long accountId) {
        try (EntityManager em = factory.createEntityManager()) {
            findAccount(em, accountId);
            return em.createQuery("""
                    select t from Transaction t
                    where t.accountId = :accountId
                    order by t.timestamp desc, t.id desc
                    """, Transaction.class)
                    .setParameter("accountId", accountId)
                    .getResultList();
        }
    }

    public void updateBalance(Long accountId, BigDecimal newBalance) {
        inTransaction(em -> {
            findAccount(em, accountId).changeBalance(newBalance);
            return null;
        });
    }

    public void transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Amount must be greater than 0");

        // Две транзакции сохраняют учебный баг до модуля 7.1.
        inTransaction(em -> {
            Account from = findAccount(em, fromAccountId);
            BigDecimal balance = from.getBalance().subtract(amount);
            if (balance.signum() < 0) {
                throw new InsufficientFundsException("Insufficient funds on account " + fromAccountId);
            }

            from.changeBalance(balance);
            em.persist(new Transaction(from, amount.negate(), "TRANSFER_OUT"));
            return null;
        });

        inTransaction(em -> {
            Account to = findAccount(em, toAccountId);
            to.changeBalance(to.getBalance().add(amount));
            em.persist(new Transaction(to, amount, "TRANSFER_IN"));
            return null;
        });
    }

    private Client findClient(EntityManager em, Long clientId) {
        Client client = em.find(Client.class, clientId);
        if (client == null) {
            throw new AccountNotFoundException("Client not found: " + clientId);
        }
        return client;
    }

    private Account findAccount(EntityManager em, Long accountId) {
        Account account = em.find(Account.class, accountId);
        if (account == null) {
            throw new AccountNotFoundException("Account not found: " + accountId);
        }
        return account;
    }

    private <T> T inTransaction(Function<EntityManager, T> action) {
        try (EntityManager em = factory.createEntityManager()) {
            var transaction = em.getTransaction();
            transaction.begin();
            try {
                T result = action.apply(em);
                transaction.commit();
                return result;
            } catch (RuntimeException e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw e;
            }
        }
    }
    public List<Client> getClientsWithAccounts() {
        try (EntityManager em = factory.createEntityManager()) {
            return em.createQuery("select c from Client c left join fetch c.accounts order by c.id", Client.class)
                    .getResultList();
        }
    }

    public void removeAccountFromClient(Long clientId, Long accountId) {
        inTransaction(em -> {
            Client client = findClient(em, clientId);
            boolean removed = client.getAccounts()
                    .removeIf(account -> accountId.equals(account.getId()));

            if (!removed) {
                throw new AccountNotFoundException("Account not found: " + accountId);
            }
            return null;
        });
    }

    @Override
    public void close() {
        factory.close();
    }
}