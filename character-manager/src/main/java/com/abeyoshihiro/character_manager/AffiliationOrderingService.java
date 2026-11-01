package com.abeyoshihiro.character_manager;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AffiliationOrderingService {
    private final CharacterRepository characters;
    private final AffiliationOrderRepository orders;
    public AffiliationOrderingService(CharacterRepository characters, AffiliationOrderRepository orders) {
        this.characters = characters; this.orders = orders;
    }
    public static String affiliation(OriginalCharacter c) { return c.getAffiliation() == null ? "" : c.getAffiliation().strip(); }
    public List<String> current(CharacterWorld world) {
        Set<String> used = new LinkedHashSet<>();
        characters.findAll().stream().sorted(Comparator.comparing(OriginalCharacter::getId))
                .filter(c -> c.getWorld() == world).map(AffiliationOrderingService::affiliation).filter(s -> !s.isEmpty()).forEach(used::add);
        List<String> result = new ArrayList<>();
        for (AffiliationOrder order : orders.findByWorldOrderByPositionAscIdAsc(world)) if (used.remove(order.getAffiliation())) result.add(order.getAffiliation());
        result.addAll(used); return result;
    }
    public Map<CharacterWorld, List<String>> snapshot() {
        Map<CharacterWorld, List<String>> result = new EnumMap<>(CharacterWorld.class);
        for (CharacterWorld world : CharacterWorld.values()) result.put(world, current(world));
        return result;
    }
    @Transactional
    public void save(CharacterWorld world, List<String> affiliations, List<Integer> positions) {
        List<String> used = current(world);
        if (affiliations.size() != positions.size() || affiliations.size() != used.size()
                || new HashSet<>(affiliations).size() != affiliations.size() || !new HashSet<>(affiliations).equals(new HashSet<>(used)))
            throw new IllegalArgumentException("所属の一覧が変更されています。画面を再読み込みして設定してください。");
        Set<Integer> unique = new HashSet<>(positions);
        if (unique.size() != used.size() || positions.stream().anyMatch(p -> p == null || p < 1 || p > used.size()))
            throw new IllegalArgumentException("表示順は1から所属数まで、重複しない番号で指定してください。");
        orders.deleteByWorld(world); orders.flush();
        for (int i = 0; i < affiliations.size(); i++) {
            AffiliationOrder order = new AffiliationOrder(); order.setWorld(world); order.setAffiliation(affiliations.get(i)); order.setPosition(positions.get(i)); orders.save(order);
        }
    }
    /** Existing preferences win during additive restore; new affiliations follow the archive order. */
    @Transactional
    public void merge(Map<CharacterWorld, List<String>> before, Map<CharacterWorld, List<String>> imported) {
        for (CharacterWorld world : CharacterWorld.values()) {
            Set<String> used = new LinkedHashSet<>(current(world)); List<String> combined = new ArrayList<>();
            for (String value : before.get(world)) if (used.remove(value)) combined.add(value);
            for (String value : imported.get(world)) if (used.remove(value)) combined.add(value);
            combined.addAll(used);
            save(world, combined, java.util.stream.IntStream.rangeClosed(1, combined.size()).boxed().toList());
        }
    }
    public Comparator<OriginalCharacter> comparator(CharacterWorld world, Comparator<OriginalCharacter> withinAffiliation) {
        List<String> values = current(world); Map<String, Integer> ranks = new HashMap<>();
        for (int i = 0; i < values.size(); i++) ranks.put(values.get(i), i);
        return Comparator.comparingInt((OriginalCharacter c) -> ranks.getOrDefault(affiliation(c), Integer.MAX_VALUE)).thenComparing(withinAffiliation);
    }
    /** Full roster in the default list order: world, affiliation rank, registration ID. */
    public List<OriginalCharacter> roster() {
        List<OriginalCharacter> all = characters.findAll();
        List<OriginalCharacter> result = new ArrayList<>();
        for (CharacterWorld world : CharacterWorld.values()) {
            all.stream().filter(c -> c.getWorld() == world)
                    .sorted(comparator(world, Comparator.comparing(OriginalCharacter::getId))).forEach(result::add);
        }
        return result;
    }
    public static class WorldSection {
        private final CharacterWorld world;
        private final List<OriginalCharacter> characters;
        private final int offset;
        private final List<String> affiliations;
        public WorldSection(CharacterWorld world, List<OriginalCharacter> characters, int offset, List<String> affiliations) {
            this.world = world; this.characters = characters; this.offset = offset; this.affiliations = affiliations;
        }
        public CharacterWorld getWorld() { return world; }
        public List<OriginalCharacter> getCharacters() { return characters; }
        public int getOffset() { return offset; }
        public List<String> getAffiliations() { return affiliations; }
    }
}
