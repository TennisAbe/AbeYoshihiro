package com.abeyoshihiro.character_manager;
import jakarta.persistence.*;
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"world", "affiliation"}))
public class AffiliationOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private CharacterWorld world;
    @Column(nullable = false, length = 120)
    private String affiliation;
    @Column(nullable = false)
    private int position;
    public Long getId() { return id; }
    public CharacterWorld getWorld() { return world; }
    public void setWorld(CharacterWorld value) { world = value; }
    public String getAffiliation() { return affiliation; }
    public void setAffiliation(String value) { affiliation = value; }
    public int getPosition() { return position; }
    public void setPosition(int value) { position = value; }
}
