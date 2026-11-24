package com.hasdeo.bank.model;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant;
@Entity @Table(name="demo_transactions", indexes=@Index(name="idx_demo_tx_created_at",columnList="created_at"))
public class BankTransaction {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=180) private String description;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
 @Column(nullable=false,length=10) private String type;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="customer_id") private Long customerId;
 protected BankTransaction(){}
 public BankTransaction(String description,BigDecimal amount,String type,Instant createdAt){this(description,amount,type,createdAt,null);}
 public BankTransaction(String description,BigDecimal amount,String type,Instant createdAt,Long customerId){this.description=description;this.amount=amount;this.type=type;this.createdAt=createdAt;this.customerId=customerId;}
 public Long getId(){return id;} public String getDescription(){return description;} public BigDecimal getAmount(){return amount;} public String getType(){return type;} public Instant getCreatedAt(){return createdAt;} public Long getCustomerId(){return customerId;}
}
