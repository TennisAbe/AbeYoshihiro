package com.abeyoshihiro.character_manager;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RelationController {
    private final CharacterRepository characters;
    private final RelationRepository relations;
    public RelationController(CharacterRepository characters, RelationRepository relations) { this.characters = characters; this.relations = relations; }
    private OriginalCharacter character(Long id) { return characters.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private CharacterRelation relation(Long owner, Long id) {
        CharacterRelation r = relations.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!r.getSource().getId().equals(owner)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return r;
    }
    private boolean valid(String type, String description, RedirectAttributes flash) {
        if (type.isBlank() || type.length() > 60 || description.length() > 2000) {
            flash.addFlashAttribute("error", "関係の種類は1〜60文字、説明は2000文字以内で入力してください。"); return false;
        }
        return true;
    }
    @PostMapping("/characters/{id}/relations") @Transactional
    public String create(@PathVariable("id") Long id, @RequestParam Long targetId,
                         @RequestParam String relationType, @RequestParam(defaultValue = "") String description, RedirectAttributes flash) {
        OriginalCharacter source = character(id); OriginalCharacter target = character(targetId);
        if (id.equals(targetId)) flash.addFlashAttribute("error", "自分自身との関係は登録できません。");
        else if (valid(relationType, description, flash)) {
            CharacterRelation r = new CharacterRelation(); r.setSource(source); r.setTarget(target);
            r.setRelationType(relationType.strip()); r.setDescription(description); relations.save(r);
        }
        return "redirect:/characters/" + id + "/edit";
    }
    @PostMapping("/characters/{id}/relations/{relationId}/update") @Transactional
    public String update(@PathVariable("id") Long id, @PathVariable("relationId") Long relationId,
                         @RequestParam String relationType, @RequestParam(defaultValue = "") String description, RedirectAttributes flash) {
        CharacterRelation r = relation(id, relationId);
        if (valid(relationType, description, flash)) { r.setRelationType(relationType.strip()); r.setDescription(description); relations.save(r); }
        return "redirect:/characters/" + id + "/edit";
    }
    @PostMapping("/characters/{id}/relations/{relationId}/delete") @Transactional
    public String delete(@PathVariable("id") Long id, @PathVariable("relationId") Long relationId) {
        relations.delete(relation(id, relationId)); return "redirect:/characters/" + id + "/edit";
    }
}
