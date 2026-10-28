package com.angelasoft.presidium.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회계 장부의 한 줄 (수입 또는 지출 한 건). 같은 날짜 안에서는 입력한 순서(id)대로 잔액을 계산한다. */
@Entity
@Table(name = "account_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_id", nullable = false)
    private AccountLedger ledger;

    @Column(nullable = false)
    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EntryType type;

    /** 지출 항목 (꽃 값·의연금·기타). 수입은 null. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ExpenseCategory category;

    /** 적요. 꽃 값·의연금은 항목 이름, 기타와 수입은 직접 입력한 내용. */
    @Column(nullable = false, length = 100)
    private String description;

    @Column(nullable = false)
    private long amount;

    @Builder
    private AccountEntry(
            AccountLedger ledger,
            LocalDate entryDate,
            EntryType type,
            ExpenseCategory category,
            String description,
            long amount
    ) {
        this.ledger = ledger;
        this.entryDate = entryDate;
        this.type = type;
        this.category = category;
        this.description = description;
        this.amount = amount;
    }

    void update(LocalDate entryDate, EntryType type, ExpenseCategory category, String description, long amount) {
        this.entryDate = entryDate;
        this.type = type;
        this.category = category;
        this.description = description;
        this.amount = amount;
    }

    /** 잔액에 더할 값. 수입은 +, 지출은 -. */
    long signedAmount() {
        return type == EntryType.INCOME ? amount : -amount;
    }
}
