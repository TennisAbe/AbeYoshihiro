package com.abeyoshihiro.character_manager;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterRepository
        extends JpaRepository<OriginalCharacter, Long> {
}