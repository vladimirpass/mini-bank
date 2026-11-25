package com.minibank.http;

import com.minibank.service.BankService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class HttpServerApp {
    public static void main(String[] args) throws IOException {
        BankService bankService = new BankService();
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Регистрируем обработчики клиентов, счетов и переводов.
        server.createContext("/clients", new ApiHandler(bankService, "/clients"));
        server.createContext("/accounts", new ApiHandler(bankService, "/accounts"));
        server.createContext("/transfers", new ApiHandler(bankService, "/transfers"));

        server.setExecutor(null);
        server.start();
        System.out.println("Server started on port 8080");
    }
}