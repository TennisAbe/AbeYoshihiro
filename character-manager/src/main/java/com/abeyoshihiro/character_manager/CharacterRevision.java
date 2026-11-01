package com.abeyoshihiro.character_manager;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
public class CharacterRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @OnDelete(action = OnDeleteAction.CASCADE)
    private OriginalCharacter character;
    @Column(nullable = false)
    private Instant savedAt;
    @Column(nullable = false, length = 16)
    private String reason;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "character_revision_field", joinColumns = @JoinColumn(name = "revision_id"))
    @MapKeyColumn(name = "field_name", length = 40)
    @Column(name = "field_value", length = 12000, nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Map<String, String> fields = new LinkedHashMap<>();
    public Long getId() { return id; }
    public OriginalCharacter getCharacter() { return character; }
    public void setCharacter(OriginalCharacter value) { character = value; }
    public Instant getSavedAt() { return savedAt; }
    public void setSavedAt(Instant value) { savedAt = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public Map<String, String> getFields() { return new LinkedHashMap<>(fields); }
    public void setFields(Map<String, String> value) { fields.clear(); fields.putAll(value); }
    public String getTimestamp() { return DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").withZone(ZoneId.of("Asia/Tokyo")).format(savedAt); }
    public String getReasonLabel() { return "RESTORE".equals(reason) ? "復元前の状態" : "更新前の状態"; }
}
