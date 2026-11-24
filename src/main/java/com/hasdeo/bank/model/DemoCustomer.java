package com.hasdeo.bank.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "demo_customers", indexes = @Index(name = "idx_demo_customer_created", columnList = "created_at"))
public class DemoCustomer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="customer_code", nullable=false, unique=true, length=24) private String customerCode;
    @Column(nullable=false, length=100) private String name;
    @Column(name="contact_label", length=80) private String contactLabel;
    @Column(name="email_label", length=120) private String emailLabel;
    @Column(name="account_type", nullable=false, length=30) private String accountType;
    @Column(nullable=false, length=16) private String status;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="username", unique=true, length=40) private String username;
    @Column(name="password_hash", length=300) private String passwordHash;
    @Column(name="card_number", unique=true, length=16) private String cardNumber;
    @Column(name="transfer_code", unique=true, length=4) private String transferCode;
    @Column(name="card_tier", length=24) private String cardTier = "Classic";
    @Column(name="qr_token", unique=true, length=64) private String qrToken;
    @Column(precision=20, scale=2) private BigDecimal balance = BigDecimal.ZERO;

    protected DemoCustomer() {}
    public DemoCustomer(String customerCode,String name,String contactLabel,String emailLabel,String accountType) {
        this.customerCode=customerCode; this.name=name; this.contactLabel=contactLabel; this.emailLabel=emailLabel;
        this.accountType=accountType; this.cardTier="Classic"; this.status="ACTIVE"; this.createdAt=Instant.now(); this.balance=BigDecimal.ZERO;
    }
    public Long getId(){return id;} public String getCustomerCode(){return customerCode;} public String getName(){return name;}
    public String getContactLabel(){return contactLabel;} public String getEmailLabel(){return emailLabel;} public String getAccountType(){return accountType;}
    public String getStatus(){return status;} public Instant getCreatedAt(){return createdAt;} public String getUsername(){return username;}
    public String getPasswordHash(){return passwordHash;} public String getCardNumber(){return cardNumber;} public String getTransferCode(){return transferCode;} public String getCardTier(){return cardTier;} public String getQrToken(){return qrToken;}
    public BigDecimal getBalance(){return balance==null?BigDecimal.ZERO:balance;}
    public void setName(String v){name=v;} public void setContactLabel(String v){contactLabel=v;} public void setEmailLabel(String v){emailLabel=v;}
    public void setAccountType(String v){accountType=v;} public void setStatus(String v){status=v;} public void setUsername(String v){username=v;}
    public void setPasswordHash(String v){passwordHash=v;} public void setCardNumber(String v){cardNumber=v;} public void setTransferCode(String v){transferCode=v;} public void setCardTier(String v){cardTier=v;} public void setQrToken(String v){qrToken=v;}
    public void setBalance(BigDecimal v){balance=v;}
}
