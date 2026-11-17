package com.minibank.http;

import com.minibank.service.BankService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HttpServerApp {
    private static final BankService bankService = new BankService();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Плохой GET-запрос для перевода (как требует первая часть задания)
        server.createContext("/transfer", exchange -> {
            try {
                // Парсим параметры из URL
                Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                Long fromAccountId = Long.parseLong(params.get("fromAccountId"));
                Long toAccountId = Long.parseLong(params.get("toAccountId"));
                BigDecimal amount = new BigDecimal(params.get("amount"));

                // Вызываем бизнес-логику
                bankService.transfer(fromAccountId, toAccountId, amount);

                // Отвечаем 200 OK с текстом "Success"
                String response = "Transfer completed successfully";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } catch (Exception e) {
                // При любой ошибке отвечаем 200 OK с текстом ошибки (как просили)
                String response = "Error: " + e.getMessage();
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("Server started on port 8080");
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length > 1) {
                    result.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
                }
            }
        }
        return result;
    }
}
