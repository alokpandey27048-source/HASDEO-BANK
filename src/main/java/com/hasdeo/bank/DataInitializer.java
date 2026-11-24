package com.hasdeo.bank;

import com.hasdeo.bank.model.AccountState;
import com.hasdeo.bank.model.BankTransaction;
import com.hasdeo.bank.model.SiteSettings;
import com.hasdeo.bank.repository.AccountStateRepository;
import com.hasdeo.bank.repository.BankTransactionRepository;
import com.hasdeo.bank.repository.SiteSettingsRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class DataInitializer implements CommandLineRunner {
    private final AccountStateRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final SiteSettingsRepository settingsRepository;

    public DataInitializer(AccountStateRepository accountRepository, BankTransactionRepository transactionRepository,
                           SiteSettingsRepository settingsRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.settingsRepository = settingsRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!accountRepository.existsById(1L)) {
            accountRepository.save(new AccountState(1L, new BigDecimal("25000.00")));
            transactionRepository.save(new BankTransaction("Demo account opened", new BigDecimal("25000.00"), "CREDIT", Instant.now()));
        }
        if (!settingsRepository.existsById(1L)) settingsRepository.save(new SiteSettings(1L));
    }
}
