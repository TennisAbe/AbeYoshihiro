package com.abeyoshihiro.character_manager;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-contained SVG with world frames and a central protagonist, shared by preview and export. */
@Service
public class RelationshipDiagramService {
    private final AffiliationOrderingService ordering;
    private final RelationRepository relations;
    public RelationshipDiagramService(AffiliationOrderingService ordering, RelationRepository relations) {
        this.ordering = ordering; this.relations = relations;
    }
    public record Diagram(List<OriginalCharacter> characters, List<CharacterRelation> relations) { }
    @Transactional(readOnly = true)
    public Diagram selected(List<Long> ids) { return select(ordering.roster(), relations.findAll(), ids); }
    public Diagram select(List<OriginalCharacter> roster, List<CharacterRelation> connections, List<Long> ids) {
        Set<Long> selected = ids == null ? Set.of() : new HashSet<>(ids);
        Set<Long> known = new HashSet<>(roster.stream().map(OriginalCharacter::getId).toList());
        if (!known.containsAll(selected)) throw new IllegalArgumentException("選択したキャラクターが見つかりません。選び直してください。");
        if (selected.isEmpty()) return new Diagram(roster, connections);
        List<CharacterRelation> edges = connections.stream().filter(r -> selected.contains(r.getSource().getId()) || selected.contains(r.getTarget().getId())).toList();
        Set<Long> visible = new HashSet<>(selected);
        edges.forEach(r -> { visible.add(r.getSource().getId()); visible.add(r.getTarget().getId()); });
        // Keep the central protagonist as context without inventing any relationship.
        return new Diagram(roster.stream().filter(c -> c.isProtagonist() || visible.contains(c.getId())).toList(), edges);
    }
    @Transactional(readOnly = true)
    public String exportSvg() { return exportSvg(List.of()); }
    @Transactional(readOnly = true)
    public String exportSvg(List<Long> ids) { Diagram view = selected(ids); return render(view.characters(), view.relations()); }

    private static String xml(String value) {
        if (value == null) return "";
        StringBuilder clean = new StringBuilder();
        value.codePoints().filter(c -> c == 9 || c == 10 || c == 13 || c >= 32 && c <= 0xd7ff || c >= 0xe000 && c <= 0xfffd || c >= 0x10000 && c <= 0x10ffff).forEach(clean::appendCodePoint);
        return clean.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
    private static String number(double n) { return String.format(Locale.ROOT, "%.1f", n); }
    private static String clip(String s, int length) {
        if (s == null) return "";
        return s.codePointCount(0, s.length()) <= length ? s : s.substring(0, s.offsetByCodePoints(0, length - 1)) + "…";
    }
    private record Point(double x, double y) { }
    private static int frameHeight(int count) { return Math.max(220, 112 + ((count + 1) / 2) * 104); }

    public String render(List<OriginalCharacter> roster, List<CharacterRelation> connections) {
        Map<CharacterWorld, List<OriginalCharacter>> groups = new EnumMap<>(CharacterWorld.class);
        for (CharacterWorld world : CharacterWorld.values()) groups.put(world, roster.stream().filter(c -> c.getWorld() == world).toList());
        int palaceHeight = frameHeight(groups.get(CharacterWorld.ALTER_PALACE).size());
        int otherHeight = frameHeight(groups.get(CharacterWorld.OTHER_WORLD).size());
        int modernHeight = frameHeight(groups.get(CharacterWorld.MODERN_WORLD).size());
        int heroHeight = frameHeight(groups.get(CharacterWorld.PROTAGONIST).size());
        int middleHeight = heroHeight + 44 + modernHeight;
        int sideHeight = Math.max(640, Math.max(palaceHeight, otherHeight));
        int middleY = 170 + Math.max(0, (sideHeight - middleHeight) / 2);
        int height = Math.max(170 + sideHeight, middleY + middleHeight) + 100;
        StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1600\" height=\"" + height + "\" viewBox=\"0 0 1600 " + height + "\" role=\"img\" aria-labelledby=\"title desc\">");
        svg.append("<title id=\"title\">NOCTALITH キャラクター相関図</title><desc id=\"desc\">主人公を中心にオルタパレス・異世界・現代世界の枠に人物を配置。矢印は登録した関係の向きです。</desc>")
           .append("<defs><radialGradient id=\"night\"><stop stop-color=\"#28203f\"/><stop offset=\"1\" stop-color=\"#0c0b13\"/></radialGradient><linearGradient id=\"card\" x2=\"1\" y2=\"1\"><stop stop-color=\"#30263f\"/><stop offset=\"1\" stop-color=\"#171421\"/></linearGradient><pattern id=\"stars\" width=\"60\" height=\"60\" patternUnits=\"userSpaceOnUse\"><circle cx=\"10\" cy=\"12\" r=\".7\" fill=\"#bca477\" opacity=\".23\"/></pattern><marker id=\"arrow\" viewBox=\"0 0 10 10\" refX=\"9\" refY=\"5\" markerWidth=\"7\" markerHeight=\"7\" orient=\"auto\"><path d=\"M 0 0 L 10 5 L 0 10 z\" fill=\"#d1b785\"/></marker></defs>")
           .append("<rect width=\"100%\" height=\"100%\" fill=\"url(#night)\"/><rect width=\"100%\" height=\"100%\" fill=\"url(#stars)\"/><g font-family=\"serif\" fill=\"#f2e8d6\"><text x=\"800\" y=\"55\" font-size=\"15\" letter-spacing=\"7\" text-anchor=\"middle\" fill=\"#c5a876\">NOCTALITH · CONNECTIONS</text><text x=\"800\" y=\"100\" font-size=\"30\" letter-spacing=\"5\" text-anchor=\"middle\">魂を結ぶ、物語の軌跡</text><path d=\"M 600 126 H 1000\" stroke=\"#806641\"/><text x=\"800\" y=\"151\" text-anchor=\"middle\" font-size=\"13\" fill=\"#b4a8c4\">矢印は関係の向き · ").append(roster.size()).append("人 / ").append(connections.size()).append("件の関係</text>");
        Map<Long, Point> positions = new HashMap<>();
        frame(svg, positions, groups.get(CharacterWorld.ALTER_PALACE), 32, 170, palaceHeight, "オルタパレス", "ALTER PALACE", "#ad91d8");
        frame(svg, positions, groups.get(CharacterWorld.OTHER_WORLD), 1088, 170, otherHeight, "異世界", "OTHER WORLD", "#81b8c9");
        frame(svg, positions, groups.get(CharacterWorld.PROTAGONIST), 560, middleY, heroHeight, "主人公", "THE PROTAGONIST", "#dfbd7a");
        frame(svg, positions, groups.get(CharacterWorld.MODERN_WORLD), 560, middleY + heroHeight + 44, modernHeight, "現代世界", "MODERN WORLD", "#94ab99");
        if (connections.isEmpty()) svg.append("<text x=\"800\" y=\"").append(height - 42).append("\" text-anchor=\"middle\" fill=\"#b4a8c4\" font-size=\"15\">関係はまだ登録されていません。</text>");
        Map<String, Integer> lanes = new HashMap<>();
        for (CharacterRelation r : connections.stream().sorted(Comparator.comparing(CharacterRelation::getId, Comparator.nullsLast(Comparator.naturalOrder()))).toList()) {
            Point a = positions.get(r.getSource().getId()), b = positions.get(r.getTarget().getId());
            if (a == null || b == null) continue;
            double dx = b.x - a.x, dy = b.y - a.y, distance = Math.hypot(dx, dy);
            if (distance == 0) continue;
            double boundary = Math.min(dx == 0 ? Double.MAX_VALUE : 106 / Math.abs(dx), dy == 0 ? Double.MAX_VALUE : 37 / Math.abs(dy));
            int lane = lanes.merge(r.getSource().getId() + ":" + r.getTarget().getId(), 1, Integer::sum);
            double bend = 25 + lane * 22, cx = (a.x + b.x) / 2 - dy / distance * bend, cy = (a.y + b.y) / 2 + dx / distance * bend;
            svg.append("<g><title>").append(xml(r.getSource().getName())).append(" → ").append(xml(r.getTarget().getName())).append(" / ").append(xml(r.getRelationType())).append("：").append(xml(r.getDescription())).append("</title><path d=\"M ").append(number(a.x + dx * boundary)).append(" ").append(number(a.y + dy * boundary)).append(" Q ").append(number(cx)).append(" ").append(number(cy)).append(" ").append(number(b.x - dx * boundary * 1.07)).append(" ").append(number(b.y - dy * boundary * 1.07)).append("\" fill=\"none\" stroke=\"#d1b785\" stroke-opacity=\".8\" stroke-width=\"1.7\" marker-end=\"url(#arrow)\"/>");
            svg.append("<text x=\"").append(number((a.x + 2 * cx + b.x) / 4)).append("\" y=\"").append(number((a.y + 2 * cy + b.y) / 4 - 7)).append("\" text-anchor=\"middle\" font-size=\"13\" fill=\"#ead4ac\" stroke=\"#15111e\" stroke-width=\"5\" paint-order=\"stroke\">").append(xml(clip(r.getRelationType(), 14))).append("</text></g>");
        }
        // Draw cards last so lines cannot obscure names.
        for (OriginalCharacter c : roster) node(svg, c, positions.get(c.getId()));
        return svg.append("</g></svg>").toString();
    }
    private static void frame(StringBuilder svg, Map<Long, Point> positions, List<OriginalCharacter> members, int x, int y, int height, String label, String english, String accent) {
        svg.append("<g><rect x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"480\" height=\"").append(height).append("\" rx=\"18\" fill=\"#14111e\" fill-opacity=\".84\" stroke=\"").append(accent).append("\" stroke-opacity=\".6\"/><path d=\"M ").append(x + 22).append(" ").append(y + 74).append(" h 436\" stroke=\"").append(accent).append("\" stroke-opacity=\".3\"/><text x=\"").append(x + 24).append("\" y=\"").append(y + 32).append("\" font-size=\"12\" letter-spacing=\"3\" fill=\"").append(accent).append("\">").append(english).append("</text><text x=\"").append(x + 24).append("\" y=\"").append(y + 59).append("\" font-size=\"23\">").append(label).append("</text></g>");
        if (members.isEmpty()) svg.append("<text x=\"").append(x + 240).append("\" y=\"").append(y + 140).append("\" text-anchor=\"middle\" font-size=\"14\" fill=\"#85788f\">登録された人物はいません</text>");
        for (int i = 0; i < members.size(); i++) {
            double px = members.size() == 1 ? x + 240 : x + 128 + (i % 2) * 224;
            positions.put(members.get(i).getId(), new Point(px, y + 132 + (i / 2) * 104));
        }
    }
    private static void node(StringBuilder svg, OriginalCharacter c, Point point) {
        String accent = c.isProtagonist() ? "#dfbd7a" : "#8f7d9f";
        svg.append("<g><title>").append(xml(c.getName())).append("</title><rect x=\"").append(number(point.x - 104)).append("\" y=\"").append(number(point.y - 36)).append("\" width=\"208\" height=\"72\" rx=\"10\" fill=\"url(#card)\" stroke=\"").append(accent).append("\"/><path d=\"M ").append(number(point.x - 91)).append(" ").append(number(point.y - 23)).append(" v 46\" stroke=\"").append(accent).append("\" stroke-width=\"2\"/>");
        String name = c.getName() == null ? "" : c.getName(); int[] points = name.codePoints().toArray();
        for (int line = 0; line < Math.min(2, (points.length + 9) / 10); line++) {
            String part = new String(points, line * 10, Math.min(10, points.length - line * 10));
            if (line == 1 && points.length > 20) part = clip(part, 9) + "…";
            svg.append("<text x=\"").append(number(point.x - 77)).append("\" y=\"").append(number(point.y + (points.length > 10 ? -4 + line * 23 : 7))).append("\" font-size=\"17\">").append(xml(part)).append("</text>");
        }
        svg.append("</g>");
    }
}
