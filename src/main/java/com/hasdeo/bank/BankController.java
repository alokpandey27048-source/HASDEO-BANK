package com.hasdeo.bank;

import com.hasdeo.bank.model.*;
import com.hasdeo.bank.repository.*;
import com.hasdeo.bank.service.GoogleDriveBackupService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@RestController
@RequestMapping("/api")
public class BankController {
    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);
    private final AccountStateRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final DemoCustomerRepository customerRepository;
    private final ActivityLogRepository activityRepository;
    private final AdminSessionRepository sessionRepository;
    private final SiteSettingsRepository settingsRepository;
    private final GoogleDriveBackupService backupService;
    private final SupportTicketRepository supportRepository;
    private final com.hasdeo.bank.service.AdminMailService mailService;

    @Value("${ADMIN_USERNAME:}") private String adminUsername;
    @Value("${ADMIN_PASSWORD:}") private String adminPassword;

    public BankController(AccountStateRepository accountRepository, BankTransactionRepository transactionRepository,
                          DemoCustomerRepository customerRepository, ActivityLogRepository activityRepository,
                          AdminSessionRepository sessionRepository, SiteSettingsRepository settingsRepository, GoogleDriveBackupService backupService, SupportTicketRepository supportRepository, com.hasdeo.bank.service.AdminMailService mailService) {
        this.accountRepository=accountRepository; this.transactionRepository=transactionRepository;
        this.customerRepository=customerRepository; this.activityRepository=activityRepository;
        this.sessionRepository=sessionRepository; this.settingsRepository=settingsRepository; this.backupService=backupService; this.supportRepository=supportRepository; this.mailService=mailService;
    }

    private void requireLogin(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn")) && session.getAttribute("customerId") == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in first.");
    }
    private void requireAdmin(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn")) || session.getAttribute("customerId") != null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required.");
    }
    private DemoCustomer currentCustomer(HttpSession session) {
        Object id=session.getAttribute("customerId");
        if(id==null) return null;
        DemoCustomer customer=customerRepository.findById(Long.valueOf(String.valueOf(id))).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Customer account not found."));
        if(!"ACTIVE".equals(customer.getStatus())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This demo customer account is inactive.");
        return customer;
    }
    private String actor(HttpSession session) { return session.getAttribute("customerUsername") != null ? String.valueOf(session.getAttribute("customerUsername")) : String.valueOf(session.getAttribute("adminUsername")); }
    private String sessionHash(HttpSession session) { return sha256(session.getId()); }
    private String sha256(String value) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) { throw new IllegalStateException("Unable to secure session reference.",ex); }
    }
    private void log(HttpSession session,String action,String details) {
        String safeDetails = details == null ? "" : details.substring(0, Math.min(240, details.length()));
        activityRepository.save(new ActivityLog(actor(session),action,safeDetails));
    }

    @PostMapping("/signup") @Transactional
    public Map<String,Object> signup(@RequestBody Map<String,String> body,HttpSession session,HttpServletRequest request) {
        String name=requiredText(body,"name",100), username=requiredText(body,"username",40).toLowerCase(Locale.ROOT);
        String email=optionalText(body.get("email"),120); if(!email.isBlank()&&!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid email address.");
        String password=body.getOrDefault("password","");
        if(!username.matches("[a-z0-9._-]{4,40}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Username must be 4-40 characters (letters, numbers, dot, underscore or hyphen).");
        if(password.length()<8 || password.length()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password must be at least 8 characters.");
        if(customerRepository.existsByUsername(username)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Username already exists.");
        String code=uniqueDigits(4,true), card=uniqueDigits(16,false);
        DemoCustomer c=new DemoCustomer("HBC"+UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase(Locale.ROOT),name,"",email,"Savings");
        c.setUsername(username); c.setPasswordHash(hashPassword(password)); c.setTransferCode(code); c.setCardNumber(card); c.setBalance(BigDecimal.ZERO);
        c=customerRepository.save(c); request.changeSessionId(); session.removeAttribute("loggedIn"); session.removeAttribute("adminUsername"); session.setAttribute("customerId",c.getId()); session.setAttribute("customerUsername",username); session.setMaxInactiveInterval(30*60);
        activityRepository.save(new ActivityLog(username,"CUSTOMER_SIGNUP","New demo customer account created"));
        mailService.notifyAdmin("New customer signup","Customer: "+name+"\nUsername: "+username+"\nTransfer code: "+code);
        return customerSessionMap(c);
    }

    @PostMapping("/customer-login") @Transactional
    public Map<String,Object> customerLogin(@RequestBody Map<String,String> body,HttpSession session,HttpServletRequest request) {
        String username=body.getOrDefault("username","").trim().toLowerCase(Locale.ROOT), password=body.getOrDefault("password","");
        DemoCustomer c=customerRepository.findByUsername(username).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Incorrect username or password."));
        if(!"ACTIVE".equals(c.getStatus()) || !verifyPassword(password,c.getPasswordHash())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Incorrect username or password.");
        request.changeSessionId(); session.removeAttribute("loggedIn"); session.removeAttribute("adminUsername"); session.setAttribute("customerId",c.getId()); session.setAttribute("customerUsername",username); session.setMaxInactiveInterval(30*60);
        activityRepository.save(new ActivityLog(username,"CUSTOMER_LOGIN","Customer signed in")); return customerSessionMap(c);
    }

    private Map<String,Object> customerSessionMap(DemoCustomer c){return Map.of("success",true,"name",c.getName(),"role","CUSTOMER","transferCode",c.getTransferCode());}
    private String uniqueDigits(int length,boolean code){SecureRandom r=new SecureRandom(); for(int i=0;i<1000;i++){StringBuilder b=new StringBuilder();for(int j=0;j<length;j++)b.append(r.nextInt(10));String v=b.toString();if(code?!customerRepository.existsByTransferCode(v):!customerRepository.existsByCardNumber(v))return v;}throw new IllegalStateException("Could not generate unique demo identifier.");}
    private String hashPassword(String password){try{byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,120000,256);byte[] hash=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();return Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(hash);}catch(Exception e){throw new IllegalStateException("Password hashing unavailable.",e);}}
    private boolean verifyPassword(String password,String stored){try{if(stored==null||!stored.contains(":"))return false;String[] p=stored.split(":");byte[] salt=Base64.getDecoder().decode(p[0]),expected=Base64.getDecoder().decode(p[1]);PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,120000,256);byte[] actual=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();return MessageDigest.isEqual(expected,actual);}catch(Exception e){return false;}}

    @PostMapping("/login")
    @Transactional
    public Map<String,Object> login(@RequestBody Map<String,String> body,HttpSession session,HttpServletRequest request) {
        String username=body.getOrDefault("username","").trim();
        String password=body.getOrDefault("password","");
        if(adminUsername.isBlank()||adminPassword.isBlank()||!adminUsername.equals(username)||!adminPassword.equals(password))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Incorrect username or password.");
        request.changeSessionId();
        session.removeAttribute("customerId"); session.removeAttribute("customerUsername");
        session.setAttribute("loggedIn",true); session.setAttribute("adminUsername",username); session.setMaxInactiveInterval(30*60);
        sessionRepository.save(new AdminSessionRecord(sessionHash(session),username));
        activityRepository.save(new ActivityLog(username,"ADMIN_LOGIN","Admin signed in"));
        mailService.notifyAdmin("Admin login","Admin account "+username+" signed in.");
        return Map.of("success",true,"name","HASDEO BANK Admin");
    }

    @PostMapping("/logout")
    @Transactional
    public Map<String,Object> logout(HttpSession session) {
        if(Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            String username=actor(session); sessionRepository.findBySessionHash(sessionHash(session)).ifPresent(AdminSessionRecord::logout);
            activityRepository.save(new ActivityLog(username,"ADMIN_LOGOUT","Admin signed out"));
        } else if(session.getAttribute("customerId")!=null) {
            activityRepository.save(new ActivityLog(actor(session),"CUSTOMER_LOGOUT","Customer signed out"));
        }
        session.invalidate(); return Map.of("success",true);
    }

    @PostMapping("/session/ping")
    @Transactional
    public Map<String,Object> sessionPing(HttpSession session) {
        requireLogin(session);
        sessionRepository.findBySessionHash(sessionHash(session)).ifPresent(AdminSessionRecord::seen);
        return Map.of("success",true);
    }

    @GetMapping("/public/status")
    @Transactional(readOnly=true)
    public Map<String,Object> publicStatus() {
        SiteSettings s=settingsRepository.findById(1L).orElseGet(()->new SiteSettings(1L));
        return Map.of("maintenance",s.isMaintenanceEnabled(),"message",safe(s.getMaintenanceMessage()),"siteTitle",safe(s.getSiteTitle()),"announcement",safe(s.getAnnouncement()));
    }

    @GetMapping("/account") @Transactional(readOnly=true)
    public Map<String,Object> account(HttpSession session) {
        requireLogin(session); DemoCustomer customer=currentCustomer(session);
        BigDecimal balance; String name,card,code,accountType,status; List<BankTransaction> tx;
        if(customer!=null){balance=customer.getBalance();name=customer.getName();card=customer.getCardNumber();code=customer.getTransferCode();accountType=customer.getAccountType();status=customer.getStatus();tx=transactionRepository.findTop100ByCustomerIdOrderByCreatedAtDesc(customer.getId());}
        else {AccountState a=accountRepository.findById(1L).orElseThrow(()->new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Demo account is not initialized."));balance=a.getBalance();name="HASDEO BANK Admin";card="0000000000002701";code="ADMIN";accountType="Premium Savings";status="ACTIVE";tx=transactionRepository.findTop100ByOrderByCreatedAtDesc();}
        Map<String,Object> result=new LinkedHashMap<>();result.put("name",name);result.put("cardNumber",card);result.put("accountNumber",card);result.put("transferCode",code);result.put("accountType",accountType);result.put("status",status);result.put("balance",balance);result.put("role",customer==null?"ADMIN":"CUSTOMER");result.put("transactions",tx.stream().map(this::toTransactionMap).toList());return result;
    }

    @PostMapping("/deposit") @Transactional
    public Map<String,Object> deposit(@RequestBody Map<String,Object> body,HttpSession session) {
        requireLogin(session);BigDecimal amount=getAmount(body);if(amount.signum()<=0||amount.compareTo(new BigDecimal("1000000.00"))>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid amount.");
        DemoCustomer c=currentCustomer(session); if(c!=null)c=customerRepository.findByIdForUpdate(c.getId()).orElseThrow(); Instant now=Instant.now();
        if(c!=null){c.setBalance(c.getBalance().add(amount));transactionRepository.save(new BankTransaction("Demo deposit",amount,"CREDIT",now,c.getId()));}
        else {AccountState a=lockedAccount();a.setBalance(a.getBalance().add(amount));transactionRepository.save(new BankTransaction("Cash deposit",amount,"CREDIT",now));}
        log(session,"DEMO_DEPOSIT","Demo deposit recorded: ₹"+amount);mailService.notifyAdmin("Demo deposit", "Customer/admin: "+actor(session)+"\nAmount: ₹"+amount);return Map.of("success",true,"balance",c==null?accountRepository.findById(1L).orElseThrow().getBalance():c.getBalance());
    }
    @PostMapping("/withdraw") @Transactional
    public Map<String,Object> withdraw(@RequestBody Map<String,Object> body,HttpSession session) {
        requireLogin(session);BigDecimal amount=getAmount(body);if(amount.signum()<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid amount.");DemoCustomer c=currentCustomer(session); if(c!=null)c=customerRepository.findByIdForUpdate(c.getId()).orElseThrow(); Instant now=Instant.now();
        if(c!=null){if(amount.compareTo(c.getBalance())>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Insufficient demo balance.");c.setBalance(c.getBalance().subtract(amount));transactionRepository.save(new BankTransaction("Demo withdrawal",amount,"DEBIT",now,c.getId()));log(session,"DEMO_WITHDRAWAL","Demo withdrawal recorded: ₹"+amount);mailService.notifyAdmin("Demo withdrawal", "Customer: "+c.getName()+" ("+c.getTransferCode()+")\nAmount: ₹"+amount);return Map.of("success",true,"balance",c.getBalance());}
        AccountState a=lockedAccount();if(amount.compareTo(a.getBalance())>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Insufficient demo balance.");a.setBalance(a.getBalance().subtract(amount));transactionRepository.save(new BankTransaction("Cash withdrawal",amount,"DEBIT",now));log(session,"DEMO_WITHDRAWAL","Demo withdrawal recorded: ₹"+amount);mailService.notifyAdmin("Demo withdrawal", "Customer/admin: "+actor(session)+"\nAmount: ₹"+amount);return Map.of("success",true,"balance",a.getBalance());
    }
    @GetMapping("/recipient/{code}") @Transactional(readOnly=true)
    public Map<String,Object> recipient(@PathVariable String code,HttpSession session){requireLogin(session);if(!code.matches("\\d{4}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid 4-digit transfer code.");DemoCustomer c=customerRepository.findByTransferCode(code).filter(x->"ACTIVE".equals(x.getStatus())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"No active customer found for this code."));return Map.of("name",c.getName(),"transferCode",c.getTransferCode());}
    @PostMapping("/transfer") @Transactional
    public Map<String,Object> transfer(@RequestBody Map<String,Object> body,HttpSession session) {
        requireLogin(session);DemoCustomer sender=currentCustomer(session);if(sender==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Only customer demo accounts can transfer to another customer.");BigDecimal amount=getAmount(body);String code=String.valueOf(body.getOrDefault("recipientCode","")).trim();
        if(!code.matches("\\d{4}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter the recipient's 4-digit transfer code.");if(amount.signum()<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid amount.");
        DemoCustomer receiverRef=customerRepository.findByTransferCode(code).filter(x->"ACTIVE".equals(x.getStatus())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Recipient not found."));if(receiverRef.getId().equals(sender.getId()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"You cannot transfer to your own account.");Long firstId=Math.min(sender.getId(),receiverRef.getId()),secondId=Math.max(sender.getId(),receiverRef.getId());DemoCustomer first=customerRepository.findByIdForUpdate(firstId).orElseThrow(),second=customerRepository.findByIdForUpdate(secondId).orElseThrow();sender=sender.getId().equals(firstId)?first:second;DemoCustomer receiver=receiverRef.getId().equals(firstId)?first:second;if(amount.compareTo(sender.getBalance())>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Insufficient demo balance.");
        sender.setBalance(sender.getBalance().subtract(amount));receiver.setBalance(receiver.getBalance().add(amount));Instant now=Instant.now();transactionRepository.save(new BankTransaction("Transfer sent to "+receiver.getName()+" ("+code+")",amount,"DEBIT",now,sender.getId()));transactionRepository.save(new BankTransaction("Transfer received from "+sender.getName(),amount,"CREDIT",now,receiver.getId()));log(session,"DEMO_TRANSFER","Demo transfer sent to code "+code+": ₹"+amount);mailService.notifyAdmin("Demo transfer", "Sender: "+sender.getName()+" ("+sender.getTransferCode()+")\nReceiver: "+receiver.getName()+" ("+code+")\nAmount: ₹"+amount);return Map.of("success",true,"balance",sender.getBalance(),"recipientName",receiver.getName());
    }

    @PostMapping("/support") @Transactional
    public Map<String,Object> createSupportTicket(@RequestBody Map<String,String> body) {
        String name=requiredText(body,"name",100), email=requiredText(body,"email",120), category=requiredText(body,"category",40), message=requiredText(body,"message",1200);
        if(!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid email address.");
        SupportTicket ticket=supportRepository.save(new SupportTicket(name,email,category,message));
        mailService.notifyAdmin("New support request #"+ticket.getId(),"Name: "+name+"\nEmail: "+email+"\nCategory: "+category+"\nMessage: "+message);
        return Map.of("success",true,"ticketId",ticket.getId());
    }

    @GetMapping("/owner/support") @Transactional(readOnly=true)
    public List<Map<String,Object>> supportTickets(HttpSession session) {
        requireAdmin(session); return supportRepository.findTop100ByOrderByCreatedAtDesc().stream().map(t->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",t.getId());m.put("name",t.getName());m.put("email",t.getEmail());m.put("category",t.getCategory());m.put("message",t.getMessage());m.put("status",t.getStatus());return timeMap(m,t.getCreatedAt());}).toList();
    }

    @GetMapping("/owner/overview") @Transactional
    public Map<String,Object> ownerOverview(HttpSession session) {
        requireAdmin(session); BigDecimal balance=accountRepository.findById(1L).map(AccountState::getBalance).orElse(BigDecimal.ZERO);
        balance=customerRepository.findAll().stream().map(DemoCustomer::getBalance).reduce(balance, BigDecimal::add);
        expireStaleSessions();
        long active=sessionRepository.findAll().stream().filter(x->"ACTIVE".equals(x.getStatus())).count();
        return Map.of("balance",balance,"customers",customerRepository.count(),"transactions",transactionRepository.count(),"activeSessions",active);
    }

    @GetMapping("/owner/customers") @Transactional(readOnly=true)
    public List<Map<String,Object>> customers(HttpSession session) { requireAdmin(session); return customerRepository.findAll().stream().sorted(Comparator.comparing(DemoCustomer::getCreatedAt).reversed()).map(this::toCustomerMap).toList(); }

    @PostMapping("/owner/customers") @Transactional
    public Map<String,Object> addCustomer(@RequestBody Map<String,String> body,HttpSession session) {
        requireAdmin(session); String name=requiredText(body,"name",100); String contact=optionalText(body.get("contact"),80); String email=optionalText(body.get("email"),120);
        String username=requiredText(body,"username",40).toLowerCase(Locale.ROOT), password=body.getOrDefault("password","");
        if(!username.matches("[a-z0-9._-]{4,40}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Username must be 4-40 characters.");
        if(customerRepository.existsByUsername(username)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Username already exists.");
        if(password.length()<8||password.length()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Customer password must be at least 8 characters.");
        String type=optionalText(body.get("accountType"),30); if(type.isBlank()) type="Savings";
        String code="HBC"+UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase(Locale.ROOT);
        DemoCustomer customer=new DemoCustomer(code,name,contact,email,type);customer.setUsername(username);customer.setPasswordHash(hashPassword(password));customer.setTransferCode(uniqueDigits(4,true));customer.setCardNumber(uniqueDigits(16,false));customer=customerRepository.save(customer); log(session,"CUSTOMER_CREATED","Created demo customer "+code); mailService.notifyAdmin("Customer created by admin","Customer: "+name+"\nUsername: "+username+"\nTransfer code: "+customer.getTransferCode());
        return toCustomerMap(customer);
    }

    @PutMapping("/owner/customers/{id}") @Transactional
    public Map<String,Object> updateCustomer(@PathVariable Long id,@RequestBody Map<String,String> body,HttpSession session) {
        requireAdmin(session); DemoCustomer c=customerRepository.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Customer not found."));
        if(body.containsKey("name")) c.setName(requiredText(body,"name",100));
        if(body.containsKey("contact")) c.setContactLabel(optionalText(body.get("contact"),80));
        if(body.containsKey("email")) c.setEmailLabel(optionalText(body.get("email"),120));
        if(body.containsKey("accountType")) c.setAccountType(optionalText(body.get("accountType"),30));
        if(body.containsKey("status")) { String st=body.get("status").toUpperCase(Locale.ROOT); if(!Set.of("ACTIVE","INACTIVE").contains(st)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid customer status."); c.setStatus(st); }
        log(session,"CUSTOMER_UPDATED","Updated demo customer "+c.getCustomerCode()); return toCustomerMap(c);
    }

    @DeleteMapping("/owner/customers/{id}") @Transactional
    public Map<String,Object> deleteCustomer(@PathVariable Long id,HttpSession session) {
        requireAdmin(session); DemoCustomer c=customerRepository.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Customer not found."));
        String code=c.getCustomerCode(); customerRepository.delete(c); log(session,"CUSTOMER_DELETED","Deleted demo customer "+code); return Map.of("success",true);
    }

    @GetMapping("/owner/transactions") @Transactional(readOnly=true)
    public List<Map<String,Object>> ownerTransactions(HttpSession session) { requireAdmin(session); return transactionRepository.findTop100ByOrderByCreatedAtDesc().stream().map(this::toTransactionMap).toList(); }

    @GetMapping("/owner/activity") @Transactional(readOnly=true)
    public List<Map<String,Object>> activity(HttpSession session) { requireAdmin(session); return activityRepository.findTop100ByOrderByCreatedAtDesc().stream().map(a->timeMap(Map.of("actor",a.getActor(),"action",a.getAction(),"details",safe(a.getDetails())),a.getCreatedAt())).toList(); }

    @GetMapping("/owner/sessions") @Transactional
    public List<Map<String,Object>> sessions(HttpSession session) {
        requireAdmin(session); expireStaleSessions();
        return sessionRepository.findTop100ByOrderByLoginAtDesc().stream().map(s->{Map<String,Object> m=new LinkedHashMap<>();m.put("username",s.getUsername());m.put("status",s.getStatus());m.put("loginAt",formatTime(s.getLoginAt()));m.put("lastSeenAt",formatTime(s.getLastSeenAt()));m.put("logoutAt",s.getLogoutAt()==null?"—":formatTime(s.getLogoutAt()));return m;}).toList();
    }

    @GetMapping("/owner/settings") @Transactional(readOnly=true)
    public Map<String,Object> settings(HttpSession session) { requireAdmin(session); SiteSettings s=getSettings(); return settingsMap(s); }

    @PostMapping("/owner/settings") @Transactional
    public Map<String,Object> updateSettings(@RequestBody Map<String,String> body,HttpSession session) {
        requireAdmin(session); SiteSettings s=getSettings();
        if(body.containsKey("siteTitle")) s.setSiteTitle(requiredText(body,"siteTitle",100));
        if(body.containsKey("announcement")) s.setAnnouncement(optionalText(body.get("announcement"),240));
        log(session,"WEBSITE_SETTINGS_UPDATED","Updated website settings"); return settingsMap(s);
    }

    @PostMapping("/owner/maintenance") @Transactional
    public Map<String,Object> updateMaintenance(@RequestBody Map<String,Object> body,HttpSession session) {
        requireAdmin(session); SiteSettings s=getSettings();
        if(body.containsKey("enabled")) s.setMaintenanceEnabled(Boolean.parseBoolean(String.valueOf(body.get("enabled"))));
        if(body.containsKey("message")) s.setMaintenanceMessage(optionalText(String.valueOf(body.get("message")),300));
        log(session,"MAINTENANCE_CHANGED",s.isMaintenanceEnabled()?"Maintenance mode enabled":"Maintenance mode disabled");
        return Map.of("maintenance",s.isMaintenanceEnabled(),"message",safe(s.getMaintenanceMessage()));
    }

    @GetMapping("/owner/mail-status")
    public Map<String,Object> mailStatus(HttpSession session) {
        requireAdmin(session);
        return mailService.status();
    }

    @PostMapping("/owner/mail-test")
    public Map<String,Object> sendMailTest(HttpSession session) {
        requireAdmin(session);
        Map<String,Object> result = mailService.sendTestNotification();
        log(session,"ADMIN_MAIL_TEST_SENT","Owner sent a Gmail SMTP test notification");
        return result;
    }

    @GetMapping("/owner/time-status")
    public Map<String,Object> timeStatus(HttpSession session) {
        requireAdmin(session);
        Instant now = Instant.now();
        return Map.of("zone","Asia/Kolkata","utc",now.toString(),"ist",now.atZone(INDIA_ZONE).toOffsetDateTime().toString());
    }

    @GetMapping("/owner/backup-status")
    public Map<String,Object> backupStatus(HttpSession session) {
        requireAdmin(session);
        return backupService.status();
    }

    @PostMapping("/owner/backup-run")
    public Map<String,Object> runBackupNow(HttpSession session) {
        requireAdmin(session);
        log(session,"MANUAL_BACKUP_STARTED","Owner requested a manual Google Drive backup");
        Map<String,Object> result=backupService.startBackup();
        log(session,"MANUAL_BACKUP_"+String.valueOf(result.get("status")),String.valueOf(result.getOrDefault("message","Backup run finished")));
        return result;
    }

    private void expireStaleSessions() {
        Instant cutoff=Instant.now().minusSeconds(30*60);
        sessionRepository.findByStatusAndLastSeenAtBefore("ACTIVE",cutoff).forEach(AdminSessionRecord::expire);
    }
    private SiteSettings getSettings(){return settingsRepository.findById(1L).orElseGet(()->settingsRepository.save(new SiteSettings(1L)));}
    private AccountState lockedAccount(){return accountRepository.findByIdForUpdate(1L).orElseThrow(()->new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Demo account is not initialized."));}
    private BigDecimal getAmount(Map<String,Object> body){try{BigDecimal v=new BigDecimal(String.valueOf(body.get("amount"))).setScale(2,RoundingMode.UNNECESSARY);if(v.precision()>14)throw new NumberFormatException();return v;}catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid amount with up to two decimal places.");}}
    private String requiredText(Map<String,String> body,String key,int max){String v=body.getOrDefault(key,"").trim();if(v.isEmpty()||v.length()>max)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid "+key+" (max "+max+" characters).");return v;}
    private String optionalText(String v,int max){String s=v==null?"":v.trim();if(s.length()>max)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Text is too long (max "+max+" characters).");return s;}
    private String safe(String v){return v==null?"":v;}
    private String formatTime(Instant instant){return instant==null?"—":instant.atZone(INDIA_ZONE).format(DATE_FORMAT);}
    private Map<String,Object> timeMap(Map<String,Object> base,Instant instant){Map<String,Object> m=new LinkedHashMap<>(base);m.put("timestamp",instant.atZone(INDIA_ZONE).toOffsetDateTime().toString());m.put("date",formatTime(instant));return m;}
    private Map<String,Object> toTransactionMap(BankTransaction t){Map<String,Object> m=new LinkedHashMap<>();m.put("id","HB"+t.getId());m.put("description",t.getDescription());m.put("amount",t.getAmount());m.put("type",t.getType());return timeMap(m,t.getCreatedAt());}
    private Map<String,Object> toCustomerMap(DemoCustomer c){Map<String,Object> m=new LinkedHashMap<>();m.put("id",c.getId());m.put("customerCode",c.getCustomerCode());m.put("name",c.getName());m.put("contact",safe(c.getContactLabel()));m.put("email",safe(c.getEmailLabel()));m.put("username",safe(c.getUsername()));m.put("accountType",c.getAccountType());m.put("status",c.getStatus());m.put("createdAt",formatTime(c.getCreatedAt()));m.put("cardNumber",safe(c.getCardNumber()));m.put("transferCode",safe(c.getTransferCode()));m.put("balance",c.getBalance());return m;}
    private Map<String,Object> settingsMap(SiteSettings s){return Map.of("siteTitle",safe(s.getSiteTitle()),"announcement",safe(s.getAnnouncement()),"maintenance",s.isMaintenanceEnabled(),"maintenanceMessage",safe(s.getMaintenanceMessage()));}
}
