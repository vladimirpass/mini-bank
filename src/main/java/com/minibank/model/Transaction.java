package com.minibank.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class Transaction {
    private final Long id;
    private final Long accountId;
    private final BigDecimal amount;
    private final String type;
    private final LocalDateTime timestamp;

}
