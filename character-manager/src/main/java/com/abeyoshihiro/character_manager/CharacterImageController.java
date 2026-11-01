package com.abeyoshihiro.character_manager;

import java.io.IOException;
import java.nio.file.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CharacterImageController {
    private final GalleryService gallery;
    public CharacterImageController(GalleryService gallery) { this.gallery = gallery; }
    @PostMapping("/characters/{id}/image")
    public String upload(@PathVariable("id") Long id, @RequestParam("image") MultipartFile file,
                         @RequestParam(defaultValue = "") String title, RedirectAttributes flash) throws IOException {
        try { gallery.upload(id, file, title); flash.addFlashAttribute("notice", "画像を追加しました。"); }
        catch (IllegalArgumentException exception) { flash.addFlashAttribute("error", exception.getMessage()); }
        catch (IOException exception) { flash.addFlashAttribute("error", "画像を読み込むか保存できませんでした。画像と保存先を確認してください。"); }
        return "redirect:/characters/" + id + "/edit";
    }
    @GetMapping("/characters/{id}/image")
    public ResponseEntity<byte[]> show(@PathVariable("id") Long id) throws IOException { return image(gallery.coverPath(id)); }
    @GetMapping("/characters/{id}/images/{pictureId}")
    public ResponseEntity<byte[]> showPicture(@PathVariable("id") Long id, @PathVariable("pictureId") Long pictureId) throws IOException {
        return image(gallery.path(gallery.picture(id, pictureId).getStorageName()));
    }
    private ResponseEntity<byte[]> image(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).header("Cache-Control", "no-store").body(Files.readAllBytes(path));
    }
    @PostMapping("/characters/{id}/images/{pictureId}/cover")
    public String cover(@PathVariable("id") Long id, @PathVariable("pictureId") Long pictureId) {
        gallery.cover(id, pictureId); return "redirect:/characters/" + id + "/edit";
    }
    @PostMapping("/characters/{id}/images/{pictureId}/delete")
    public String delete(@PathVariable("id") Long id, @PathVariable("pictureId") Long pictureId) throws IOException {
        gallery.delete(id, pictureId); return "redirect:/characters/" + id + "/edit";
    }
}
