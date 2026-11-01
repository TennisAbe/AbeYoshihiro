package com.abeyoshihiro.character_manager;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PictureRepository extends JpaRepository<CharacterPicture, Long> {
    List<CharacterPicture> findByCharacterIdOrderByIdAsc(Long id);
}
