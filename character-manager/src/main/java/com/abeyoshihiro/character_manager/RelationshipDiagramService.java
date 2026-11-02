package com.abeyoshihiro.character_manager;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Standalone SVG: no remote resources, executable markup or browser-only dependencies. */
@Service
public class RelationshipDiagramService {
    private final AffiliationOrderingService ordering;
    private final RelationRepository relations;
    public RelationshipDiagramService(AffiliationOrderingService ordering, RelationRepository relations) {
        this.ordering = ordering; this.relations = relations;
    }
    @Transactional(readOnly = true)
    public String exportSvg() { return render(ordering.roster(), relations.findAll()); }

    private static String xml(String value) {
        if (value == null) return "";
        StringBuilder clean = new StringBuilder();
        value.codePoints().filter(c -> c == 9 || c == 10 || c == 13 || c >= 32 && c <= 0xd7ff || c >= 0xe000 && c <= 0xfffd || c >= 0x10000 && c <= 0x10ffff).forEach(clean::appendCodePoint);
        return clean.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
    private static String number(double n) { return String.format(Locale.ROOT, "%.1f", n); }
    private static String shortName(String value) {
        if (value == null) return "";
        return value.codePointCount(0, value.length()) <= 16 ? value : value.substring(0, value.offsetByCodePoints(0, 15)) + "…";
    }
    public String render(List<OriginalCharacter> roster, List<CharacterRelation> connections) {
        // One lane per relation keeps direction, parallel and reciprocal edges distinct even in dense graphs.
        double radius = Math.max(300, roster.size() * 60.0);
        int width = Math.max(1100, (int) (radius * 2 + 400)), rowHeight = 100;
        int overviewHeight = roster.isEmpty() ? 100 : width;
        int height = overviewHeight + 180 + rowHeight * Math.max(1, connections.size() + roster.size());
        StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + width + "\" height=\"" + height + "\" viewBox=\"0 0 " + width + " " + height + "\" role=\"img\" aria-labelledby=\"title desc\">");
        svg.append("<title id=\"title\">NOCTALITH キャラクター相関図</title><desc id=\"desc\">矢印は登録した関係の向きです。同名の人物はCから始まるIDで区別します。全キャラクター一覧を末尾に表示します。</desc>")
           .append("<defs><marker id=\"arrow\" viewBox=\"0 0 10 10\" refX=\"9\" refY=\"5\" markerWidth=\"8\" markerHeight=\"8\" orient=\"auto-start-reverse\"><path d=\"M 0 0 L 10 5 L 0 10 z\" fill=\"#6b3fa0\"/></marker></defs>")
           .append("<rect width=\"100%\" height=\"100%\" fill=\"#faf7ff\"/><g font-family=\"sans-serif\" fill=\"#251c38\"><text x=\"30\" y=\"35\" font-size=\"24\">キャラクター相関図（矢印：関係の向き）</text>");
        List<CharacterRelation> sorted = connections.stream().sorted(Comparator.comparing(CharacterRelation::getId, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        Map<Long, double[]> positions = new HashMap<>();
        for (int i = 0; i < roster.size(); i++) {
            double angle = 2 * Math.PI * i / roster.size() - Math.PI / 2;
            positions.put(roster.get(i).getId(), new double[]{width / 2.0 + radius * Math.cos(angle), width / 2.0 + radius * Math.sin(angle)});
        }
        Map<String, Integer> lanes = new HashMap<>();
        for (CharacterRelation r : connections) {
            double[] a = positions.get(r.getSource().getId()), b = positions.get(r.getTarget().getId());
            if (a == null || b == null) continue;
            double dx = b[0] - a[0], dy = b[1] - a[1], distance = Math.hypot(dx, dy);
            if (distance == 0) continue;
            int lane = lanes.merge(r.getSource().getId() + ":" + r.getTarget().getId(), 1, Integer::sum);
            double cx = (a[0] + b[0]) / 2 - dy / distance * (50 + lane * 35), cy = (a[1] + b[1]) / 2 + dx / distance * (50 + lane * 35);
            svg.append("<g><title>").append(xml(r.getSource().getName())).append(" → ").append(xml(r.getTarget().getName())).append(" / ").append(xml(r.getRelationType())).append("</title><path d=\"M ").append(number(a[0] + dx / distance * 145)).append(" ").append(number(a[1] + dy / distance * 40)).append(" Q ").append(number(cx)).append(" ").append(number(cy)).append(" ").append(number(b[0] - dx / distance * 150)).append(" ").append(number(b[1] - dy / distance * 45)).append("\" fill=\"none\" stroke=\"#6b3fa0\" stroke-width=\"2\" marker-end=\"url(#arrow)\"/>");
            svg.append("<text x=\"").append(number((a[0]+2*cx+b[0])/4)).append("\" y=\"").append(number((a[1]+2*cy+b[1])/4)).append("\" text-anchor=\"middle\" font-size=\"14\">").append(xml(shortName(r.getRelationType()))).append("</text></g>");
        }
        for (OriginalCharacter c : roster) {
            double[] point = positions.get(c.getId()); node(svg, c, (int) point[0] - 140, (int) point[1]);
        }
        int y = overviewHeight + 90;
        if (sorted.isEmpty()) svg.append("<text x=\"30\" y=\"65\">関係はまだ登録されていません。</text>");
        for (CharacterRelation r : sorted) {
            svg.append("<g><title>").append(xml(r.getSource().getName())).append(" → ").append(xml(r.getTarget().getName())).append(" / ").append(xml(r.getRelationType())).append("：").append(xml(r.getDescription())).append("</title>");
            node(svg, r.getSource(), 20, y); node(svg, r.getTarget(), 800, y);
            svg.append("<path d=\"M 300 ").append(y + 10).append(" H 790\" fill=\"none\" stroke=\"#6b3fa0\" stroke-width=\"2\" marker-end=\"url(#arrow)\"/>")
               .append("<text x=\"545\" y=\"").append(y).append("\" text-anchor=\"middle\" font-size=\"16\">").append(xml(shortName(r.getRelationType()))).append("</text></g>");
            y += rowHeight;
        }
        svg.append("<text x=\"30\" y=\"").append(y + 10).append("\" font-size=\"20\">全キャラクター（関係のない人物も含む）</text>");
        y += rowHeight;
        for (OriginalCharacter c : roster) { node(svg, c, 20, y); y += rowHeight; }
        return svg.append("</g></svg>").toString();
    }
    private static void node(StringBuilder svg, OriginalCharacter c, int x, int y) {
        svg.append("<g><title>").append(xml(c.getName())).append("</title><rect x=\"").append(x).append("\" y=\"").append(y - 28).append("\" width=\"280\" height=\"65\" rx=\"10\" fill=\"#eee4fa\" stroke=\"#6b3fa0\"/><text x=\"").append(x + 12).append("\" y=\"").append(y - 4).append("\" font-size=\"16\">").append(xml(shortName(c.getName()))).append("</text><text x=\"").append(x + 12).append("\" y=\"").append(y + 20).append("\" font-size=\"13\">C").append(String.format(Locale.ROOT, "%06d", c.getId())).append(" / ").append(xml(c.getWorld().getLabel())).append("</text></g>");
    }
}
