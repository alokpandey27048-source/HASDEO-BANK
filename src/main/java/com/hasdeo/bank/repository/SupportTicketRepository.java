package com.hasdeo.bank.repository;
import com.hasdeo.bank.model.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SupportTicketRepository extends JpaRepository<SupportTicket,Long> {
 List<SupportTicket> findTop100ByOrderByCreatedAtDesc();
}
