package com.angelasoft.presidium.ledger;

import com.angelasoft.admin.security.AdminUserDetails;
import com.angelasoft.presidium.ledger.LedgerDtos.CarryoverRequest;
import com.angelasoft.presidium.ledger.LedgerDtos.EntryRequest;
import com.angelasoft.presidium.ledger.LedgerDtos.LedgerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 월별 회계 장부 API (인증 필요). {month} 는 "2026-10" 형식. 변경 API 는 바뀐 장부 전체를 돌려준다.
 *
 *   GET    /app/presidiums/me/ledgers/{month}                       장부 (없으면 exists=false + 추천 이월금)
 *   POST   /app/presidiums/me/ledgers/{month}                       장부 만들기 {income, expense, balance}   4간부만
 *   PUT    /app/presidiums/me/ledgers/{month}/carryover             전월 이월금 수정                         4간부만
 *   POST   /app/presidiums/me/ledgers/{month}/entries               내역 추가 {date, type, category, description, amount}
 *   PUT    /app/presidiums/me/ledgers/{month}/entries/{entryId}     내역 수정
 *   DELETE /app/presidiums/me/ledgers/{month}/entries/{entryId}     내역 삭제
 */
@RestController
@RequestMapping("/app/presidiums/me/ledgers/{month}")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    @GetMapping
    public LedgerResponse get(@AuthenticationPrincipal AdminUserDetails principal, @PathVariable String month) {
        return ledgerService.get(principal.getUserId(), month);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerResponse create(
            @AuthenticationPrincipal AdminUserDetails principal,
            @PathVariable String month,
            @RequestBody CarryoverRequest request
    ) {
        return ledgerService.create(principal.getUserId(), month, request);
    }

    @PutMapping("/carryover")
    public LedgerResponse updateCarryover(
            @AuthenticationPrincipal AdminUserDetails principal,
            @PathVariable String month,
            @RequestBody CarryoverRequest request
    ) {
        return ledgerService.updateCarryover(principal.getUserId(), month, request);
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerResponse addEntry(
            @AuthenticationPrincipal AdminUserDetails principal,
            @PathVariable String month,
            @RequestBody EntryRequest request
    ) {
        return ledgerService.addEntry(principal.getUserId(), month, request);
    }

    @PutMapping("/entries/{entryId}")
    public LedgerResponse updateEntry(
            @AuthenticationPrincipal AdminUserDetails principal,
            @PathVariable String month,
            @PathVariable Long entryId,
            @RequestBody EntryRequest request
    ) {
        return ledgerService.updateEntry(principal.getUserId(), month, entryId, request);
    }

    @DeleteMapping("/entries/{entryId}")
    public LedgerResponse deleteEntry(
            @AuthenticationPrincipal AdminUserDetails principal,
            @PathVariable String month,
            @PathVariable Long entryId
    ) {
        return ledgerService.deleteEntry(principal.getUserId(), month, entryId);
    }
}
