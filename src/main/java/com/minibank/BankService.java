package com.minibank;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MiniBank — "MVP", который наспех собрал предыдущий разработчик:
 * вся логика в одном классе, все данные — в HashMap, персистентности
 * нет вообще (перезапуск процесса — и всё пропало).
 *
 * ПРОБЛЕМА (найдёшь и исправишь, когда дойдёт очередь — модуль 7.1,
 * когда появится @Transactional): в transfer() проверка существования
 * счёта-получателя происходит ПОСЛЕ списания денег со счёта-
 * отправителя. Если счёт-получатель не существует, деньги уже спишутся
 * с первого счёта и "исчезнут" — их некуда вернуть, потому что метод
 * прерывается исключением до зачисления.
 *
 * Эта проблема не решается на HashMap "в лоб" без искусственных
 * костылей (можно вручную откатывать баланс в catch, но это не
 * настоящее решение) — она по-настоящему решается только когда
 * появится транзакционность (сначала в БД — ACID, модуль 4, а затем
 * в коде — @Transactional, модуль 7.1). До этого момента баг просто
 * существует и это нормально: то, что появится в модуле 7.1, — как
 * раз тот инструмент, который проблему полностью закрывает.
 */
public class BankService {

    private final Map<Long, Client> clients = new HashMap<>();
    private final Map<Long, Account> accounts = new HashMap<>();
    private final Map<Long, Transaction> transactions = new HashMap<>();

    private final AtomicLong clientIdSeq = new AtomicLong(1);
    private final AtomicLong accountIdSeq = new AtomicLong(1);
    private final AtomicLong transactionIdSeq = new AtomicLong(1);

    public Client registerClient(String name, String email) {
        Long id = clientIdSeq.getAndIncrement();
        Client client = new Client(id, name, email);
        clients.put(id, client);
        return client;
    }

    public Account openAccount(Long clientId, String accountNumber) {
        if (!clients.containsKey(clientId)) {
            throw new IllegalArgumentException("Client not found: " + clientId);
        }
        Long id = accountIdSeq.getAndIncrement();
        Account account = new Account(id, clientId, accountNumber, BigDecimal.ZERO);
        accounts.put(id, account);
        return account;
    }

    public Account getAccount(Long accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        return account;
    }

    public Client getClient(Long clientId) {
        Client client = clients.get(clientId);
        if (client == null) {
            throw new IllegalArgumentException("Client not found: " + clientId);
        }
        return client;
    }

    public void transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        Account from = getAccount(fromAccountId);

        // Списываем деньги ДО того, как проверили существование счёта-
        // получателя — если toAccountId не существует, деньги уже
        // списаны и потеряны.
        if (from.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds on account " + fromAccountId);
        }
        from.setBalance(from.getBalance().subtract(amount));
        recordTransaction(fromAccountId, amount.negate(), "TRANSFER_OUT");

        Account to = getAccount(toAccountId); // если бросит исключение — деньги уже пропали

        to.setBalance(to.getBalance().add(amount));
        recordTransaction(toAccountId, amount, "TRANSFER_IN");
    }

    private void recordTransaction(Long accountId, BigDecimal amount, String type) {
        Long id = transactionIdSeq.getAndIncrement();
        transactions.put(id, new Transaction(id, accountId, amount, type, LocalDateTime.now()));
    }
}
