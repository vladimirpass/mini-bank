package com.minibank;

import com.minibank.service.BankService;

public class ClientAccountsSqlDemo {
    public static void main(String[] args) {
        try (BankService bank = new BankService()) {
            System.out.println("=== Начало замера JOIN FETCH ===");

            var clients = bank.getClientsWithAccounts();
            for (var client : clients) {
                System.out.println("Клиент " + client.getId()
                        + ", счетов: " + client.getAccounts().size());
            }

            System.out.println("Всего клиентов: " + clients.size());
            System.out.println("=== Конец замера ===");
        }
    }
}
