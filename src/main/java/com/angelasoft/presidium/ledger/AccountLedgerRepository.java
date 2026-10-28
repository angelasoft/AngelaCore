package com.angelasoft.presidium.ledger;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountLedgerRepository extends JpaRepository<AccountLedger, Long> {

    Optional<AccountLedger> findByPresidiumIdAndYearAndMonth(Long presidiumId, int year, int month);
}
