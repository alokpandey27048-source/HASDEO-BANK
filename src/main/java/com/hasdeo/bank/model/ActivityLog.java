package com.hasdeo.bank.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "owner_activity_logs", indexes = @Index(name = "idx_owner_activity_created", columnList = "created_at"))
public class ActivityLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40) private String actor;
    @Column(nullable = false, length = 60) private String action;
    @Column(length = 240) private String details;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected ActivityLog() {}
    public ActivityLog(String actor,String action,String details){this.actor=actor;this.action=action;this.details=details;this.createdAt=Instant.now();}
    public Long getId(){return id;} public String getActor(){return actor;} public String getAction(){return action;}
    public String getDetails(){return details;} public Instant getCreatedAt(){return createdAt;}
}
