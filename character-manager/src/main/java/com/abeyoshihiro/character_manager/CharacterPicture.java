package com.abeyoshihiro.character_manager;

import jakarta.persistence.*;

@Entity
public class CharacterPicture {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private OriginalCharacter character;
    @Column(nullable = false, unique = true)
    private String storageName;
    @Column(length = 100)
    private String title;
    public Long getId() { return id; }
    public OriginalCharacter getCharacter() { return character; }
    public void setCharacter(OriginalCharacter value) { character = value; }
    public String getStorageName() { return storageName; }
    public void setStorageName(String value) { storageName = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
}
