package com.abeyoshihiro.character_manager;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CharacterController {
    private final CharacterRepository repository;
    private final RevisionService revisions;
    private final RevisionRepository revisionRepository;
    private final AffiliationOrderingService ordering;
    private final GalleryService gallery;
    private final PictureRepository pictures;
    private final RelationRepository relations;
    private final TransactionTemplate transactions;
    public CharacterController(CharacterRepository repository, GalleryService gallery, PictureRepository pictures,
                               RelationRepository relations, TransactionTemplate transactions, AffiliationOrderingService ordering, RevisionService revisions, RevisionRepository revisionRepository) {
        this.revisions = revisions; this.revisionRepository = revisionRepository; this.ordering = ordering; this.repository = repository; this.gallery = gallery; this.pictures = pictures; this.relations = relations; this.transactions = transactions;
    }
    @InitBinder("character")
    public void bindCharacter(WebDataBinder binder) {
        binder.setAllowedFields("world", "name", "affiliation", "basicSettings", "personality", "speechStyle", "firstPerson", "feelingsTowardMikado", "likes", "dislikes", "abilities", "tagsInput");
    }
    @GetMapping("/")
    public String index(@RequestParam(name = "q", defaultValue = "") String query,
                        @RequestParam(name = "tag", defaultValue = "") String tag,
                        @RequestParam(defaultValue = "oldest") String sort,
                        @RequestParam(defaultValue = "false") boolean favorites, Model model) {
        String keyword = query.strip(); String selectedTag = tag.strip();
        List<OriginalCharacter> found = selectedTag.isEmpty()
                ? repository.findByNameContainingIgnoreCaseOrderByIdAsc(keyword)
                : repository.findByNameContainingIgnoreCaseAndTagsContainingOrderByIdAsc(keyword, selectedTag);
        Map<String, Comparator<OriginalCharacter>> sorts = Map.of(
                "oldest", Comparator.comparing(OriginalCharacter::getId),
                "newest", Comparator.comparing(OriginalCharacter::getId).reversed(),
                "name", Comparator.comparing(OriginalCharacter::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(OriginalCharacter::getId),
                "favorite", Comparator.comparing(OriginalCharacter::isFavorite).reversed().thenComparing(OriginalCharacter::getId));
        String selectedSort = sorts.containsKey(sort) ? sort : "oldest";
        List<OriginalCharacter> displayed = new ArrayList<>();
        List<AffiliationOrderingService.WorldSection> groups = new ArrayList<>();
        for (CharacterWorld world : CharacterWorld.values()) {
            List<OriginalCharacter> items = found.stream().filter(c -> c.getWorld() == world && (!favorites || c.isFavorite()))
                    .sorted(ordering.comparator(world, sorts.get(selectedSort))).toList();
            groups.add(new AffiliationOrderingService.WorldSection(world, items, displayed.size(), List.of()));
            displayed.addAll(items);
        }
        model.addAttribute("characters", displayed);
        model.addAttribute("worldGroups", groups);
        model.addAttribute("selectedTag", selectedTag); model.addAttribute("tags", repository.findAllTags());
        model.addAttribute("query", keyword); model.addAttribute("sort", selectedSort); model.addAttribute("favorites", favorites);
        return "index";
    }
    private void populate(OriginalCharacter c, Model model) {
        model.addAttribute("worlds", CharacterWorld.values());
        model.addAttribute("pictures", c.getId() == null ? List.of() : gallery.list(c.getId()));
        model.addAttribute("outgoing", c.getId() == null ? List.of() : relations.findBySourceIdOrderByIdAsc(c.getId()));
        model.addAttribute("incoming", c.getId() == null ? List.of() : relations.findByTargetIdOrderByIdAsc(c.getId()));
        model.addAttribute("others", repository.findAll().stream().filter(other -> !Objects.equals(other.getId(), c.getId())).sorted(Comparator.comparing(OriginalCharacter::getId)).toList());
    }
    @GetMapping("/characters/new")
    public String newCharacter(Model model) {
        OriginalCharacter c = new OriginalCharacter(); model.addAttribute("character", c); populate(c, model); return "character-form";
    }
    @PostMapping("/characters")
    public String create(@ModelAttribute("character") OriginalCharacter character, BindingResult binding, Model model) {
        if (!validate(character, binding)) {
            // Preserve the existing empty-name redirect, while displaying all other validation errors in place.
            if (!binding.hasFieldErrors("tagsInput") && (character.getName() == null || character.getName().isBlank())) return "redirect:/characters/new";
            populate(character, model); return "character-form";
        }
        OriginalCharacter saved = new OriginalCharacter(); ProfileFields.copy(character, saved); repository.save(saved);
        return "redirect:/characters/" + saved.getId() + "/edit";
    }
    @PostMapping("/characters/{id}/delete")
    public String delete(@PathVariable("id") Long id) throws IOException {
        synchronized (gallery) {
            gallery.character(id);
            List<String> files = new ArrayList<>(gallery.list(id).stream().map(CharacterPicture::getStorageName).toList());
            files.add(id + ".png");
            transactions.executeWithoutResult(status -> {
                revisionRepository.deleteByCharacterId(id); relations.deleteBySourceIdOrTargetId(id, id); pictures.deleteAll(gallery.list(id)); pictures.flush(); repository.deleteById(id);
            });
            for (String file : new HashSet<>(files)) Files.deleteIfExists(gallery.path(file));
        }
        return "redirect:/";
    }
    @GetMapping("/characters/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        OriginalCharacter c = gallery.character(id); model.addAttribute("character", c); populate(c, model);
        model.addAttribute("profileSections", ProfileFields.values(c).entrySet().stream()
                .filter(e -> !java.util.Set.of("name", "affiliation").contains(e.getKey()))
                .map(e -> new RevisionService.Difference(ProfileSnapshot.labels().get(e.getKey()), e.getValue(), "")).toList());
        model.addAttribute("history", revisions.entries(c));
        return "character-detail";
    }
    @GetMapping("/characters/{id}/edit")
    public String edit(@PathVariable("id") Long id, Model model) {
        OriginalCharacter c = gallery.character(id); model.addAttribute("character", c); populate(c, model); return "character-form";
    }
    @PostMapping("/characters/{id}/update")
    public String update(@PathVariable("id") Long id, @ModelAttribute("character") OriginalCharacter input,
                         BindingResult binding, Model model) {
        if (!validate(input, binding)) {
            OriginalCharacter c = gallery.character(id);
            input.setId(id); input.setImageRegistered(c.isImageRegistered()); input.setCoverImageId(c.getCoverImageId()); input.setFavorite(c.isFavorite());
            populate(input, model); return "character-form";
        }
        revisions.update(id, input); return "redirect:/characters/" + id;
    }
    @PostMapping("/characters/{id}/favorite")
    public String favorite(@PathVariable("id") Long id, @RequestParam(defaultValue = "") String q,
                           @RequestParam(defaultValue = "") String tag, @RequestParam(defaultValue = "oldest") String sort,
                           @RequestParam(defaultValue = "false") boolean favorites, RedirectAttributes redirect) {
        transactions.executeWithoutResult(status -> {
            OriginalCharacter c = gallery.character(id); c.setFavorite(!c.isFavorite()); repository.save(c);
        });
        redirect.addAttribute("q", q); redirect.addAttribute("tag", tag); redirect.addAttribute("sort", sort); redirect.addAttribute("favorites", favorites);
        return "redirect:/";
    }
    private boolean validate(OriginalCharacter input, BindingResult binding) {
        try { TagParser.parse(input.getTagsInput()); }
        catch (IllegalArgumentException exception) { binding.rejectValue("tagsInput", "tags.invalid", exception.getMessage()); }
        ProfileFields.values(input).forEach((field, value) -> {
            if (value != null && value.length() > ProfileFields.limits().get(field)) binding.rejectValue(field, "profile.length", "入力できる文字数を超えています。");
        });
        if (input.getName() == null || input.getName().isBlank()) binding.rejectValue("name", "name.required", "名前を入力してください。");
        return !binding.hasErrors();
    }
}
