package com.minibank;

import java.math.BigDecimal;

public class Main {

    public static void main(String[] args) {
        BankService bank = new BankService();

        Client client = bank.registerClient("Ivan Petrov", "ivan@example.com");
        Account account1 = bank.openAccount(client.getId(), "ACC-001");
        Account account2 = bank.openAccount(client.getId(), "ACC-002");

        // Пополняем account1 "вручную" переводом с самого себя не получится —
        // для демо просто задаём баланс напрямую через первый перевод
        // от несуществующего служебного счёта нет смысла эмулировать,
        // поэтому просто выставим баланс через пакетный доступ в демо:
        account1.setBalance(new BigDecimal("1000.00"));

        System.out.println("=== Обычный перевод ===");
        bank.transfer(account1.getId(), account2.getId(), new BigDecimal("100.00"));
        System.out.println("account1 balance: " + bank.getAccount(account1.getId()).getBalance());
        System.out.println("account2 balance: " + bank.getAccount(account2.getId()).getBalance());

        System.out.println();
        System.out.println("=== Перевод на несуществующий счёт (баг) ===");
        BigDecimal balanceBefore = bank.getAccount(account1.getId()).getBalance();
        try {
            bank.transfer(account1.getId(), 999L, new BigDecimal("50.00"));
        } catch (IllegalArgumentException e) {
            System.out.println("Ошибка (как и ожидалось): " + e.getMessage());
        }
        BigDecimal balanceAfter = bank.getAccount(account1.getId()).getBalance();
        System.out.println("Баланс до попытки перевода: " + balanceBefore);
        System.out.println("Баланс после попытки перевода: " + balanceAfter);
        System.out.println("Деньги пропали: " + (balanceAfter.compareTo(balanceBefore) < 0));
    }
}
