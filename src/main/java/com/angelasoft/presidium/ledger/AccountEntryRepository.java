package com.angelasoft.presidium.ledger;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountEntryRepository extends JpaRepository<AccountEntry, Long> {

    /** 잔액 계산 순서: 날짜, 같은 날이면 입력한 순서. */
    List<AccountEntry> findByLedgerIdOrderByEntryDateAscIdAsc(Long ledgerId);

    Optional<AccountEntry> findByIdAndLedgerId(Long id, Long ledgerId);
}
