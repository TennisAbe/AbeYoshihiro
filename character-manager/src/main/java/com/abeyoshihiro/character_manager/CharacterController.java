package com.abeyoshihiro.character_manager;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
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

    @InitBinder("character")
    public void bindCharacter(WebDataBinder binder) {
        binder.setAllowedFields("name", "personality", "memo", "tagsInput");
    }

    @GetMapping("/")
    public String index(@RequestParam(name = "q", defaultValue = "") String query,
                        @RequestParam(name = "tag", defaultValue = "") String tag,
                        Model model) {
        String keyword = query.strip();
        String selectedTag = tag.strip();
        model.addAttribute("characters", selectedTag.isEmpty()
                ? repository.findByNameContainingIgnoreCaseOrderByIdAsc(keyword)
                : repository.findByNameContainingIgnoreCaseAndTagsContainingOrderByIdAsc(keyword, selectedTag));
        model.addAttribute("selectedTag", selectedTag);
        model.addAttribute("tags", repository.findAllTags());
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
            @ModelAttribute("character") OriginalCharacter character,
            BindingResult binding) {
        if (!validateTags(character, binding)) {
            return "character-form";
        }
        if (character.getName() != null
                && !character.getName().isBlank()) {
            OriginalCharacter saved = new OriginalCharacter();
            saved.setName(character.getName().strip());
            saved.setPersonality(character.getPersonality());
            saved.setMemo(character.getMemo());
            saved.replaceTags(TagParser.parse(character.getTagsInput()));
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
            @ModelAttribute("character") OriginalCharacter input,
            BindingResult binding, Model model) {

        OriginalCharacter character = repository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!validateTags(input, binding)) {
            input.setId(id);
            input.setImageRegistered(character.isImageRegistered());
            return "character-form";
        }

        if (input.getName() != null && !input.getName().isBlank()) {
            character.setName(input.getName().strip());
            character.setPersonality(input.getPersonality());
            character.setMemo(input.getMemo());
            character.replaceTags(TagParser.parse(input.getTagsInput()));
            repository.save(character);
        }

        return "redirect:/characters/" + id + "/edit";
    }
    private boolean validateTags(OriginalCharacter input, BindingResult binding) {
        try {
            TagParser.parse(input.getTagsInput());
        } catch (IllegalArgumentException exception) {
            binding.rejectValue("tagsInput", "tags.invalid", exception.getMessage());
        }
        return !binding.hasErrors();
    }
}
