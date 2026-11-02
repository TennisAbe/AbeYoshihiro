package com.abeyoshihiro.character_manager;

import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AffiliationOrderController {
    private final AffiliationOrderingService ordering;
    public AffiliationOrderController(AffiliationOrderingService ordering) { this.ordering = ordering; }
    @GetMapping("/affiliations/order")
    public String index(Model model) {
        model.addAttribute("sections", CharacterWorld.worlds().stream().map(w -> new AffiliationOrderingService.WorldSection(w, List.of(), 0, ordering.current(w))).toList());
        return "affiliation-order";
    }
    @PostMapping("/affiliations/order/{world}")
    public String save(@PathVariable CharacterWorld world,
                       @RequestParam(defaultValue = "") List<String> affiliations,
                       @RequestParam(defaultValue = "") List<Integer> positions, RedirectAttributes flash) {
        try { ordering.save(world, affiliations, positions); flash.addFlashAttribute("notice", world.getLabel() + "の所属の表示順を保存しました。"); }
        catch (IllegalArgumentException exception) { flash.addFlashAttribute("error", exception.getMessage()); }
        return "redirect:/affiliations/order";
    }
}
