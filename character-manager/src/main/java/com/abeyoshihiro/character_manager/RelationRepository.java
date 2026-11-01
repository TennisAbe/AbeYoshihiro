package com.abeyoshihiro.character_manager;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RelationRepository extends JpaRepository<CharacterRelation, Long> {
    List<CharacterRelation> findBySourceIdOrderByIdAsc(Long id);
    List<CharacterRelation> findByTargetIdOrderByIdAsc(Long id);
    void deleteBySourceIdOrTargetId(Long sourceId, Long targetId);
}
