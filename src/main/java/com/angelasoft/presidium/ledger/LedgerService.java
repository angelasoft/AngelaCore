package com.angelasoft.presidium.ledger;

import com.angelasoft.presidium.PresidiumMembership;
import com.angelasoft.presidium.governance.GovernanceException;
import com.angelasoft.presidium.governance.MembershipLookup;
import com.angelasoft.presidium.ledger.LedgerDtos.Amounts;
import com.angelasoft.presidium.ledger.LedgerDtos.CarryoverRequest;
import com.angelasoft.presidium.ledger.LedgerDtos.EntryRequest;
import com.angelasoft.presidium.ledger.LedgerDtos.EntryResponse;
import com.angelasoft.presidium.ledger.LedgerDtos.ExpenseBreakdown;
import com.angelasoft.presidium.ledger.LedgerDtos.LedgerResponse;
import com.angelasoft.presidium.ledger.LedgerDtos.Totals;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월별 회계 장부. 단원은 조회만, 4간부는 장부 작성(전월 이월금)과 내역 등록·수정·삭제를 할 수 있다.
 *
 * - 잔액: 전월 이월 잔액에서 시작해 날짜·입력 순서대로 수입은 더하고 지출은 뺀다.
 * - 월계: 이번 달 수입·지출 합계. 누계: 전월 이월 수입·지출 누계 + 월계.
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    /** 내역 한 건의 최대 금액 (10억 원). */
    private static final long MAX_AMOUNT = 1_000_000_000L;
    /** 이월 누계의 최대 금액 (1000억 원). */
    private static final long MAX_CARRYOVER = 100_000_000_000L;
    private static final int MAX_DESCRIPTION = 100;

    private final MembershipLookup membershipLookup;
    private final AccountLedgerRepository ledgerRepository;
    private final AccountEntryRepository entryRepository;

    @Transactional(readOnly = true)
    public LedgerResponse get(Long userId, String yearMonth) {
        PresidiumMembership me = membershipLookup.require(userId);
        YearMonth ym = parse(yearMonth);
        Amounts previous = previousCumulative(me.getPresidium().getId(), ym);
        return ledgerRepository.findByPresidiumIdAndYearAndMonth(me.getPresidium().getId(), ym.getYear(), ym.getMonthValue())
                .map(ledger -> toResponse(ledger, me.isOfficer(), previous))
                .orElseGet(() -> new LedgerResponse(
                        false, ym.getYear(), ym.getMonthValue(), me.isOfficer(),
                        null, List.of(), null, null, null, 0, previous, previous));
    }

    @Transactional
    public LedgerResponse create(Long userId, String yearMonth, CarryoverRequest request) {
        PresidiumMembership me = requireOfficer(userId);
        YearMonth ym = parse(yearMonth);
        Long presidiumId = me.getPresidium().getId();
        if (ledgerRepository.findByPresidiumIdAndYearAndMonth(presidiumId, ym.getYear(), ym.getMonthValue()).isPresent()) {
            throw GovernanceException.conflict(ym.getMonthValue() + "월 장부가 이미 있습니다.");
        }
        validateCarryover(request);
        AccountLedger ledger = ledgerRepository.save(AccountLedger.builder()
                .presidium(me.getPresidium())
                .year(ym.getYear())
                .month(ym.getMonthValue())
                .carryoverIncome(request.income())
                .carryoverExpense(request.expense())
                .carryoverBalance(request.balance())
                .createdBy(me.getUser())
                .build());
        return toResponse(ledger, true, previousCumulative(presidiumId, ym));
    }

    @Transactional
    public LedgerResponse updateCarryover(Long userId, String yearMonth, CarryoverRequest request) {
        PresidiumMembership me = requireOfficer(userId);
        YearMonth ym = parse(yearMonth);
        validateCarryover(request);
        AccountLedger ledger = requireLedger(me, ym);
        ledger.updateCarryover(request.income(), request.expense(), request.balance());
        return toResponse(ledger, true, previousCumulative(me.getPresidium().getId(), ym));
    }

    @Transactional
    public LedgerResponse addEntry(Long userId, String yearMonth, EntryRequest request) {
        PresidiumMembership me = requireOfficer(userId);
        YearMonth ym = parse(yearMonth);
        AccountLedger ledger = requireLedger(me, ym);
        Validated v = validateEntry(request, ym);
        entryRepository.save(AccountEntry.builder()
                .ledger(ledger)
                .entryDate(v.date())
                .type(v.type())
                .category(v.category())
                .description(v.description())
                .amount(v.amount())
                .build());
        ledger.touch();
        return toResponse(ledger, true, previousCumulative(me.getPresidium().getId(), ym));
    }

    @Transactional
    public LedgerResponse updateEntry(Long userId, String yearMonth, Long entryId, EntryRequest request) {
        PresidiumMembership me = requireOfficer(userId);
        YearMonth ym = parse(yearMonth);
        AccountLedger ledger = requireLedger(me, ym);
        AccountEntry entry = requireEntry(ledger, entryId);
        Validated v = validateEntry(request, ym);
        entry.update(v.date(), v.type(), v.category(), v.description(), v.amount());
        ledger.touch();
        return toResponse(ledger, true, previousCumulative(me.getPresidium().getId(), ym));
    }

    @Transactional
    public LedgerResponse deleteEntry(Long userId, String yearMonth, Long entryId) {
        PresidiumMembership me = requireOfficer(userId);
        YearMonth ym = parse(yearMonth);
        AccountLedger ledger = requireLedger(me, ym);
        entryRepository.delete(requireEntry(ledger, entryId));
        entryRepository.flush();
        ledger.touch();
        return toResponse(ledger, true, previousCumulative(me.getPresidium().getId(), ym));
    }

    private LedgerResponse toResponse(AccountLedger ledger, boolean canEdit, Amounts previousCumulative) {
        List<AccountEntry> entries = entryRepository.findByLedgerIdOrderByEntryDateAscIdAsc(ledger.getId());
        long balance = ledger.getCarryoverBalance();
        long income = 0;
        long expense = 0;
        long donation = 0;
        long flower = 0;
        long other = 0;
        List<EntryResponse> lines = new ArrayList<>();
        for (AccountEntry e : entries) {
            balance += e.signedAmount();
            if (e.getType() == EntryType.INCOME) {
                income += e.getAmount();
            } else {
                expense += e.getAmount();
                switch (e.getCategory()) {
                    case DONATION -> donation += e.getAmount();
                    case FLOWER -> flower += e.getAmount();
                    case OTHER -> other += e.getAmount();
                }
            }
            lines.add(new EntryResponse(
                    e.getId(), e.getEntryDate(), e.getType(), e.getCategory(),
                    e.getCategory() == null ? null : e.getCategory().getLabel(),
                    e.getDescription(), e.getAmount(), balance));
        }
        Amounts carryover = new Amounts(ledger.getCarryoverIncome(), ledger.getCarryoverExpense(), ledger.getCarryoverBalance());
        Amounts cumulative = new Amounts(carryover.income() + income, carryover.expense() + expense, balance);
        return new LedgerResponse(
                true, ledger.getYear(), ledger.getMonth(), canEdit,
                carryover, lines, new Totals(income, expense), cumulative,
                new ExpenseBreakdown(donation, flower, other), balance, previousCumulative, null);
    }

    /** 전월 장부가 있으면 그 누계 (다음 달 이월금으로 쓴다). */
    private Amounts previousCumulative(Long presidiumId, YearMonth ym) {
        YearMonth prev = ym.minusMonths(1);
        return ledgerRepository.findByPresidiumIdAndYearAndMonth(presidiumId, prev.getYear(), prev.getMonthValue())
                .map(ledger -> toResponse(ledger, false, null).cumulative())
                .orElse(null);
    }

    private PresidiumMembership requireOfficer(Long userId) {
        PresidiumMembership me = membershipLookup.require(userId);
        if (!me.isOfficer()) {
            throw GovernanceException.forbidden("회계 장부는 4간부만 작성·수정할 수 있습니다.");
        }
        return me;
    }

    private AccountLedger requireLedger(PresidiumMembership me, YearMonth ym) {
        return ledgerRepository.findByPresidiumIdAndYearAndMonth(me.getPresidium().getId(), ym.getYear(), ym.getMonthValue())
                .orElseThrow(() -> GovernanceException.notFound(
                        ym.getMonthValue() + "월 장부가 없습니다. 먼저 전월 이월금을 입력해 장부를 만들어 주세요."));
    }

    private AccountEntry requireEntry(AccountLedger ledger, Long entryId) {
        return entryRepository.findByIdAndLedgerId(entryId, ledger.getId())
                .orElseThrow(() -> GovernanceException.notFound("장부 내역을 찾을 수 없습니다."));
    }

    private static void validateCarryover(CarryoverRequest request) {
        if (request.income() == null || request.expense() == null || request.balance() == null) {
            throw GovernanceException.badRequest("전월 이월금의 수입금액, 지출금액, 잔액을 모두 입력해 주세요.");
        }
        if (request.income() < 0 || request.expense() < 0) {
            throw GovernanceException.badRequest("수입금액과 지출금액은 0원 이상이어야 합니다.");
        }
        if (request.income() > MAX_CARRYOVER || request.expense() > MAX_CARRYOVER
                || Math.abs(request.balance()) > MAX_CARRYOVER) {
            throw GovernanceException.badRequest("금액이 너무 큽니다.");
        }
    }

    private record Validated(LocalDate date, EntryType type, ExpenseCategory category, String description, long amount) {
    }

    private static Validated validateEntry(EntryRequest request, YearMonth ym) {
        if (request.date() == null || request.type() == null || request.amount() == null) {
            throw GovernanceException.badRequest("날짜, 수입/지출, 금액을 입력해 주세요.");
        }
        if (!YearMonth.from(request.date()).equals(ym)) {
            throw GovernanceException.badRequest(ym.getMonthValue() + "월 날짜만 입력할 수 있습니다.");
        }
        if (request.amount() <= 0 || request.amount() > MAX_AMOUNT) {
            throw GovernanceException.badRequest("금액은 1원 이상 10억 원 이하로 입력해 주세요.");
        }

        String description = request.description() == null ? "" : request.description().trim();
        ExpenseCategory category = null;
        if (request.type() == EntryType.EXPENSE) {
            category = request.category();
            if (category == null) {
                throw GovernanceException.badRequest("지출 항목(꽃 값, 의연금, 기타)을 선택해 주세요.");
            }
            if (category != ExpenseCategory.OTHER) {
                description = description.isEmpty() ? category.getLabel() : description;
            }
        }
        if (description.isEmpty()) {
            throw GovernanceException.badRequest("적요를 입력해 주세요.");
        }
        if (description.length() > MAX_DESCRIPTION) {
            throw GovernanceException.badRequest("적요는 " + MAX_DESCRIPTION + "자 이내로 입력해 주세요.");
        }
        return new Validated(request.date(), request.type(), category, description, request.amount());
    }

    private static YearMonth parse(String yearMonth) {
        try {
            return YearMonth.parse(yearMonth);
        } catch (DateTimeParseException | NullPointerException e) {
            throw GovernanceException.badRequest("월은 2026-10 형식으로 보내 주세요.");
        }
    }
}
