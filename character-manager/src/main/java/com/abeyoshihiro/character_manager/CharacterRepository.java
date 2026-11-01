package com.abeyoshihiro.character_manager;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CharacterRepository extends JpaRepository<OriginalCharacter, Long> {
    List<OriginalCharacter> findByNameContainingIgnoreCaseOrderByIdAsc(String name);
    List<OriginalCharacter> findByNameContainingIgnoreCaseAndTagsContainingOrderByIdAsc(
            String name, String tag);

    @Query("select distinct tag from OriginalCharacter c join c.tags tag order by tag")
    List<String> findAllTags();
}
