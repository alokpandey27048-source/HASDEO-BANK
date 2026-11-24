package com.hasdeo.bank.repository;
import com.hasdeo.bank.model.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ActivityLogRepository extends JpaRepository<ActivityLog,Long> { List<ActivityLog> findTop100ByOrderByCreatedAtDesc(); List<ActivityLog> findByActionInOrderByCreatedAtDesc(List<String> actions); }
