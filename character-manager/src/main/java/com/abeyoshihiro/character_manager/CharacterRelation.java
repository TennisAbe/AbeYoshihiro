package com.abeyoshihiro.character_manager;
import jakarta.persistence.*;
@Entity
public class CharacterRelation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private OriginalCharacter source;
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private OriginalCharacter target;
    @Column(nullable = false, length = 60)
    private String relationType;
    @Column(length = 2000)
    private String description;
    public Long getId() { return id; }
    public OriginalCharacter getSource() { return source; }
    public void setSource(OriginalCharacter value) { source = value; }
    public OriginalCharacter getTarget() { return target; }
    public void setTarget(OriginalCharacter value) { target = value; }
    public String getRelationType() { return relationType; }
    public void setRelationType(String value) { relationType = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
}
