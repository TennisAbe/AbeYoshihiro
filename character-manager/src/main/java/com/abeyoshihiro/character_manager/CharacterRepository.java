package com.abeyoshihiro.character_manager;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface CharacterRepository extends JpaRepository<OriginalCharacter, Long> {
    List<OriginalCharacter> findByNameContainingIgnoreCaseOrderByIdAsc(String name);
    List<OriginalCharacter> findByNameContainingIgnoreCaseAndTagsContainingOrderByIdAsc(
            String name, String tag);

    @Query("select distinct tag from OriginalCharacter c join c.tags tag order by tag")
    List<String> findAllTags();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OriginalCharacter c where c.id = :id")
    java.util.Optional<OriginalCharacter> findForUpdate(@Param("id") Long id);
}
