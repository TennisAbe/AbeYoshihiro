package com.abeyoshihiro.character_manager;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CardColorController {
    private final CharacterRepository characters;
    private final TransactionTemplate transactions;
    public CardColorController(CharacterRepository characters, TransactionTemplate transactions) {
        this.characters = characters; this.transactions = transactions;
    }
    private String save(Long id, String color) {
        final String value;
        try { value = CardColors.normalize(color); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
        return transactions.execute(status -> {
            var character = characters.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            character.setCardColor(value); characters.save(character);
            return value == null ? "" : value;
        });
    }
    @PutMapping("/characters/{id}/card-color")
    @ResponseBody
    public Map<String, String> update(@PathVariable Long id, @RequestParam String color) {
        return Map.of("color", save(id, color));
    }
    @PostMapping("/characters/{id}/card-color")
    public String updateForm(@PathVariable Long id, @RequestParam int red, @RequestParam int green, @RequestParam int blue,
            @RequestParam(defaultValue = "false") boolean reset, @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String tag, @RequestParam(defaultValue = "oldest") String sort,
            @RequestParam(defaultValue = "false") boolean favorites, RedirectAttributes redirect) {
        if (!reset && (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RGBは0〜255で指定してください。");
        save(id, reset ? "" : String.format("#%02x%02x%02x", red, green, blue));
        redirect.addAttribute("q", q); redirect.addAttribute("tag", tag); redirect.addAttribute("sort", sort); redirect.addAttribute("favorites", favorites);
        return "redirect:/";
    }
}
