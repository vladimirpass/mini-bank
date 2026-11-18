package com.minibank.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minibank.exception.AccountNotFoundException;
import com.minibank.exception.InsufficientFundsException;
import com.minibank.model.Account;
import com.minibank.model.Client;
import com.minibank.service.BankService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class ApiHandler implements HttpHandler {
    private final BankService bankService;
    private final ObjectMapper objectMapper;
    private final String path;

    public ApiHandler(BankService bankService, String path) {
        this.bankService = bankService;
        this.objectMapper = new ObjectMapper();
        this.path = path;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        try {
            switch (path) {
                case "/clients":
                    handleClients(exchange);
                    break;
                case "/accounts":
                    handleAccounts(exchange);
                    break;
                case "/transfer":
                    handleTransfer(exchange);
                    break;
                default:
                    sendResponse(exchange, 404, "Not Found");
            }
        } catch (AccountNotFoundException e) {
            sendResponse(exchange, 404, e.getMessage());
        } catch (InsufficientFundsException e) {
            sendResponse(exchange, 409, e.getMessage());
        } catch (IllegalArgumentException e) {
            sendResponse(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    private void handleClients(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readJsonBody(exchange);
        Client client = bankService.registerClient(
                (String) body.get("name"),
                (String) body.get("email")
        );
        sendJsonResponse(exchange, 200, client);
    }

    private void handleAccounts(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readJsonBody(exchange);
        Account account = bankService.openAccount(
                Long.parseLong(body.get("clientId").toString()),
                (String) body.get("accountNumber")
        );
        sendJsonResponse(exchange, 200, account);
    }

    private void handleTransfer(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readJsonBody(exchange);
        bankService.transfer(
                Long.parseLong(body.get("fromAccountId").toString()),
                Long.parseLong(body.get("toAccountId").toString()),
                new BigDecimal(body.get("amount").toString())
        );
        sendResponse(exchange, 200, "Transfer completed successfully");
    }

    private Map<String, Object> readJsonBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        byte[] bytes = is.readAllBytes();
        return objectMapper.readValue(bytes, HashMap.class);
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object responseObj) throws IOException {
        String response = objectMapper.writeValueAsString(responseObj);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        exchange.sendResponseHeaders(statusCode, message.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(message.getBytes());
        }
    }
}