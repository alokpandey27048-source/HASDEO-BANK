package com.hasdeo.bank.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "backup_runs", indexes = @Index(name = "idx_backup_runs_started", columnList = "started_at"))
public class BackupRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "drive_file_id", length = 200) private String driveFileId;
    @Column(name = "drive_file_name", length = 240) private String driveFileName;
    @Column(name = "file_size_bytes") private Long fileSizeBytes;
    @Column(length = 1000) private String message;

    public BackupRun() { this.startedAt = Instant.now(); this.status = "RUNNING"; }
    public void succeed(String fileId, String fileName, long size) { this.status="SUCCESS"; this.driveFileId=fileId; this.driveFileName=fileName; this.fileSizeBytes=size; this.finishedAt=Instant.now(); this.message="Dump restored successfully into an isolated temporary PostgreSQL 18 instance, then uploaded to Google Drive and file metadata verified."; }
    public void addWarning(String warning) { String combined = (this.message == null ? "" : this.message + " ") + warning; this.message = combined.substring(0, Math.min(990, combined.length())); }
    public void fail(String reason) { this.status="FAILED"; this.finishedAt=Instant.now(); this.message=reason == null ? "Backup failed." : reason.substring(0, Math.min(990, reason.length())); }
    public Long getId(){return id;} public Instant getStartedAt(){return startedAt;} public Instant getFinishedAt(){return finishedAt;}
    public String getStatus(){return status;} public String getDriveFileId(){return driveFileId;} public String getDriveFileName(){return driveFileName;}
    public Long getFileSizeBytes(){return fileSizeBytes;} public String getMessage(){return message;}
}
