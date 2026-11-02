package com.abeyoshihiro.character_manager;

import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RevisionService {
    private final CharacterRepository characters;
    private final RevisionRepository revisions;
    public RevisionService(CharacterRepository characters, RevisionRepository revisions) { this.characters = characters; this.revisions = revisions; }
    public List<CharacterRevision> list(Long owner) { return revisions.findByCharacterIdOrderBySavedAtDescIdDesc(owner); }
    public CharacterRevision revision(Long owner, Long id) {
        CharacterRevision revision = revisions.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!revision.getCharacter().getId().equals(owner)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return revision;
    }
    private OriginalCharacter locked(Long id) { return characters.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private void capture(OriginalCharacter current, String reason) {
        CharacterRevision revision = new CharacterRevision(); revision.setCharacter(current); revision.setSavedAt(Instant.now()); revision.setReason(reason);
        revision.setFields(ProfileSnapshot.of(current)); revisions.saveAndFlush(revision);
        List<CharacterRevision> history = list(current.getId());
        if (history.size() > 3) revisions.deleteAll(history.subList(3, history.size()));
    }
    @Transactional
    public void update(Long id, OriginalCharacter input) {
        OriginalCharacter current = locked(id); OriginalCharacter candidate = new OriginalCharacter(); ProfileFields.copy(input, candidate); ProfileFields.validate(candidate);
        if (ProfileSnapshot.of(current).equals(ProfileSnapshot.of(candidate))) return;
        capture(current, "UPDATE"); ProfileFields.copy(input, current); characters.save(current);
    }
    @Transactional
    public boolean restore(Long owner, Long revisionId, String expectedFingerprint) {
        OriginalCharacter current = locked(owner); CharacterRevision revision = revision(owner, revisionId);
        if (!ProfileSnapshot.fingerprint(current).equals(expectedFingerprint))
            throw new IllegalArgumentException("確認後にプロフィールが更新されています。差分を確認し直してください。");
        Map<String, String> fields = ProfileSnapshot.compatible(revision.getFields()); ProfileSnapshot.validate(fields);
        if (ProfileSnapshot.of(current).equals(fields)) return false;
        capture(current, "RESTORE"); ProfileSnapshot.apply(fields, current); characters.save(current); return true;
    }
    public List<Difference> differences(OriginalCharacter current, CharacterRevision revision) {
        Map<String, String> now = ProfileSnapshot.of(current), previous = ProfileSnapshot.compatible(revision.getFields());
        List<Difference> result = new ArrayList<>();
        ProfileSnapshot.labels().forEach((key, label) -> {
            if (!Objects.equals(now.get(key), previous.get(key))) result.add(new Difference(label, display(key, now.get(key)), display(key, previous.get(key))));
        });
        return result;
    }
    private String display(String key, String value) {
        if ("world".equals(key)) return CharacterWorld.parse(value).getLabel();
        return value == null || value.isEmpty() ? "（未設定）" : value;
    }
    public List<HistoryEntry> entries(OriginalCharacter current) { return list(current.getId()).stream().map(r -> new HistoryEntry(r, differences(current, r))).toList(); }
    public static class Difference {
        private final String label, currentValue, historicalValue;
        public Difference(String label, String currentValue, String historicalValue) { this.label = label; this.currentValue = currentValue; this.historicalValue = historicalValue; }
        public String getLabel() { return label; }
        public String getCurrentValue() { return currentValue; }
        public String getHistoricalValue() { return historicalValue; }
    }
    public static class HistoryEntry {
        private final CharacterRevision revision;
        private final List<Difference> differences;
        public HistoryEntry(CharacterRevision revision, List<Difference> differences) { this.revision = revision; this.differences = differences; }
        public CharacterRevision getRevision() { return revision; }
        public List<Difference> getDifferences() { return differences; }
    }
}
