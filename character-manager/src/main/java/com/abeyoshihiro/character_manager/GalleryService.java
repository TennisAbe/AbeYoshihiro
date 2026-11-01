package com.abeyoshihiro.character_manager;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GalleryService implements ApplicationRunner {
    public static final long MAX_PIXELS = 12_000_000;
    public static final int MAX_IMAGES = 20;
    private final CharacterRepository characters;
    private final PictureRepository pictures;
    private final TransactionTemplate transactions;
    private final Path directory;

    public GalleryService(CharacterRepository characters, PictureRepository pictures, TransactionTemplate transactions,
                          @Value("${noctalith.upload-directory:uploads}") String directory) {
        this.characters = characters; this.pictures = pictures; this.transactions = transactions;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }
    public Path directory() { return directory; }
    public Path path(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9-]+\\.png")) throw new IllegalArgumentException("画像ファイル名が不正です。");
        return directory.resolve(name);
    }
    public OriginalCharacter character(Long id) {
        return characters.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    public List<CharacterPicture> list(Long id) { return pictures.findByCharacterIdOrderByIdAsc(id); }
    public CharacterPicture picture(Long owner, Long pictureId) {
        CharacterPicture image = pictures.findById(pictureId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!image.getCharacter().getId().equals(owner)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return image;
    }
    public byte[] normalize(byte[] bytes) throws IOException {
        if (bytes.length == 0) throw new IllegalArgumentException("画像を選択してください。");
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("PNG・JPEG・GIF画像を選択してください。");
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpeg", "jpg", "gif").contains(format)) throw new IllegalArgumentException("対応していない画像形式です。");
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) throw new IllegalArgumentException("画像は1200万画素以内にしてください。");
                BufferedImage image = reader.read(0);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "png", output)) throw new IOException("画像を保存できませんでした。");
                if (output.size() > 20 * 1024 * 1024) throw new IllegalArgumentException("変換後の画像が大きすぎます。");
                return output.toByteArray();
            } finally { reader.dispose(); }
        }
    }
    public synchronized void upload(Long owner, MultipartFile file, String title) throws IOException {
        character(owner);
        if (file.getSize() > 5 * 1024 * 1024) throw new IllegalArgumentException("画像は1枚5MB以内にしてください。");
        if (title.length() > 100) throw new IllegalArgumentException("画像の説明は100文字以内にしてください。");
        if (list(owner).size() >= MAX_IMAGES) throw new IllegalArgumentException("画像は1キャラクター20枚までです。");
        byte[] image = normalize(file.getBytes());
        Files.createDirectories(directory);
        String name = UUID.randomUUID() + ".png";
        Path destination = path(name);
        Files.write(destination, image, StandardOpenOption.CREATE_NEW);
        try {
            transactions.executeWithoutResult(status -> {
                OriginalCharacter c = character(owner);
                CharacterPicture picture = new CharacterPicture();
                picture.setCharacter(c); picture.setStorageName(name); picture.setTitle(title.strip());
                pictures.saveAndFlush(picture);
                if (c.getCoverImageId() == null) c.setCoverImageId(picture.getId());
                c.setImageRegistered(true); characters.save(c);
            });
        } catch (RuntimeException exception) { Files.deleteIfExists(destination); throw exception; }
    }
    public synchronized void cover(Long owner, Long pictureId) {
        transactions.executeWithoutResult(status -> {
            picture(owner, pictureId);
            OriginalCharacter c = character(owner); c.setCoverImageId(pictureId); c.setImageRegistered(true); characters.save(c);
        });
    }
    public synchronized void delete(Long owner, Long pictureId) throws IOException {
        String name = picture(owner, pictureId).getStorageName();
        transactions.executeWithoutResult(status -> {
            OriginalCharacter c = character(owner);
            pictures.delete(picture(owner, pictureId)); pictures.flush();
            List<CharacterPicture> remaining = list(owner);
            if (Objects.equals(c.getCoverImageId(), pictureId)) c.setCoverImageId(remaining.isEmpty() ? null : remaining.get(0).getId());
            c.setImageRegistered(!remaining.isEmpty()); characters.save(c);
        });
        Files.deleteIfExists(path(name));
    }
    public Path coverPath(Long owner) {
        OriginalCharacter c = character(owner);
        if (c.getCoverImageId() != null) return path(picture(owner, c.getCoverImageId()).getStorageName());
        if (c.isImageRegistered()) return path(owner + ".png");
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    @Override
    public synchronized void run(ApplicationArguments args) {
        transactions.executeWithoutResult(status -> {
            for (OriginalCharacter c : characters.findAll()) {
                if (c.isImageRegistered() && c.getCoverImageId() == null && Files.isRegularFile(path(c.getId() + ".png"))) {
                    CharacterPicture p = new CharacterPicture(); p.setCharacter(c); p.setStorageName(c.getId() + ".png"); p.setTitle("既存の画像");
                    pictures.saveAndFlush(p); c.setCoverImageId(p.getId()); characters.save(c);
                }
            }
        });
    }
}
