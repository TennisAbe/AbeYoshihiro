package com.abeyoshihiro.character_manager;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterRepository extends JpaRepository<OriginalCharacter, Long> {
    List<OriginalCharacter> findByNameContainingIgnoreCaseOrderByIdAsc(String name);
}
