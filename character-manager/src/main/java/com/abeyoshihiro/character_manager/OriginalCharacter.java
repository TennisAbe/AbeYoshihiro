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
    @Column(length = 2000)
    private String personality;
    @Column(length = 8000)
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
    // Legacy fields are retained only for non-destructive migration and old ZIP imports.
    @Column(length = 40)
    private String age;
    public String getAge() { return age; }
    public void setAge(String value) { age = value; }

    @Column(length = 40)
    private String birthday;
    public String getBirthday() { return birthday; }
    public void setBirthday(String value) { birthday = value; }

    @Column(length = 40)
    private String height;
    public String getHeight() { return height; }
    public void setHeight(String value) { height = value; }

    @Column(length = 80)
    private String species;
    public String getSpecies() { return species; }
    public void setSpecies(String value) { species = value; }

    @Column(length = 120)
    private String affiliation;
    public String getAffiliation() { return affiliation; }
    public void setAffiliation(String value) { affiliation = value; }

    @Column(length = 4000)
    private String abilities;
    public String getAbilities() { return abilities; }
    public void setAbilities(String value) { abilities = value; }

    @Column(length = 8)
    private String gender;
    @Column(length = 8)
    private String heightCm;
    @Column(length = 120)
    private String bustCm;
    public String getGender() { return gender == null || gender.isEmpty() ? null : gender; }
    public void setGender(String value) { gender = value; }
    public String getHeightCm() { return heightCm == null || heightCm.isEmpty() ? null : heightCm; }
    public void setHeightCm(String value) { heightCm = value; }
    public String getBustCm() { return "女".equals(gender) ? (bustCm == null || bustCm.isEmpty() ? null : bustCm) : null; }
    public void setBustCm(String value) { bustCm = value; }

    @Column(length = 7)
    private String cardColor;
    public String getCardColor() { return cardColor; }
    public void setCardColor(String value) { cardColor = CardColors.normalize(value); }
    public String getCardRgb() { return CardColors.rgb(cardColor); }
    private int cardChannel(int offset) { return Integer.parseInt((cardColor == null ? "#33213e" : cardColor).substring(offset, offset + 2), 16); }
    public int getCardRed() { return cardChannel(1); }
    public int getCardGreen() { return cardChannel(3); }
    public int getCardBlue() { return cardChannel(5); }

    private Boolean favorite = false;
    private Long coverImageId;
    public boolean isFavorite() { return Boolean.TRUE.equals(favorite); }
    public void setFavorite(Boolean value) { favorite = value; }
    public Long getCoverImageId() { return coverImageId; }
    public void setCoverImageId(Long value) { coverImageId = value; }
    @Column(length = 12000)
    private String basicSettings;
    public String getBasicSettings() { return basicSettings; }
    public void setBasicSettings(String value) { basicSettings = value; }

    @Column(length = 2000)
    private String speechStyle;
    public String getSpeechStyle() { return speechStyle; }
    public void setSpeechStyle(String value) { speechStyle = value; }

    @Column(length = 120)
    private String firstPerson;
    public String getFirstPerson() { return firstPerson; }
    public void setFirstPerson(String value) { firstPerson = value; }

    @Column(length = 4000)
    private String feelingsTowardMikado;
    public String getFeelingsTowardMikado() { return feelingsTowardMikado; }
    public void setFeelingsTowardMikado(String value) { feelingsTowardMikado = value; }

    @Column(length = 2000)
    private String likes;
    public String getLikes() { return likes; }
    public void setLikes(String value) { likes = value; }

    @Column(length = 2000)
    private String dislikes;
    public String getDislikes() { return dislikes; }
    public void setDislikes(String value) { dislikes = value; }

    // Store enum names as VARCHAR so adding a category upgrades existing H2 ENUM columns.
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(length = 24)
    private CharacterWorld world;
    public boolean isProtagonist() { return getWorld() == CharacterWorld.PROTAGONIST; }
    public CharacterWorld getWorld() { return world == null ? CharacterWorld.ALTER_PALACE : world; }
    public void setWorld(CharacterWorld value) { world = value; }
    public boolean initializeWorld() {
        if (world != null) return false;
        world = CharacterWorld.ALTER_PALACE; return true;
    }
}
