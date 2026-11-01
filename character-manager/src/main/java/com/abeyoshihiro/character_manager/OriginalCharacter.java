package com.abeyoshihiro.character_manager;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class OriginalCharacter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String personality;
    private String memo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPersonality() {
        return personality;
    }

    public void setPersonality(String personality) {
        this.personality = personality;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }
    
    private Boolean imageRegistered = false;

    public boolean isImageRegistered() {
        return Boolean.TRUE.equals(imageRegistered);
    }

    public void setImageRegistered(Boolean imageRegistered) {
        this.imageRegistered = imageRegistered;
    }
    
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "character_tags", joinColumns = @JoinColumn(name = "character_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"character_id", "tag"}))
    @Column(name = "tag", nullable = false, length = 30)
    private Set<String> tags = new LinkedHashSet<>();

    @Transient
    private String tagsInput;

    public Set<String> getTags() {
        return new TreeSet<>(tags);
    }

    public void replaceTags(Set<String> values) {
        tags.clear();
        tags.addAll(values);
    }

    public String getTagsInput() {
        return tagsInput != null ? tagsInput : String.join(", ", getTags());
    }

    public void setTagsInput(String tagsInput) {
        this.tagsInput = tagsInput;
    }
}
