package com.abeyoshihiro.character_manager;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class CharacterImageController {

    private final CharacterRepository repository;

    private final Path uploadDirectory =
            Path.of("uploads").toAbsolutePath().normalize();

    public CharacterImageController(CharacterRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/characters/{id}/image")
    public String upload(
            @PathVariable("id") Long id,
            @RequestParam("image") MultipartFile file) throws IOException {

        OriginalCharacter character = repository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "画像を選択してください");
        }

        BufferedImage image;
        try (var input = file.getInputStream()) {
            image = ImageIO.read(input);
        }

        if (image == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "読み込める画像を選択してください");
        }

        Files.createDirectories(uploadDirectory);

        // ファイル名はキャラクターのIDから作成します。
        // アップロードされた画像はPNG形式で保存します。
        Path destination = uploadDirectory.resolve(id + ".png");

        if (!ImageIO.write(image, "png", destination.toFile())) {
            throw new IOException("画像を保存できませんでした");
        }

        character.setImageRegistered(true);
        repository.save(character);

        return "redirect:/";
    }

    @GetMapping("/characters/{id}/image")
    public ResponseEntity<byte[]> show(
            @PathVariable("id") Long id) throws IOException {

        OriginalCharacter character = repository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND));

        Path imagePath = uploadDirectory.resolve(id + ".png");

        if (!character.isImageRegistered()
                || !Files.isRegularFile(imagePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Cache-Control", "no-store")
                .body(Files.readAllBytes(imagePath));
    }
}