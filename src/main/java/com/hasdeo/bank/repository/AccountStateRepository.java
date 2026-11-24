package com.hasdeo.bank.repository;

import com.hasdeo.bank.model.AccountState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AccountStateRepository extends JpaRepository<AccountState, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountState a where a.id = :id")
    Optional<AccountState> findByIdForUpdate(@Param("id") Long id);
}
