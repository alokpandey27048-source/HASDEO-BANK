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
    private final com.hasdeo.bank.service.SupportAiService supportAiService;

    @Value("${ADMIN_USERNAME:}") private String adminUsername;
    @Value("${ADMIN_PASSWORD:}") private String adminPassword;

    public BankController(AccountStateRepository accountRepository, BankTransactionRepository transactionRepository,
                          DemoCustomerRepository customerRepository, ActivityLogRepository activityRepository,
                          AdminSessionRepository sessionRepository, SiteSettingsRepository settingsRepository, GoogleDriveBackupService backupService, SupportTicketRepository supportRepository, com.hasdeo.bank.service.AdminMailService mailService, com.hasdeo.bank.service.SupportAiService supportAiService) {
        this.accountRepository=accountRepository; this.transactionRepository=transactionRepository;
        this.customerRepository=customerRepository; this.activityRepository=activityRepository;
        this.sessionRepository=sessionRepository; this.settingsRepository=settingsRepository; this.backupService=backupService; this.supportRepository=supportRepository; this.mailService=mailService; this.supportAiService=supportAiService;
    }

    private void requireLogin(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn")) && session.getAttribute("customerId") == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in first.");
        if (session.getAttribute("customerId") != null && !customerLoginAllowed())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer access is temporarily disabled by the website owner.");
    }
    private boolean customerLoginAllowed() {
        return settingsRepository.findById(1L).map(SiteSettings::isCustomerLoginEnabled).orElse(true);
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
        SiteSettings accessSettings=settingsRepository.findById(1L).orElseGet(()->new SiteSettings(1L));
        if(!accessSettings.isCustomerSignupEnabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"New customer signups are temporarily closed by the website owner.");
        String name=requiredText(body,"name",100), username=requiredText(body,"username",40).toLowerCase(Locale.ROOT);
        String email=optionalText(body.get("email"),120); if(!email.isBlank()&&!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid email address.");
        String password=body.getOrDefault("password","");
        if(!username.matches("[a-z0-9._-]{4,40}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Username must be 4-40 characters (letters, numbers, dot, underscore or hyphen).");
        if(password.length()<8 || password.length()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password must be at least 8 characters.");
        if(customerRepository.existsByUsername(username)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Username already exists.");
        String code=uniqueDigits(4,true), card=uniqueDigits(16,false);
        String cardTier=validCardTier(body.getOrDefault("cardTier","Classic"));
        DemoCustomer c=new DemoCustomer("HBC"+UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase(Locale.ROOT),name,"",email,"Savings");
        c.setUsername(username); c.setPasswordHash(hashPassword(password)); c.setTransferCode(code); c.setCardNumber(card); c.setCardTier(cardTier); c.setQrToken(newQrToken()); c.setBalance(BigDecimal.ZERO);
        c=customerRepository.save(c);
        activityRepository.save(new ActivityLog(username,"CUSTOMER_SIGNUP","New demo customer account created"));
        mailService.notifyAdmin("New customer signup","Customer: "+name+"\nUsername: "+username+"\nTransfer code: "+code);
        // Signup and login are independent switches. If login is OFF, create the account
        // without silently signing the new customer in.
        if(!customerLoginAllowed()) return Map.of("success",true,"accountCreated",true,"requiresLogin",true,"message","Demo account created. Customer login is currently disabled by the website owner.");
        request.changeSessionId(); session.removeAttribute("loggedIn"); session.removeAttribute("adminUsername"); session.setAttribute("customerId",c.getId()); session.setAttribute("customerUsername",username); session.setMaxInactiveInterval(30*60);
        sessionRepository.save(new AdminSessionRecord(sessionHash(session),username,"CUSTOMER"));
        activityRepository.save(new ActivityLog(username,"PRESENCE_ONLINE","Customer session started through signup"));
        return customerSessionMap(c);
    }

    @PostMapping("/customer-login") @Transactional
    public Map<String,Object> customerLogin(@RequestBody Map<String,String> body,HttpSession session,HttpServletRequest request) {
        if(!customerLoginAllowed()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Customer login is temporarily disabled by the website owner.");
        String username=body.getOrDefault("username","").trim().toLowerCase(Locale.ROOT), password=body.getOrDefault("password","");
        DemoCustomer c=customerRepository.findByUsername(username).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Incorrect username or password."));
        if(!"ACTIVE".equals(c.getStatus()) || !verifyPassword(password,c.getPasswordHash())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Incorrect username or password.");
        request.changeSessionId(); session.removeAttribute("loggedIn"); session.removeAttribute("adminUsername"); session.setAttribute("customerId",c.getId()); session.setAttribute("customerUsername",username); session.setMaxInactiveInterval(30*60);
        sessionRepository.save(new AdminSessionRecord(sessionHash(session),username,"CUSTOMER"));
        activityRepository.save(new ActivityLog(username,"PRESENCE_ONLINE","Customer session started"));
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
        activityRepository.save(new ActivityLog(username,"PRESENCE_ONLINE","Admin session started"));
        activityRepository.save(new ActivityLog(username,"ADMIN_LOGIN","Admin signed in"));
        mailService.notifyAdmin("Admin login","Admin account "+username+" signed in.");
        return Map.of("success",true,"name","HASDEO BANK Admin");
    }

    @PostMapping("/logout")
    @Transactional
    public Map<String,Object> logout(HttpSession session) {
        if(Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            String username=actor(session); sessionRepository.findBySessionHash(sessionHash(session)).ifPresent(AdminSessionRecord::logout);
            activityRepository.save(new ActivityLog(username,"PRESENCE_OFFLINE","Admin session ended by sign out"));
            activityRepository.save(new ActivityLog(username,"ADMIN_LOGOUT","Admin signed out"));
        } else if(session.getAttribute("customerId")!=null) {
            String username=actor(session); sessionRepository.findBySessionHash(sessionHash(session)).ifPresent(AdminSessionRecord::logout);
            activityRepository.save(new ActivityLog(username,"PRESENCE_OFFLINE","Customer session ended by sign out"));
            activityRepository.save(new ActivityLog(username,"CUSTOMER_LOGOUT","Customer signed out"));
        }
        session.invalidate(); return Map.of("success",true);
    }

    @GetMapping("/session")
    @Transactional(readOnly=true)
    public Map<String,Object> sessionStatus(HttpSession session) {
        if (Boolean.TRUE.equals(session.getAttribute("loggedIn")))
            return Map.of("authenticated",true,"role","ADMIN");
        Object customerId = session.getAttribute("customerId");
        if (customerId != null) {
            if (!customerLoginAllowed()) return Map.of("authenticated",false,"accessDisabled",true);
            DemoCustomer customer = currentCustomer(session);
            return Map.of("authenticated",true,"role","CUSTOMER","name",customer.getName());
        }
        return Map.of("authenticated",false);
    }

    @PostMapping("/session/ping")
    @Transactional
    public Map<String,Object> sessionPing(HttpSession session) {
        requireLogin(session);
        String username=actor(session);
        String role=session.getAttribute("customerId")!=null?"CUSTOMER":"ADMIN";
        sessionRepository.findBySessionHash(sessionHash(session)).ifPresent(record -> {
            if(record.shouldLogHeartbeat()) {
                activityRepository.save(new ActivityLog(username,"PRESENCE_HEARTBEAT",role+" session heartbeat received"));
                record.markPresenceLogged();
            }
            record.seen();
        });
        return Map.of("success",true,"serverTime",Instant.now().toString());
    }

    @GetMapping("/public/status")
    @Transactional(readOnly=true)
    public Map<String,Object> publicStatus() {
        SiteSettings s=settingsRepository.findById(1L).orElseGet(()->new SiteSettings(1L));
        return Map.of("maintenance",s.isMaintenanceEnabled(),"message",safe(s.getMaintenanceMessage()),"siteTitle",safe(s.getSiteTitle()),"announcement",safe(s.getAnnouncement()),"announcementUpdatedAt",s.getAnnouncementUpdatedAt()==null?"":s.getAnnouncementUpdatedAt().toString(),"customerSignupEnabled",s.isCustomerSignupEnabled(),"customerLoginEnabled",s.isCustomerLoginEnabled());
    }

    @GetMapping("/account") @Transactional
    public Map<String,Object> account(HttpSession session) {
        requireLogin(session); DemoCustomer customer=currentCustomer(session);
        if(customer!=null){if(customer.getQrToken()==null || customer.getQrToken().isBlank()) customer.setQrToken(newQrToken());if(customer.getCardTier()==null || customer.getCardTier().isBlank()) customer.setCardTier("Classic");}
        BigDecimal balance; String name,card,code,accountType,status; List<BankTransaction> tx;
        if(customer!=null){balance=customer.getBalance();name=customer.getName();card=customer.getCardNumber();code=customer.getTransferCode();accountType=customer.getAccountType();status=customer.getStatus();tx=transactionRepository.findTop100ByCustomerIdOrderByCreatedAtDesc(customer.getId());}
        else {AccountState a=accountRepository.findById(1L).orElseThrow(()->new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Demo account is not initialized."));balance=a.getBalance();name="HASDEO BANK Admin";card="0000000000002704";code="2704";accountType="Premium Savings";status="ACTIVE";tx=transactionRepository.findTop100ByOrderByCreatedAtDesc();}
        Map<String,Object> result=new LinkedHashMap<>();result.put("name",name);result.put("cardNumber",card);result.put("accountNumber",card);result.put("transferCode",code);result.put("accountType",accountType);result.put("cardTier",customer==null?"Royal":customer.getCardTier());result.put("qrToken",customer==null?"":customer.getQrToken());result.put("status",status);result.put("balance",balance);result.put("role",customer==null?"ADMIN":"CUSTOMER");result.put("transactions",tx.stream().map(this::toTransactionMap).toList());return result;
    }

    private static final Set<String> CARD_TIERS=Set.of("Classic","Silver","Gold","Platinum","Royal");
    private String validCardTier(String value){String tier=value==null?"Classic":value.trim();if(!CARD_TIERS.contains(tier))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid demo card tier.");return tier;}
    private String newQrToken(){byte[] bytes=new byte[24];new SecureRandom().nextBytes(bytes);return HexFormat.of().formatHex(bytes);}

    @GetMapping("/public/card/{token}") @Transactional(readOnly=true)
    public Map<String,Object> publicDemoCard(@PathVariable String token){
        if(token==null || !token.matches("[a-fA-F0-9]{48}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Demo card profile not found.");
        DemoCustomer c=customerRepository.findByQrToken(token).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Demo card profile not found."));
        String card=Objects.toString(c.getCardNumber(),"");
        return Map.of("name",c.getName(),"customerCode",c.getCustomerCode(),"cardTier",Objects.toString(c.getCardTier(),"Classic"),"cardLastFour",card.length()>=4?card.substring(card.length()-4):"----","status",c.getStatus(),"notice","Public educational demo profile. No real banking or payment account is represented.");
    }

    @PostMapping("/customer/card-tier") @Transactional
    public Map<String,Object> updateCustomerCardTier(@RequestBody Map<String,String> body,HttpSession session){
        requireLogin(session);DemoCustomer c=currentCustomer(session);
        if(c==null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only customer demo accounts can change card styles.");
        String tier=validCardTier(body.get("cardTier"));String old=c.getCardTier();c.setCardTier(tier);
        log(session,"DEMO_CARD_TIER_UPDATED","Card tier changed from "+old+" to "+tier);
        return Map.of("success",true,"cardTier",tier,"message","Demo card style updated.");
    }

    @PostMapping("/deposit") @Transactional
    public Map<String,Object> deposit(@RequestBody Map<String,Object> body,HttpSession session) {
        requireLogin(session);BigDecimal amount=getAmount(body);if(amount.signum()<=0||amount.compareTo(new BigDecimal("1000000000.00"))>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid amount.");
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
    public Map<String,Object> recipient(@PathVariable String code,HttpSession session){
        requireLogin(session);
        if(!code.matches("\\d{4}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid 4-digit demo transfer code.");
        if("2704".equals(code)) return Map.of("name","HASDEO BANK Admin","transferCode","2704","role","ADMIN");
        DemoCustomer c=customerRepository.findByTransferCode(code).filter(x->"ACTIVE".equals(x.getStatus())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"No active demo account found for this code."));
        return Map.of("name",c.getName(),"transferCode",c.getTransferCode(),"role","CUSTOMER");
    }

    @PostMapping("/transfer") @Transactional
    public Map<String,Object> transfer(@RequestBody Map<String,Object> body,HttpSession session) {
        requireLogin(session);
        BigDecimal amount=getAmount(body);
        String code=String.valueOf(body.getOrDefault("recipientCode","")).trim();
        if(!code.matches("\\d{4}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter the recipient's 4-digit demo code.");
        if(amount.signum()<=0 || amount.compareTo(new BigDecimal("1000000000.00"))>0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid demo amount.");
        DemoCustomer sender=currentCustomer(session);
        Instant now=Instant.now();

        // 2704 represents the fictional admin demo account.
        if(sender==null) {
            if("2704".equals(code)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"You cannot transfer to the same admin demo account.");
            AccountState admin=lockedAccount();
            DemoCustomer receiverRef=customerRepository.findByTransferCode(code).filter(x->"ACTIVE".equals(x.getStatus())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Recipient not found."));
            DemoCustomer receiver=customerRepository.findByIdForUpdate(receiverRef.getId()).orElseThrow();
            if(amount.compareTo(admin.getBalance())>0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Insufficient admin demo balance.");
            admin.setBalance(admin.getBalance().subtract(amount));
            receiver.setBalance(receiver.getBalance().add(amount));
            transactionRepository.save(new BankTransaction("Admin demo transfer sent to "+receiver.getName()+" ("+code+")",amount,"DEBIT",now));
            transactionRepository.save(new BankTransaction("Demo transfer received from HASDEO BANK Admin",amount,"CREDIT",now,receiver.getId()));
            log(session,"ADMIN_DEMO_TRANSFER","Admin demo transfer sent to "+code+": ₹"+amount);
            mailService.notifyAdmin("Admin demo transfer","Receiver: "+receiver.getName()+" ("+code+")\nAmount: ₹"+amount);
            return Map.of("success",true,"balance",admin.getBalance(),"recipientName",receiver.getName());
        }

        if("2704".equals(code)) {
            AccountState admin=lockedAccount();
            DemoCustomer lockedSender=customerRepository.findByIdForUpdate(sender.getId()).orElseThrow();
            if(amount.compareTo(lockedSender.getBalance())>0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Insufficient demo balance.");
            lockedSender.setBalance(lockedSender.getBalance().subtract(amount));
            admin.setBalance(admin.getBalance().add(amount));
            transactionRepository.save(new BankTransaction("Demo transfer sent to HASDEO BANK Admin (2704)",amount,"DEBIT",now,lockedSender.getId()));
            transactionRepository.save(new BankTransaction("Demo transfer received from "+lockedSender.getName(),amount,"CREDIT",now));
            log(session,"DEMO_TRANSFER_TO_ADMIN","Demo transfer sent to admin code 2704: ₹"+amount);
            mailService.notifyAdmin("Customer to admin demo transfer","Sender: "+lockedSender.getName()+" ("+lockedSender.getTransferCode()+")\nAmount: ₹"+amount);
            return Map.of("success",true,"balance",lockedSender.getBalance(),"recipientName","HASDEO BANK Admin");
        }

        DemoCustomer receiverRef=customerRepository.findByTransferCode(code).filter(x->"ACTIVE".equals(x.getStatus())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Recipient not found."));
        if(receiverRef.getId().equals(sender.getId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"You cannot transfer to your own account.");
        Long firstId=Math.min(sender.getId(),receiverRef.getId()),secondId=Math.max(sender.getId(),receiverRef.getId());
        DemoCustomer first=customerRepository.findByIdForUpdate(firstId).orElseThrow(),second=customerRepository.findByIdForUpdate(secondId).orElseThrow();
        sender=sender.getId().equals(firstId)?first:second;
        DemoCustomer receiver=receiverRef.getId().equals(firstId)?first:second;
        if(!"ACTIVE".equals(receiver.getStatus())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Recipient account is inactive.");
        if(amount.compareTo(sender.getBalance())>0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Insufficient demo balance.");
        sender.setBalance(sender.getBalance().subtract(amount)); receiver.setBalance(receiver.getBalance().add(amount));
        transactionRepository.save(new BankTransaction("Transfer sent to "+receiver.getName()+" ("+code+")",amount,"DEBIT",now,sender.getId()));
        transactionRepository.save(new BankTransaction("Transfer received from "+sender.getName(),amount,"CREDIT",now,receiver.getId()));
        log(session,"DEMO_TRANSFER","Demo transfer sent to code "+code+": ₹"+amount);
        mailService.notifyAdmin("Demo transfer","Sender: "+sender.getName()+" ("+sender.getTransferCode()+")\nReceiver: "+receiver.getName()+" ("+code+")\nAmount: ₹"+amount);
        return Map.of("success",true,"balance",sender.getBalance(),"recipientName",receiver.getName());
    }

    @PostMapping("/profile/password") @Transactional
    public Map<String,Object> changeCustomerPassword(@RequestBody Map<String,String> body,HttpSession session) {
        requireLogin(session);
        DemoCustomer customer=currentCustomer(session);
        if(customer==null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Customer profile settings are available for demo customer accounts only.");
        String current=body.getOrDefault("currentPassword","");
        String next=body.getOrDefault("newPassword","");
        if(!verifyPassword(current,customer.getPasswordHash())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Current password is incorrect.");
        if(next.length()<8 || next.length()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"New password must be 8-100 characters.");
        customer.setPasswordHash(hashPassword(next));
        log(session,"CUSTOMER_PASSWORD_CHANGED","Customer changed their demo account password");
        return Map.of("success",true,"message","Demo account password updated.");
    }

    @PostMapping("/feedback") @Transactional
    public Map<String,Object> submitFeedback(@RequestBody Map<String,String> body,HttpSession session) {
        requireLogin(session);
        int rating;
        try { rating=Integer.parseInt(body.getOrDefault("rating","5")); } catch(Exception ex) { rating=5; }
        if(rating<1 || rating>5) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a rating from 1 to 5.");
        String message=optionalText(body.getOrDefault("message",""),800);
        String who=actor(session);
        SupportTicket ticket=supportRepository.save(new SupportTicket(who,"", "Website feedback · "+rating+"/5",message.isBlank()?"Rating: "+rating+"/5":"Rating: "+rating+"/5\n"+message));
        mailService.notifyAdmin("HASDEO BANK feedback #"+ticket.getId(),"From: "+who+"\nRating: "+rating+"/5\n"+message);
        return Map.of("success",true,"message","Thanks! Your feedback has been saved.");
    }

    @PostMapping("/support") @Transactional
    public Map<String,Object> createSupportTicket(@RequestBody Map<String,String> body) {
        String name=requiredText(body,"name",100), email=requiredText(body,"email",120), category=requiredText(body,"category",40), message=requiredText(body,"message",1200);
        if(!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid email address.");
        SupportTicket ticket=supportRepository.save(new SupportTicket(name,email,category,message));
        boolean emailSent=mailService.notifyAdmin("New support request #"+ticket.getId(),"Name: "+name+"\nEmail: "+email+"\nCategory: "+category+"\nMessage: "+message);
        return Map.of("success",true,"ticketId",ticket.getId(),"emailSent",emailSent);
    }

    @PostMapping("/support/ai") @Transactional
    public Map<String,Object> aiSupport(@RequestBody Map<String,Object> body) {
        String message=String.valueOf(body.getOrDefault("message", "")).trim();
        if(message.isBlank() || message.length()>1200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a question up to 1200 characters.");
        String name=optionalText(String.valueOf(body.getOrDefault("name", "")),100);
        String email=optionalText(String.valueOf(body.getOrDefault("email", "")),120);
        if(!email.isBlank()&&!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid email address.");
        List<Map<String,String>> history=new ArrayList<>();
        Object rawHistory=body.get("history");
        if(rawHistory instanceof List<?> items) for(Object item:items) {
            if(item instanceof Map<?,?> map) {
                String role=String.valueOf(map.get("role") == null ? "" : map.get("role"));
                String content=String.valueOf(map.get("content") == null ? "" : map.get("content"));
                if(("user".equals(role)||"assistant".equals(role))&&!content.isBlank()) history.add(Map.of("role",role,"content",content.substring(0,Math.min(1200,content.length()))));
            }
        }
        try {
            SiteSettings aiSettings=getSettings();
            com.hasdeo.bank.service.SupportAiService.AiReply reply=supportAiService.reply(history,message,aiSettings.getAiCreatorName(),aiSettings.getAiCustomKnowledge());
            if(!reply.needsHuman()) return Map.of("success",true,"answer",reply.answer(),"escalated",false);
            String transcript=history.stream().map(m->m.get("role")+": "+m.get("content")).reduce("",(a,b)->a+b+"\n")+"Customer: "+message+"\nAI: "+reply.answer();
            SupportTicket ticket=supportRepository.save(new SupportTicket(name.isBlank()?"Website visitor":name,email,"AI escalation",transcript.substring(0,Math.min(1200,transcript.length()))));
            boolean emailSent=mailService.notifyAdmin("AI support needs human help #"+ticket.getId(),"Name: "+(name.isBlank()?"Not provided":name)+"\nEmail: "+(email.isBlank()?"Not provided":email)+"\nConversation:\n"+transcript);
            return Map.of("success",true,"answer",reply.answer()+"\n\nI've forwarded this to the website support team. Reference ticket #"+ticket.getId()+".","escalated",true,"ticketId",ticket.getId(),"emailSent",emailSent);
        } catch(Exception ex) {
            System.getLogger(BankController.class.getName()).log(System.Logger.Level.ERROR, "HASDEO Help AI endpoint failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            String answer = !supportAiService.isConfigured()
                    ? "AI abhi configure nahi hai. Website owner ko server par OPENAI_API_KEY set karna hoga. Tab tak Contact Support form use kar sakte ho. / The AI service is not configured yet; please use Contact Support for now."
                    : "I couldn't reach the AI service just now. Please try again in a moment. If this keeps happening, you can choose Contact Support below. / AI service abhi respond nahi kar rahi; thodi der baad try karo.";
            return Map.of("success",true,"answer",answer,"escalated",false,"available",supportAiService.isConfigured());
        }
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
        List<AdminSessionRecord> recorded=sessionRepository.findTop100ByOrderByLoginAtDesc();
        List<Map<String,Object>> rows=new ArrayList<>();
        for(AdminSessionRecord record:recorded) {
            Map<String,Object> m=new LinkedHashMap<>();m.put("username",record.getUsername());m.put("role",record.getRole());m.put("status",record.getStatus());
            m.put("loginAt",formatTime(record.getLoginAt()));m.put("lastSeenAt",formatTime(record.getLastSeenAt()));m.put("logoutAt",record.getLogoutAt()==null?"—":formatTime(record.getLogoutAt()));m.put("_sortAt",record.getLoginAt());rows.add(m);
        }
        // Preserve older customer login/signup events, but label them historical because no heartbeat/logout was captured then.
        for(ActivityLog event:activityRepository.findTop100ByOrderByCreatedAtDesc()) {
            if(!"CUSTOMER_LOGIN".equals(event.getAction()) && !"CUSTOMER_SIGNUP".equals(event.getAction())) continue;
            boolean alreadyRecorded=recorded.stream().anyMatch(r->r.getUsername().equals(event.getActor()) && Math.abs(r.getLoginAt().toEpochMilli()-event.getCreatedAt().toEpochMilli())<=15000);
            if(!alreadyRecorded) {Map<String,Object> m=new LinkedHashMap<>();m.put("username",event.getActor());m.put("role","CUSTOMER");m.put("status","HISTORIC");m.put("loginAt",formatTime(event.getCreatedAt()));m.put("lastSeenAt","Not recorded");m.put("logoutAt","Not recorded");m.put("_sortAt",event.getCreatedAt());rows.add(m);}
        }
        rows.sort((a,b)->((Instant)b.get("_sortAt")).compareTo((Instant)a.get("_sortAt")));
        rows.forEach(m->m.remove("_sortAt"));
        return rows.stream().limit(100).toList();
    }

    @GetMapping("/owner/presence") @Transactional
    public List<Map<String,Object>> livePresence(HttpSession session) {
        requireAdmin(session); expireStaleSessions();
        Map<String,AdminSessionRecord> latest=new HashMap<>();
        Map<String,Boolean> anyOnline=new HashMap<>();
        Instant cutoff=Instant.now().minusSeconds(75);
        for(AdminSessionRecord record:sessionRepository.findByRoleOrderByLastSeenAtDesc("CUSTOMER")) {
            AdminSessionRecord prior=latest.get(record.getUsername());
            if(prior==null || (record.getLastSeenAt()!=null && (prior.getLastSeenAt()==null || record.getLastSeenAt().isAfter(prior.getLastSeenAt())))) latest.put(record.getUsername(),record);
            if("ACTIVE".equals(record.getStatus()) && record.getLastSeenAt()!=null && record.getLastSeenAt().isAfter(cutoff)) anyOnline.put(record.getUsername(),true);
        }
        return customerRepository.findAll().stream().map(c->{
            AdminSessionRecord r=latest.get(c.getUsername()); boolean online=Boolean.TRUE.equals(anyOnline.get(c.getUsername()));
            Map<String,Object> m=new LinkedHashMap<>();m.put("name",c.getName());m.put("username",c.getUsername());m.put("status",online?"ONLINE":"OFFLINE");
            m.put("lastSeenAt",r==null?"Never":formatTime(r.getLastSeenAt()));m.put("loginAt",r==null?"Never":formatTime(r.getLoginAt()));return m;
        }).toList();
    }

    @GetMapping("/owner/presence/history") @Transactional(readOnly=true)
    public List<Map<String,Object>> presenceHistory(HttpSession session) {
        requireAdmin(session);
        List<String> events=List.of("PRESENCE_ONLINE","PRESENCE_HEARTBEAT","PRESENCE_OFFLINE","PRESENCE_EXPIRED");
        return activityRepository.findByActionInOrderByCreatedAtDesc(events).stream().map(e->{
            Map<String,Object> row=new LinkedHashMap<>();row.put("timestamp",formatTime(e.getCreatedAt()));row.put("username",e.getActor());row.put("event",e.getAction().replace("PRESENCE_",""));row.put("details",safe(e.getDetails()));return row;
        }).toList();
    }

    @GetMapping("/owner/settings") @Transactional(readOnly=true)
    public Map<String,Object> settings(HttpSession session) { requireAdmin(session); SiteSettings s=getSettings(); return settingsMap(s); }

    @PostMapping("/owner/settings") @Transactional
    public Map<String,Object> updateSettings(@RequestBody Map<String,String> body,HttpSession session) {
        requireAdmin(session); SiteSettings s=getSettings();
        if(body.containsKey("siteTitle")) s.setSiteTitle(requiredText(body,"siteTitle",100));
        if(body.containsKey("aiCreatorName")) s.setAiCreatorName(optionalText(body.get("aiCreatorName"),80));
        if(body.containsKey("aiCustomKnowledge")) s.setAiCustomKnowledge(optionalText(body.get("aiCustomKnowledge"),5000));
        log(session,"WEBSITE_SETTINGS_UPDATED","Updated website and AI assistant settings"); return settingsMap(s);
    }

    @PostMapping("/owner/access-control") @Transactional
    public Map<String,Object> updateCustomerAccess(@RequestBody Map<String,Object> body,HttpSession session) {
        requireAdmin(session); SiteSettings s=getSettings();
        if(body.containsKey("customerSignupEnabled")) s.setCustomerSignupEnabled(parseBooleanSetting(body.get("customerSignupEnabled"),"customerSignupEnabled"));
        if(body.containsKey("customerLoginEnabled")) s.setCustomerLoginEnabled(parseBooleanSetting(body.get("customerLoginEnabled"),"customerLoginEnabled"));
        log(session,"CUSTOMER_ACCESS_SETTINGS_UPDATED","Customer signup="+s.isCustomerSignupEnabled()+", customer login="+s.isCustomerLoginEnabled());
        return Map.of("customerSignupEnabled",s.isCustomerSignupEnabled(),"customerLoginEnabled",s.isCustomerLoginEnabled());
    }

    private boolean parseBooleanSetting(Object value,String field) {
        if(value instanceof Boolean b) return b;
        if(value instanceof String text && ("true".equalsIgnoreCase(text)||"false".equalsIgnoreCase(text))) return Boolean.parseBoolean(text);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid value for "+field+".");
    }

    @PostMapping("/owner/announcement/send") @Transactional
    public Map<String,Object> sendAnnouncement(@RequestBody Map<String,Object> body,HttpSession session) {
        requireAdmin(session); SiteSettings s=getSettings();
        String message=requiredTextObject(body,"message",240);
        s.setAnnouncement(message);
        log(session,"ANNOUNCEMENT_SENT","Broadcast announcement sent to all customer sessions");
        return Map.of("success",true,"message",safe(s.getAnnouncement()),"updatedAt",s.getAnnouncementUpdatedAt().toString());
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
        Instant cutoff=Instant.now().minusSeconds(75);
        sessionRepository.findByStatusAndLastSeenAtBefore("ACTIVE",cutoff).forEach(record->{
            record.expire();
            activityRepository.save(new ActivityLog(record.getUsername(),"PRESENCE_EXPIRED",record.getRole()+" session marked offline after heartbeat timeout"));
        });
    }
    private SiteSettings getSettings(){return settingsRepository.findById(1L).orElseGet(()->settingsRepository.save(new SiteSettings(1L)));}
    private AccountState lockedAccount(){return accountRepository.findByIdForUpdate(1L).orElseThrow(()->new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Demo account is not initialized."));}
    private BigDecimal getAmount(Map<String,Object> body){try{BigDecimal v=new BigDecimal(String.valueOf(body.get("amount"))).setScale(2,RoundingMode.UNNECESSARY);if(v.signum()<=0||v.compareTo(new BigDecimal("1000000000.00"))>0)throw new NumberFormatException();return v;}catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter an amount from ₹0.01 up to ₹100 crore, with up to two decimal places.");}}
    private String requiredTextObject(Map<String,Object> body,String key,int max){String v=String.valueOf(body.getOrDefault(key,"")).trim();if(v.isEmpty()||v.length()>max)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a message up to "+max+" characters.");return v;}
    private String requiredText(Map<String,String> body,String key,int max){String v=body.getOrDefault(key,"").trim();if(v.isEmpty()||v.length()>max)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid "+key+" (max "+max+" characters).");return v;}
    private String optionalText(String v,int max){String s=v==null?"":v.trim();if(s.length()>max)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Text is too long (max "+max+" characters).");return s;}
    private String safe(String v){return v==null?"":v;}
    private String formatTime(Instant instant){return instant==null?"—":instant.atZone(INDIA_ZONE).format(DATE_FORMAT);}
    private Map<String,Object> timeMap(Map<String,Object> base,Instant instant){Map<String,Object> m=new LinkedHashMap<>(base);m.put("timestamp",instant.atZone(INDIA_ZONE).toOffsetDateTime().toString());m.put("date",formatTime(instant));return m;}
    private Map<String,Object> toTransactionMap(BankTransaction t){Map<String,Object> m=new LinkedHashMap<>();m.put("id","HB"+t.getId());m.put("description",t.getDescription());m.put("amount",t.getAmount());m.put("type",t.getType());return timeMap(m,t.getCreatedAt());}
    private Map<String,Object> toCustomerMap(DemoCustomer c){Map<String,Object> m=new LinkedHashMap<>();m.put("id",c.getId());m.put("customerCode",c.getCustomerCode());m.put("name",c.getName());m.put("contact",safe(c.getContactLabel()));m.put("email",safe(c.getEmailLabel()));m.put("username",safe(c.getUsername()));m.put("accountType",c.getAccountType());m.put("cardTier",c.getCardTier()==null?"Classic":c.getCardTier());m.put("status",c.getStatus());m.put("createdAt",formatTime(c.getCreatedAt()));m.put("cardNumber",safe(c.getCardNumber()));m.put("transferCode",safe(c.getTransferCode()));m.put("balance",c.getBalance());return m;}
    private Map<String,Object> settingsMap(SiteSettings s){return Map.of("siteTitle",safe(s.getSiteTitle()),"announcement",safe(s.getAnnouncement()),"maintenance",s.isMaintenanceEnabled(),"maintenanceMessage",safe(s.getMaintenanceMessage()),"aiCreatorName",safe(s.getAiCreatorName()),"aiCustomKnowledge",safe(s.getAiCustomKnowledge()),"customerSignupEnabled",s.isCustomerSignupEnabled(),"customerLoginEnabled",s.isCustomerLoginEnabled());}
}
