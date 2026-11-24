package com.hasdeo.bank.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="support_tickets", indexes=@Index(name="idx_support_created_at", columnList="created_at"))
public class SupportTicket {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, length=100) private String name;
    @Column(nullable=false, length=120) private String email;
    @Column(nullable=false, length=40) private String category;
    @Column(nullable=false, length=1200) private String message;
    @Column(nullable=false, length=16) private String status="OPEN";
    @Column(name="created_at", nullable=false) private Instant createdAt=Instant.now();
    protected SupportTicket() {}
    public SupportTicket(String name,String email,String category,String message){this.name=name;this.email=email;this.category=category;this.message=message;this.status="OPEN";this.createdAt=Instant.now();}
    public Long getId(){return id;} public String getName(){return name;} public String getEmail(){return email;} public String getCategory(){return category;} public String getMessage(){return message;} public String getStatus(){return status;} public Instant getCreatedAt(){return createdAt;}
    public void setStatus(String value){status=value;}
}
