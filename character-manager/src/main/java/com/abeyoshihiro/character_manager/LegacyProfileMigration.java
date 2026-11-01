package com.abeyoshihiro.character_manager;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Order(0)
public class LegacyProfileMigration implements ApplicationRunner {
    private final CharacterRepository characters;
    private final TransactionTemplate transactions;
    public LegacyProfileMigration(CharacterRepository characters, TransactionTemplate transactions) {
        this.characters = characters; this.transactions = transactions;
    }
    @Override
    public void run(ApplicationArguments args) {
        transactions.executeWithoutResult(status -> {
            for (OriginalCharacter c : characters.findAll()) {
                if (ProfileFields.migrateLegacy(c)) characters.save(c);
            }
        });
    }
}
