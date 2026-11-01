package com.abeyoshihiro.character_manager;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AffiliationOrderRepository extends JpaRepository<AffiliationOrder, Long> {
    List<AffiliationOrder> findByWorldOrderByPositionAscIdAsc(CharacterWorld world);
    void deleteByWorld(CharacterWorld world);
}
