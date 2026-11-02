package com.abeyoshihiro.character_manager;

import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class RelationshipDiagramController {
    private final RelationshipDiagramService diagrams;
    private final RelationRepository relations;
    public RelationshipDiagramController(RelationshipDiagramService diagrams, RelationRepository relations) { this.diagrams = diagrams; this.relations = relations; }
    @GetMapping("/relationships")
    public String index(Model model) {
        model.addAttribute("relations", relations.findAll());
        return "relationships";
    }
    @GetMapping("/relationships/diagram.svg")
    public ResponseEntity<byte[]> svg(@RequestParam(defaultValue = "false") boolean download) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("image/svg+xml;charset=UTF-8"))
                .header("Content-Disposition", (download ? "attachment" : "inline") + "; filename=\"noctalith-relationships.svg\"")
                .header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(diagrams.exportSvg().getBytes(StandardCharsets.UTF_8));
    }
}
