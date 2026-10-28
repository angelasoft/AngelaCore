package com.angelasoft.presidium.ledger;

import com.angelasoft.admin.user.User;
import com.angelasoft.presidium.Presidium;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 달치 회계 장부. 전월 이월금(수입 누계·지출 누계·잔액)으로 시작하고, 내역은 {@link AccountEntry}.
 * 잔액·월계·누계는 저장하지 않고 조회할 때 계산한다.
 */
@Entity
@Table(
        name = "account_ledgers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"presidium_id", "ledger_year", "ledger_month"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presidium_id", nullable = false)
    private Presidium presidium;

    @Column(name = "ledger_year", nullable = false)
    private int year;

    @Column(name = "ledger_month", nullable = false)
    private int month;

    /** 전월 이월 수입 누계. */
    @Column(nullable = false)
    private long carryoverIncome;

    /** 전월 이월 지출 누계. */
    @Column(nullable = false)
    private long carryoverExpense;

    /** 전월 이월 잔액. */
    @Column(nullable = false)
    private long carryoverBalance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private AccountLedger(
            Presidium presidium,
            int year,
            int month,
            long carryoverIncome,
            long carryoverExpense,
            long carryoverBalance,
            User createdBy
    ) {
        this.presidium = presidium;
        this.year = year;
        this.month = month;
        this.carryoverIncome = carryoverIncome;
        this.carryoverExpense = carryoverExpense;
        this.carryoverBalance = carryoverBalance;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    void updateCarryover(long income, long expense, long balance) {
        this.carryoverIncome = income;
        this.carryoverExpense = expense;
        this.carryoverBalance = balance;
        this.updatedAt = LocalDateTime.now();
    }

    void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
