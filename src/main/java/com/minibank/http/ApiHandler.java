package com.minibank.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minibank.exception.AccountNotFoundException;
import com.minibank.exception.InsufficientFundsException;
import com.minibank.model.Account;
import com.minibank.model.Client;
import com.minibank.service.BankService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ApiHandler implements HttpHandler {
    private final BankService bankService;
    private final String path;
    private final ObjectMapper objectMapper = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    public ApiHandler(BankService bankService, String path) {
        this.bankService = bankService;
        this.path = path;
    }

    // Направляет запрос в нужный обработчик и обрабатывает ошибки.
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String uri = exchange.getRequestURI().getPath();

            if ("/accounts".equals(path) && uri.matches("/accounts/[0-9]+(/transactions)?")) {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Allow", "GET");
                    sendResponse(exchange, 405, "Method Not Allowed");
                    return;
                }
                handleAccountRead(exchange, uri);
                return;
            }
            if (!path.equals(uri)) {
                sendResponse(exchange, 404, "Not Found");
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            switch (path) {
                case "/clients" -> handleClients(exchange);
                case "/accounts" -> handleAccounts(exchange);
                case "/transfer", "/transfers" -> handleTransfer(exchange);
                default -> sendResponse(exchange, 404, "Not Found");
            }
        } catch (AccountNotFoundException e) {
            sendResponse(exchange, 404, e.getMessage());
        } catch (InsufficientFundsException e) {
            sendResponse(exchange, 409, e.getMessage());
        } catch (IllegalArgumentException e) {
            sendResponse(exchange, 400, e.getMessage());
        } catch (RuntimeException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "Internal Server Error");
        } finally {
            exchange.close();
        }
    }

    // Возвращает данные счёта или историю его операций.
    private void handleAccountRead(HttpExchange exchange, String uri) throws IOException {
        long accountId = Long.parseLong(uri.split("/")[2]);

        if (uri.endsWith("/transactions")) {
            var history = bankService.getTransactions(accountId).stream()
                    .map(tx -> Map.of(
                            "id", tx.getId(),
                            "accountId", tx.getAccountId(),
                            "amount", tx.getAmount(),
                            "type", tx.getType(),
                            "timestamp", tx.getTimestamp().toString()
                    ))
                    .toList();

            sendJsonResponse(exchange, 200, history);
        } else {
            sendJsonResponse(exchange, 200, bankService.getAccount(accountId));
        }
    }

    // Регистрирует клиента по данным из JSON.
    private void handleClients(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readJsonBody(exchange);
        Client client = bankService.registerClient(
                requiredText(body, "name"),
                requiredText(body, "email")
        );
        sendJsonResponse(exchange, 200, client);
    }

    // Открывает счёт для клиента.
    private void handleAccounts(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readJsonBody(exchange);
        Account account = bankService.openAccount(
                Long.parseLong(String.valueOf(body.get("clientId"))),
                requiredText(body, "accountNumber")
        );
        sendJsonResponse(exchange, 200, account);
    }

    // Передаёт данные перевода в BankService.
    private void handleTransfer(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readJsonBody(exchange);
        bankService.transfer(
                Long.parseLong(String.valueOf(body.get("fromAccountId"))),
                Long.parseLong(String.valueOf(body.get("toAccountId"))),
                new BigDecimal(String.valueOf(body.get("amount")))
        );
        sendResponse(exchange, 200, "Transfer completed successfully");
    }

    // Читает тело запроса как JSON-объект.
    private Map<String, Object> readJsonBody(HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> body = objectMapper.readValue(
                    exchange.getRequestBody(),
                    new TypeReference<Map<String, Object>>() {}
            );
            if (body == null) throw new IllegalArgumentException("Expected a JSON object");
            return body;

        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON");
        }
    }

    // Проверяет, что обязательное текстовое поле заполнено.
    private String requiredText(Map<String, Object> body, String field) {
        if (body.get(field) instanceof String text && !text.isBlank()) return text;
        throw new IllegalArgumentException(field + " is required");
    }

    // Отправляет ответ в формате JSON.
    private void sendJsonResponse(HttpExchange exchange, int status, Object body) throws IOException {
        send(exchange, status, "application/json", objectMapper.writeValueAsBytes(body));
    }

    // Отправляет текстовый ответ.
    private void sendResponse(HttpExchange exchange, int status, String message) throws IOException {
        send(exchange, status, "text/plain; charset=UTF-8", message.getBytes(StandardCharsets.UTF_8));
    }

    // Записывает HTTP-статус, заголовки и тело ответа.
    private void send(HttpExchange exchange, int status, String contentType, byte[] bytes)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);

        if ("HEAD".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}