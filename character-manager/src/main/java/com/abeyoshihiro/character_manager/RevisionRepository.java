package com.abeyoshihiro.character_manager;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RevisionRepository extends JpaRepository<CharacterRevision, Long> {
    List<CharacterRevision> findByCharacterIdOrderBySavedAtDescIdDesc(Long id);
    void deleteByCharacterId(Long id);
}
