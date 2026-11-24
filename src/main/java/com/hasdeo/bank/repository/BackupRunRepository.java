package com.hasdeo.bank.repository;

import com.hasdeo.bank.model.BackupRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BackupRunRepository extends JpaRepository<BackupRun, Long> {
    List<BackupRun> findTop20ByOrderByStartedAtDesc();
    java.util.Optional<BackupRun> findFirstByStatusOrderByFinishedAtDesc(String status);
    List<BackupRun> findByStatus(String status);
}
