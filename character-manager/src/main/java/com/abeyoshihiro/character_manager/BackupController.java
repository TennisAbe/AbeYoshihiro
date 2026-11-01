package com.abeyoshihiro.character_manager;
import java.time.LocalDate;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
public class BackupController {
    private final BackupService backup;
    public BackupController(BackupService backup) { this.backup = backup; }
    @GetMapping("/backup") public String index() { return "backup"; }
    @GetMapping("/backup/download")
    public Object download(RedirectAttributes flash) {
        try {
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                    .header("Content-Disposition", "attachment; filename=\"noctalith-" + LocalDate.now() + ".zip\"")
                    .header("Cache-Control", "no-store").body(backup.exportArchive());
        } catch (Exception e) {
            flash.addFlashAttribute("error", e instanceof IllegalArgumentException ? e.getMessage() : "バックアップを作成できませんでした。保存先を確認してください。");
            return "redirect:/backup";
        }
    }
    @PostMapping("/backup/restore")
    public String restore(@RequestParam MultipartFile archive, @RequestParam(defaultValue = "false") boolean confirmed, RedirectAttributes flash) {
        if (!confirmed) flash.addFlashAttribute("error", "追加復元の確認にチェックしてください。");
        else if (archive.isEmpty() || archive.getSize() > 100L * 1024 * 1024) flash.addFlashAttribute("error", "100MB以内のZIPを選択してください。");
        else try (var input = archive.getInputStream()) {
            int count = backup.restore(input); flash.addFlashAttribute("notice", count + "件のキャラクターを追加復元しました。");
        } catch (Exception e) {
            flash.addFlashAttribute("error", e instanceof IllegalArgumentException ? e.getMessage() : "復元できませんでした。バックアップファイルを確認してください。");
        }
        return "redirect:/backup";
    }
}
