package com.minibank.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class Account {
    private final Long id;
    private final Long clientId;
    private final String accountNumber;
    private BigDecimal balance;

}
