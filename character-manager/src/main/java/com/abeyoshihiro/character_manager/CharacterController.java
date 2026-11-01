package com.abeyoshihiro.character_manager;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class CharacterController {

    private final CharacterRepository repository;

    public CharacterController(CharacterRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/")
    public String index(@RequestParam(name = "q", defaultValue = "") String query,
                        Model model) {
        String keyword = query.strip();
        model.addAttribute("characters",
                repository.findByNameContainingIgnoreCaseOrderByIdAsc(keyword));
        model.addAttribute("query", keyword);
        return "index";
    }

    @GetMapping("/characters/new")
    public String newCharacter(Model model) {
        model.addAttribute("character", new OriginalCharacter());
        return "character-form";
    }

    @PostMapping("/characters")
    public String create(
            @ModelAttribute("character") OriginalCharacter character) {
        if (character.getName() != null
                && !character.getName().isBlank()) {
            OriginalCharacter saved = new OriginalCharacter();
            saved.setName(character.getName().strip());
            saved.setPersonality(character.getPersonality());
            saved.setMemo(character.getMemo());
            repository.save(saved);
            return "redirect:/characters/" + saved.getId() + "/edit";
        }
        return "redirect:/characters/new";
    }
    
    @PostMapping("/characters/{id}/delete")
    public String delete(@PathVariable("id") Long id) {
        repository.deleteById(id);
        return "redirect:/";
    }
    
    @GetMapping("/characters/{id}/edit")
    public String edit(@PathVariable("id") Long id, Model model) {
        OriginalCharacter character = repository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND));

        model.addAttribute("character", character);
        return "character-form";
    }

    @PostMapping("/characters/{id}/update")
    public String update(
            @PathVariable("id") Long id,
            @ModelAttribute("character") OriginalCharacter input) {

        OriginalCharacter character = repository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (input.getName() != null && !input.getName().isBlank()) {
            character.setName(input.getName().strip());
            character.setPersonality(input.getPersonality());
            character.setMemo(input.getMemo());
            repository.save(character);
        }

        return "redirect:/characters/" + id + "/edit";
    }
}