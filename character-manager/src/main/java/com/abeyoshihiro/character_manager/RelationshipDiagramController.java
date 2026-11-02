package com.abeyoshihiro.character_manager;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class RelationshipDiagramController {
    private final RelationshipDiagramService diagrams;
    private final AffiliationOrderingService ordering;
    public RelationshipDiagramController(RelationshipDiagramService diagrams, AffiliationOrderingService ordering) { this.diagrams = diagrams; this.ordering = ordering; }
    private RelationshipDiagramService.Diagram selected(List<Long> ids) {
        try { return diagrams.selected(ids); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
    }
    @GetMapping("/relationships")
    public String index(@RequestParam(required = false) List<Long> ids, Model model) {
        var view = selected(ids);
        model.addAttribute("relations", view.relations()); model.addAttribute("roster", ordering.roster());
        model.addAttribute("selectedIds", ids == null ? List.of() : ids);
        model.addAttribute("selection", ids == null ? "" : String.join(",", ids.stream().map(String::valueOf).toList()));
        model.addAttribute("visibleCount", view.characters().size());
        return "relationships";
    }
    @GetMapping("/relationships/diagram.svg")
    public ResponseEntity<byte[]> svg(@RequestParam(defaultValue = "false") boolean download, @RequestParam(required = false) List<Long> ids) {
        var view = selected(ids);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("image/svg+xml;charset=UTF-8"))
                .header("Content-Disposition", (download ? "attachment" : "inline") + "; filename=\"noctalith-relationships.svg\"")
                .header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(diagrams.render(view.characters(), view.relations()).getBytes(StandardCharsets.UTF_8));
    }
}
