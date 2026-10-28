package com.angelasoft.presidium.ledger;

import java.time.LocalDate;
import java.util.List;

public final class LedgerDtos {

    private LedgerDtos() {
    }

    /** 전월 이월금: 수입 누계, 지출 누계, 잔액. */
    public record CarryoverRequest(Long income, Long expense, Long balance) {
    }

    /** 장부 한 줄. 지출이면 category 필수, 수입·기타 지출이면 description 필수. */
    public record EntryRequest(LocalDate date, EntryType type, ExpenseCategory category, String description, Long amount) {
    }

    public record Amounts(long income, long expense, long balance) {
    }

    public record Totals(long income, long expense) {
    }

    public record EntryResponse(
            Long id,
            LocalDate date,
            EntryType type,
            ExpenseCategory category,
            String categoryLabel,
            String description,
            long amount,
            /** 이 줄까지 반영한 잔액. */
            long balance
    ) {
    }

    /** 장부 하단 지출 내역. */
    public record ExpenseBreakdown(long donation, long flower, long other) {
    }

    /**
     * 한 달 장부. exists=false 이면 아직 장부가 없고, suggestedCarryover 에 전월 장부의 누계(있으면)를 담는다.
     * previousCumulative 는 전월 장부가 있을 때 그 누계로, 저장된 이월금과 다른지 앱에서 비교할 수 있다.
     */
    public record LedgerResponse(
            boolean exists,
            int year,
            int month,
            boolean canEdit,
            Amounts carryover,
            List<EntryResponse> entries,
            Totals monthly,
            Amounts cumulative,
            ExpenseBreakdown expenseBreakdown,
            long finalBalance,
            Amounts previousCumulative,
            Amounts suggestedCarryover
    ) {
    }
}
