package com.abeyoshihiro.character_manager;

import java.time.*;
import java.time.format.DateTimeFormatter;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ComicShareController {
    private final ComicShareService share;
    private final CharacterRepository characters;
    private final PictureRepository pictures;
    private final AffiliationOrderingService ordering;
    public ComicShareController(ComicShareService share, CharacterRepository characters, PictureRepository pictures, AffiliationOrderingService ordering) { this.ordering = ordering; this.share = share; this.characters = characters; this.pictures = pictures; }
    @GetMapping("/share")
    public String index(Model model) {
        model.addAttribute("characters", ordering.roster()); model.addAttribute("updatePrompt", ComicShareService.UPDATE_PROMPT);
        model.addAttribute("characterCount", characters.count()); model.addAttribute("pictureCount", pictures.count()); model.addAttribute("sharePrompt", ComicShareService.PROMPT); return "comic-share";
    }
    @GetMapping("/share/download")
    public Object download(@org.springframework.web.bind.annotation.RequestParam(required = false) java.util.List<Long> characterIds, @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean selected, @org.springframework.web.bind.annotation.RequestParam(required = false) java.util.List<Long> ids, @org.springframework.web.bind.annotation.RequestParam(defaultValue = "100") int spacing, RedirectAttributes flash) {
        try {
            String date = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("Asia/Tokyo")).format(Instant.now());
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                    .header("Content-Disposition", "attachment; filename=\"noctalith-comic-" + (selected ? "update-" : "reference-") + date + ".zip\"")
                    .header("Cache-Control", "no-store").body(selected ? share.exportSelected(characterIds, spacing) : share.exportAll(ids, spacing));
        } catch (Exception exception) {
            flash.addFlashAttribute("error", exception instanceof IllegalArgumentException ? exception.getMessage() : "共有資料を作成できませんでした。画像の保存先を確認してください。");
            return "redirect:/share";
        }
    }
}
