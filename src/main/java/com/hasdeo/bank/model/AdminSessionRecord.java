package com.hasdeo.bank.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "admin_session_history", indexes = @Index(name = "idx_admin_session_login", columnList = "login_at"))
public class AdminSessionRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="session_hash", nullable=false, unique=true, length=64) private String sessionHash;
    @Column(nullable=false, length=60) private String username;
    @Column(name="session_role",nullable=false,length=12,columnDefinition="varchar(12) default 'ADMIN'") private String role;
    @Column(name="login_at", nullable=false) private Instant loginAt;
    @Column(name="last_seen_at", nullable=false) private Instant lastSeenAt;
    @Column(name="logout_at") private Instant logoutAt;
    @Column(nullable=false, length=16) private String status;
    protected AdminSessionRecord() {}
    public AdminSessionRecord(String hash,String username){this(hash,username,"ADMIN");}
    public AdminSessionRecord(String hash,String username,String role){this.sessionHash=hash;this.username=username;this.role=role;this.loginAt=Instant.now();this.lastSeenAt=this.loginAt;this.status="ACTIVE";}
    public Long getId(){return id;} public String getSessionHash(){return sessionHash;} public String getUsername(){return username;}
    public Instant getLoginAt(){return loginAt;} public Instant getLastSeenAt(){return lastSeenAt;} public Instant getLogoutAt(){return logoutAt;} public String getStatus(){return status;} public String getRole(){return role;}
    public void seen(){lastSeenAt=Instant.now();status="ACTIVE";logoutAt=null;}
    public void logout(){logoutAt=Instant.now();status="LOGGED_OUT";}
    public void expire(){status="EXPIRED";}
}
