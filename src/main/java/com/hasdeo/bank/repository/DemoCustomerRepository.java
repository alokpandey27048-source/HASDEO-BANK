package com.hasdeo.bank.repository;
import com.hasdeo.bank.model.DemoCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface DemoCustomerRepository extends JpaRepository<DemoCustomer,Long> {
 boolean existsByCustomerCode(String code); boolean existsByUsername(String username); boolean existsByCardNumber(String cardNumber); boolean existsByTransferCode(String transferCode);
 Optional<DemoCustomer> findByUsername(String username); Optional<DemoCustomer> findByTransferCode(String transferCode); Optional<DemoCustomer> findByQrToken(String qrToken);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from DemoCustomer c where c.id=:id") Optional<DemoCustomer> findByIdForUpdate(@Param("id") Long id);
}
