package com.hasdeo.bank.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hasdeo.bank.model.BackupRun;
import com.hasdeo.bank.repository.BackupRunRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class GoogleDriveBackupService {
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String DRIVE_API = "https://www.googleapis.com/drive/v3/files";
    private static final String DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3/files";
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(IST);

    private final BackupRunRepository runs;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(20)).build();
    private final AtomicBoolean backupInProgress = new AtomicBoolean(false);

    @Value("${BACKUP_DB_URL:${spring.datasource.url:}}") private String jdbcUrl;
    @Value("${BACKUP_DB_USERNAME:${spring.datasource.username:}}") private String dbUsername;
    @Value("${BACKUP_DB_PASSWORD:${spring.datasource.password:}}") private String dbPassword;
    @Value("${GOOGLE_SERVICE_ACCOUNT_JSON_BASE64:}") private String serviceAccountBase64;
    @Value("${GOOGLE_OAUTH_CLIENT_ID:}") private String oauthClientId;
    @Value("${GOOGLE_OAUTH_CLIENT_SECRET:}") private String oauthClientSecret;
    @Value("${GOOGLE_OAUTH_REFRESH_TOKEN:}") private String oauthRefreshToken;
    @Value("${GOOGLE_DRIVE_FOLDER_ID:}") private String driveFolderId;
    @Value("${BACKUP_RETENTION_COUNT:14}") private int retentionCount;
    @Value("${BACKUP_MAX_SIZE_MB:250}") private long maxSizeMb;

    public GoogleDriveBackupService(BackupRunRepository runs, ObjectMapper mapper) {
        this.runs = runs; this.mapper = mapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedRuns() {
        for (BackupRun interrupted : runs.findByStatus("RUNNING")) {
            interrupted.fail("The application restarted before this backup run could finish.");
            runs.save(interrupted);
        }
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Kolkata")
    public void scheduledBackup() { runBackup(); }

    public Map<String,Object> runBackup() {
        if (!backupInProgress.compareAndSet(false, true)) return Map.of("status", "RUNNING", "message", "A backup is already running.");
        try { return executeBackup(); }
        finally { backupInProgress.set(false); }
    }

    public Map<String,Object> startBackup() {
        if (!backupInProgress.compareAndSet(false, true)) return Map.of("status", "RUNNING", "message", "A backup is already running.");
        CompletableFuture.runAsync(() -> {
            try { executeBackup(); }
            finally { backupInProgress.set(false); }
        });
        return Map.of("status", "RUNNING", "message", "Backup started. Refresh the status panel in a few seconds.");
    }

    private Map<String,Object> executeBackup() {
        BackupRun run = runs.save(new BackupRun());
        Path dump = null;
        try {
            ensureConfigured();
            dump = Files.createTempFile("hasdeo-bank-", ".dump");
            createDatabaseDump(dump);
            long size = Files.size(dump);
            if (size <= 0 || size > maxSizeMb * 1024L * 1024L) throw new IllegalStateException("Backup file is empty or exceeds BACKUP_MAX_SIZE_MB.");
            dump.toFile().setReadable(true, false);
            verifyDump(dump);
            String token = accessToken();
            JsonNode uploaded = uploadToDrive(token, dump, size);
            String fileId = required(uploaded, "id");
            String fileName = required(uploaded, "name");
            JsonNode verified = driveGet(token, DRIVE_API + "/" + url(fileId) + "?fields=id,name,size,md5Checksum,trashed&supportsAllDrives=true");
            String localMd5 = md5(dump);
            String driveMd5 = verified.path("md5Checksum").asText("");
            if (!fileId.equals(required(verified,"id")) || !fileName.equals(required(verified,"name")) || number(verified,"size") != size || !localMd5.equalsIgnoreCase(driveMd5) || verified.path("trashed").asBoolean(true)) {
                throw new IllegalStateException("Google Drive upload verification did not match the uploaded backup file size or checksum.");
            }
            run.succeed(fileId, fileName, size);
            runs.save(run);
            try { applyRetention(token, fileId); } catch (Exception cleanupError) { run.addWarning("Backup verified; retention cleanup warning: " + safeMessage(cleanupError)); runs.save(run); }
            return runMap(run);
        } catch (Exception ex) {
            run.fail(safeMessage(ex)); runs.save(run);
            return runMap(run);
        } finally {
            if (dump != null) try { Files.deleteIfExists(dump); } catch (IOException ignored) {}
        }
    }

    public Map<String,Object> status() {
        List<BackupRun> history = runs.findTop20ByOrderByStartedAtDesc();
        BackupRun latest = history.isEmpty() ? null : history.get(0);
        BackupRun latestSuccess = runs.findFirstByStatusOrderByFinishedAtDesc("SUCCESS").orElse(null);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("configured", isConfigured());
        out.put("running", backupInProgress.get());
        out.put("status", backupInProgress.get() ? "RUNNING" : (latest == null ? (isConfigured()?"NEVER_RUN":"NOT_CONFIGURED") : latest.getStatus()));
        out.put("message", backupInProgress.get() ? "Backup is running: database dump, isolated restore test and Drive verification are in progress." : (latest == null ? (isConfigured()?"Google Drive is configured. First scheduled backup is at 2:00 AM IST.":"Set Google Drive OAuth credentials and folder environment variables to enable backups.") : latest.getMessage()));
        out.put("lastBackup", latestSuccess == null ? "Not available" : latestSuccess.getFinishedAt().atZone(IST).format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a z")));
        out.put("fileName", latestSuccess == null ? "—" : Objects.toString(latestSuccess.getDriveFileName(),"—"));
        out.put("fileSizeBytes", latestSuccess == null ? 0 : latestSuccess.getFileSizeBytes());
        out.put("retentionCount", retentionCount);
        out.put("schedule", "Daily at 02:00 Asia/Kolkata while the service is running");
        out.put("history", history.stream().map(this::runMap).toList());
        return out;
    }

    private boolean hasGoogleAuth() {
        boolean serviceAccount = !serviceAccountBase64.isBlank();
        boolean oauthUser = !oauthClientId.isBlank() && !oauthClientSecret.isBlank() && !oauthRefreshToken.isBlank();
        return serviceAccount || oauthUser;
    }
    private boolean isConfigured() { return hasGoogleAuth() && !driveFolderId.isBlank() && !jdbcUrl.isBlank() && !dbUsername.isBlank() && !dbPassword.isBlank(); }
    private void ensureConfigured() {
        if (!isConfigured()) throw new IllegalStateException("Backup is not configured. Set Google Drive OAuth credentials (or a service-account key with Shared Drive access), GOOGLE_DRIVE_FOLDER_ID and the database environment variables.");
        if (retentionCount < 1 || retentionCount > 100) throw new IllegalStateException("BACKUP_RETENTION_COUNT must be between 1 and 100.");
    }

    private void createDatabaseDump(Path target) throws Exception {
        URI db = jdbcUri();
        List<String> command = new ArrayList<>(List.of("pg_dump", "--format=custom", "--no-owner", "--no-acl", "--host", db.getHost(), "--port", String.valueOf(db.getPort() < 0 ? 5432 : db.getPort()), "--username", dbUsername, "--dbname", db.getPath().replaceFirst("^/", ""), "--file", target.toString()));
        Path logFile = Files.createTempFile("hasdeo-pgdump-", ".log");
        try {
            ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(logFile.toFile());
            pb.environment().put("PGPASSWORD", dbPassword);
            pb.environment().put("PGSSLMODE", queryValue(db.getQuery(), "sslmode", "require"));
            Process process = pb.start();
            boolean ended = process.waitFor(4, java.util.concurrent.TimeUnit.MINUTES);
            if (!ended) { process.destroyForcibly(); throw new IllegalStateException("pg_dump timed out after 4 minutes."); }
            if (process.exitValue()!=0) throw new IllegalStateException("pg_dump failed: " + Files.readString(logFile, StandardCharsets.UTF_8));
        } finally { Files.deleteIfExists(logFile); }
    }

    private void verifyDump(Path file) throws Exception {
        Path cluster = Files.createTempDirectory("hasdeo-restore-test-");
        Path log = Files.createTempFile("hasdeo-restore-test-", ".log");
        log.toFile().setReadable(true, false); log.toFile().setWritable(true, false);
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        String dataDir = cluster.toAbsolutePath().toString();
        boolean started = false;
        try {
            runCommand(List.of("chown", "-R", "postgres:postgres", dataDir), 30, log);
            runAsPostgres(List.of("/usr/lib/postgresql/18/bin/initdb", "-D", dataDir, "-A", "trust", "--no-locale", "-E", "UTF8"), 90, log);
            started = true;
            runAsPostgres(List.of("/usr/lib/postgresql/18/bin/pg_ctl", "-D", dataDir, "-o", "-h 127.0.0.1 -p " + port + " -F -c shared_buffers=16MB -c max_connections=10 -c work_mem=2MB", "-w", "start"), 60, log);
            runAsPostgres(List.of("/usr/bin/createdb", "-h", "127.0.0.1", "-p", String.valueOf(port), "-U", "postgres", "hasdeo_restore_test"), 30, log);
            runAsPostgres(List.of("/usr/lib/postgresql/18/bin/pg_restore", "--exit-on-error", "--no-owner", "--no-acl", "-h", "127.0.0.1", "-p", String.valueOf(port), "-U", "postgres", "-d", "hasdeo_restore_test", file.toString()), 180, log);
        } finally {
            if (started) {
                try { runAsPostgres(List.of("/usr/lib/postgresql/18/bin/pg_ctl", "-D", dataDir, "-m", "fast", "-w", "stop"), 30, log); }
                catch (Exception ignored) {}
            }
            deleteTree(cluster);
            Files.deleteIfExists(log);
        }
    }

    private void runAsPostgres(List<String> command, int timeoutSeconds, Path log) throws Exception {
        List<String> full = new ArrayList<>(List.of("runuser", "-u", "postgres", "--")); full.addAll(command);
        runCommand(full, timeoutSeconds, log);
    }

    private void runCommand(List<String> command, int timeoutSeconds, Path log) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean ended = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        if (!ended) { process.destroyForcibly(); throw new IllegalStateException("Restore verification command timed out: " + command.get(0)); }
        if (process.exitValue()!=0) throw new IllegalStateException("Restore verification failed: " + Files.readString(log, StandardCharsets.UTF_8));
    }

    private void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) {} }); }
        catch (IOException ignored) {}
    }

    private URI jdbcUri() {
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) throw new IllegalStateException("DB_URL must be a PostgreSQL JDBC URL.");
        return URI.create(jdbcUrl.substring("jdbc:".length()));
    }
    private String queryValue(String query,String key,String fallback) {
        if(query!=null) for(String pair:query.split("&")){String[] p=pair.split("=",2);if(p.length==2&&p[0].equals(key))return p[1];}
        return fallback;
    }

    private String accessToken() throws Exception {
        if (serviceAccountBase64.isBlank()) return oauthUserAccessToken();
        JsonNode account = mapper.readTree(Base64.getDecoder().decode(serviceAccountBase64));
        String email = required(account,"client_email");
        String privatePem = required(account,"private_key").replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----","").replaceAll("\\s","");
        byte[] keyBytes = Base64.getDecoder().decode(privatePem);
        var privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        long now = Instant.now().getEpochSecond();
        String header = base64Url(mapper.writeValueAsBytes(Map.of("alg","RS256","typ","JWT")));
        String claims = base64Url(mapper.writeValueAsBytes(Map.of("iss",email,"scope","https://www.googleapis.com/auth/drive","aud",TOKEN_URL,"iat",now,"exp",now+3600)));
        String unsigned = header + "." + claims;
        Signature signature = Signature.getInstance("SHA256withRSA"); signature.initSign(privateKey); signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
        String assertion = unsigned + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        String form = "grant_type="+url("urn:ietf:params:oauth:grant-type:jwt-bearer")+"&assertion="+url(assertion);
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL)).timeout(java.time.Duration.ofSeconds(30)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
        JsonNode response = sendJson(request);
        return required(response,"access_token");
    }


    private String oauthUserAccessToken() throws Exception {
        String form = "grant_type=refresh_token" +
                "&client_id=" + url(oauthClientId) +
                "&client_secret=" + url(oauthClientSecret) +
                "&refresh_token=" + url(oauthRefreshToken);
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL)).timeout(java.time.Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        return required(sendJson(request), "access_token");
    }

    private JsonNode uploadToDrive(String token,Path file,long size) throws Exception {
        String metadata = mapper.writeValueAsString(Map.of(
                "name", "HASDEO-BANK-"+FILE_TIME.format(Instant.now())+"-IST.dump",
                "parents", List.of(driveFolderId),
                "description", "Automated PostgreSQL custom-format backup for the HASDEO BANK educational demo."));
        HttpRequest create = HttpRequest.newBuilder(URI.create(DRIVE_UPLOAD+"?uploadType=resumable&supportsAllDrives=true&fields=id,name,size,md5Checksum"))
                .timeout(java.time.Duration.ofSeconds(30))
                .header("Authorization", "Bearer "+token)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("X-Upload-Content-Type", "application/octet-stream")
                .header("X-Upload-Content-Length", String.valueOf(size))
                .POST(HttpRequest.BodyPublishers.ofString(metadata)).build();
        HttpResponse<String> created = http.send(create, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (created.statusCode()<200 || created.statusCode()>=300) throw new IllegalStateException("Google Drive upload session failed (HTTP "+created.statusCode()+"): "+created.body());
        String uploadUrl = created.headers().firstValue("Location").orElseThrow(() -> new IllegalStateException("Google Drive did not return a resumable upload URL."));
        HttpRequest put = HttpRequest.newBuilder(URI.create(uploadUrl)).timeout(java.time.Duration.ofMinutes(8))
                .header("Authorization", "Bearer "+token)
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofFile(file)).build();
        HttpResponse<String> uploaded = http.send(put, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (uploaded.statusCode()<200 || uploaded.statusCode()>=300) throw new IllegalStateException("Google Drive file upload failed (HTTP "+uploaded.statusCode()+"): "+uploaded.body());
        return mapper.readTree(uploaded.body());
    }

    private void applyRetention(String token,String newestId) throws Exception {
        String q = "'"+driveFolderId.replace("'","\\'")+"' in parents and trashed = false and name contains 'HASDEO-BANK-'";
        String endpoint = DRIVE_API+"?q="+url(q)+"&pageSize=100&orderBy=createdTime%20desc&fields=files(id,name,createdTime),nextPageToken&supportsAllDrives=true&includeItemsFromAllDrives=true";
        JsonNode response = driveGet(token,endpoint);
        List<JsonNode> files = new ArrayList<>(); response.path("files").forEach(files::add);
        files.sort(Comparator.comparing((JsonNode n)->n.path("createdTime").asText("")).reversed());
        for(int i=retentionCount;i<files.size();i++) {
            String id=files.get(i).path("id").asText(); if(id.isBlank()||id.equals(newestId)) continue;
            HttpRequest delete=HttpRequest.newBuilder(URI.create(DRIVE_API+"/"+url(id)+"?supportsAllDrives=true")).timeout(java.time.Duration.ofSeconds(30)).header("Authorization","Bearer "+token).DELETE().build();
            HttpResponse<String> r=http.send(delete,HttpResponse.BodyHandlers.ofString());
            if(r.statusCode()!=204 && r.statusCode()!=200 && r.statusCode()!=404) throw new IllegalStateException("Backup uploaded and verified, but retention cleanup failed (HTTP "+r.statusCode()+").");
        }
    }

    private JsonNode driveGet(String token,String endpoint) throws Exception {
        HttpRequest request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(java.time.Duration.ofSeconds(30)).header("Authorization","Bearer "+token).GET().build(); return sendJson(request);
    }
    private JsonNode sendJson(HttpRequest request) throws Exception {
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if(response.statusCode()<200||response.statusCode()>=300) throw new IllegalStateException("Google API returned HTTP "+response.statusCode()+": "+response.body());
        return mapper.readTree(response.body());
    }
    private String required(JsonNode node,String field) { String value=node.path(field).asText(""); if(value.isBlank()) throw new IllegalStateException("Google response or credentials missing field: "+field); return value; }
    private long number(JsonNode node,String field) { try{return Long.parseLong(node.path(field).asText("0"));}catch(Exception e){return 0;} }
    private String md5(Path file) throws Exception {
        var digest = java.security.MessageDigest.getInstance("MD5");
        try (var input = Files.newInputStream(file)) { byte[] buffer = new byte[8192]; int read; while ((read=input.read(buffer))!=-1) digest.update(buffer,0,read); }
        return HexFormat.of().formatHex(digest.digest());
    }
    private String base64Url(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private String url(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
    private String safeMessage(Exception ex) { String s=ex.getMessage(); if(s==null||s.isBlank())s=ex.getClass().getSimpleName(); return s.length()>950?s.substring(0,950):s; }
    private Map<String,Object> runMap(BackupRun run) {
        Map<String,Object> m=new LinkedHashMap<>(); m.put("id",run.getId());m.put("status",run.getStatus());m.put("startedAt",run.getStartedAt()==null?"—":run.getStartedAt().atZone(IST).format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm:ss a z")));
        m.put("finishedAt",run.getFinishedAt()==null?"—":run.getFinishedAt().atZone(IST).format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm:ss a z")));
        m.put("fileName",Objects.toString(run.getDriveFileName(),"—"));m.put("fileSizeBytes",run.getFileSizeBytes()==null?0:run.getFileSizeBytes());m.put("message",Objects.toString(run.getMessage(),""));return m;
    }
}
