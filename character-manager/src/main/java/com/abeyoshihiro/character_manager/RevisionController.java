package com.abeyoshihiro.character_manager;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RevisionController {
    private final RevisionService revisions;
    private final GalleryService gallery;
    public RevisionController(RevisionService revisions, GalleryService gallery) { this.revisions = revisions; this.gallery = gallery; }
    @GetMapping("/characters/{id}/history/{revisionId}")
    public String preview(@PathVariable Long id, @PathVariable Long revisionId, Model model) {
        OriginalCharacter c = gallery.character(id); CharacterRevision revision = revisions.revision(id, revisionId);
        model.addAttribute("character", c); model.addAttribute("revision", revision); model.addAttribute("differences", revisions.differences(c, revision));
        model.addAttribute("fingerprint", ProfileSnapshot.fingerprint(c)); return "revision-confirm";
    }
    @PostMapping("/characters/{id}/history/{revisionId}/restore")
    public String restore(@PathVariable Long id, @PathVariable Long revisionId, @RequestParam(defaultValue = "false") boolean confirmed,
                          @RequestParam(defaultValue = "") String fingerprint, RedirectAttributes flash) {
        if (!confirmed) { gallery.character(id); revisions.revision(id, revisionId); flash.addFlashAttribute("error", "差分を確認し、復元の確認にチェックしてください。"); return "redirect:/characters/" + id + "/history/" + revisionId; }
        try { boolean changed = revisions.restore(id, revisionId, fingerprint); flash.addFlashAttribute("notice", changed ? "プロフィールを履歴から復元しました。" : "現在と同じ内容のため変更しませんでした。"); }
        catch (IllegalArgumentException exception) { flash.addFlashAttribute("error", exception.getMessage()); return "redirect:/characters/" + id + "/history/" + revisionId; }
        return "redirect:/characters/" + id;
    }
}
