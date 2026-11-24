package com.hasdeo.bank.repository;
import com.hasdeo.bank.model.BankTransaction; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;
public interface BankTransactionRepository extends JpaRepository<BankTransaction,Long> {
 List<BankTransaction> findTop100ByOrderByCreatedAtDesc();
 List<BankTransaction> findTop100ByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
