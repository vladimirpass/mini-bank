package com.minibank;

import com.minibank.exception.AccountNotFoundException;
import com.minibank.service.BankService;
import java.util.UUID;

public class OrphanRemovalDemo {
    public static void main(String[] args) {
        try (BankService bank = new BankService()) {
            Long clientId = 1L;
            String number = "DEMO-" + UUID.randomUUID().toString().substring(0, 8);
            Long accountId = bank.openAccount(clientId, number).getId();

            System.out.println("До удаления: счёт " + bank.getAccount(accountId).getId()
                    + " найден в БД");

            bank.removeAccountFromClient(clientId, accountId);

            try {
                bank.getAccount(accountId);
                throw new IllegalStateException("Тестовый счёт остался в БД");
            } catch (AccountNotFoundException e) {
                System.out.println("После удаления: счёт " + accountId + " отсутствует в БД");
            }
        }
    }
}
