package com.abeyoshihiro.character_manager;

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
    
    
}