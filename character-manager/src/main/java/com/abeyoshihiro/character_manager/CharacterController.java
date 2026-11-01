package com.abeyoshihiro.character_manager;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
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
    public String index(Model model) {
        model.addAttribute("characters", repository.findAll());
        model.addAttribute("character", new OriginalCharacter());
        return "index";
    }

    @PostMapping("/characters")
    public String create(
            @ModelAttribute("character") OriginalCharacter character) {
        if (character.getName() != null
                && !character.getName().isBlank()) {
            repository.save(character);
        }
        return "redirect:/";
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
        model.addAttribute("characters", repository.findAll());
        return "index";
    }

    @PostMapping("/characters/{id}/update")
    public String update(
            @PathVariable("id") Long id,
            @ModelAttribute("character") OriginalCharacter input) {

        OriginalCharacter character = repository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (input.getName() != null && !input.getName().isBlank()) {
            character.setName(input.getName());
            character.setPersonality(input.getPersonality());
            character.setMemo(input.getMemo());
            repository.save(character);
        }

        return "redirect:/";
    }
}