package com.minibank.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "client_id", insertable = false, updatable = false)
    private Client client;

    @Column(name = "account_number", nullable = false, length = 34)
    private String accountNumber;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @JsonIgnore
    @OneToMany(mappedBy = "account", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private List<Transaction> transactions = new ArrayList<>();

    public Account(Long id, Long clientId, String accountNumber, BigDecimal balance) {
        this.id = id;
        this.clientId = clientId;
        this.accountNumber = accountNumber;
        this.balance = balance;
    }
    public Account(Client client, String accountNumber) {
        this(null, client.getId(), accountNumber, BigDecimal.ZERO);
        this.client = client;
    }

    public void changeBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
