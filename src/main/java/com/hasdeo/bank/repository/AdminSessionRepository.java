package com.hasdeo.bank.repository;
import com.hasdeo.bank.model.AdminSessionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
public interface AdminSessionRepository extends JpaRepository<AdminSessionRecord,Long> {
 Optional<AdminSessionRecord> findBySessionHash(String sessionHash);
 List<AdminSessionRecord> findTop100ByOrderByLoginAtDesc();
 List<AdminSessionRecord> findByRoleOrderByLastSeenAtDesc(String role);
 List<AdminSessionRecord> findByStatusAndLastSeenAtBefore(String status, Instant cutoff);
}
