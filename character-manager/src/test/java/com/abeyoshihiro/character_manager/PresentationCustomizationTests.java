package com.abeyoshihiro.character_manager;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.w3c.dom.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:presentation", "spring.jpa.hibernate.ddl-auto=create-drop"})
class PresentationCustomizationTests {
    @Value("${local.server.port}") int port;
    @Autowired CharacterRepository characters;
    @Autowired RelationRepository relations;
    @Autowired RevisionRepository revisions;
    @Autowired RevisionService history;
    @Autowired BackupService backup;
    @Autowired ComicShareService share;
    @Autowired RelationshipDiagramService diagrams;
    final HttpClient client = HttpClient.newHttpClient();
    @BeforeEach void reset() { revisions.deleteAll(); relations.deleteAll(); characters.deleteAll(); }
    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    HttpResponse<String> request(String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Content-Type", "application/x-www-form-urlencoded")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    OriginalCharacter fresh(Long id) { return characters.findById(id).orElseThrow(); }
    OriginalCharacter save(String name) { var c = new OriginalCharacter(); c.setName(name); return characters.save(c); }
    static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        Map<String, byte[]> result = new HashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e; while ((e = zip.getNextEntry()) != null) result.put(e.getName(), zip.readAllBytes());
        }
        return result;
    }
    @Test void freeTextBustSurvivesEditingHistoryBackupAndShare() throws Exception {
        String bust = "Gカップ / 約90cm・未測定 <参考>";
        assertEquals(302, request("POST", "/characters", "name=TextBust&gender=" + enc("女") + "&bustCm=" + enc(bust)).statusCode());
        var c = characters.findAll().get(0); Long id = c.getId();
        assertEquals(bust, c.getBustCm());
        String detail = request("GET", "/characters/" + id, "").body();
        assertTrue(detail.contains("胸囲")); assertFalse(detail.contains("胸囲（cm）")); assertTrue(detail.contains("&lt;参考&gt;"));
        assertEquals(200, request("POST", "/characters/" + id + "/update", "name=Invalid&gender=" + enc("女") + "&bustCm=" + enc("あ".repeat(121))).statusCode());
        assertEquals(bust, fresh(id).getBustCm());
        assertEquals(302, request("POST", "/characters/" + id + "/update", "name=TextBust&gender=" + enc("女") + "&bustCm=" + enc("F〜G")).statusCode());
        var revision = history.list(id).get(0);
        assertTrue(history.restore(id, revision.getId(), ProfileSnapshot.fingerprint(fresh(id))));
        assertEquals(bust, fresh(id).getBustCm());
        var bytes = backup.exportArchive(); assertEquals(1, backup.restore(new ByteArrayInputStream(bytes)));
        var imported = characters.findAll().stream().max(Comparator.comparing(OriginalCharacter::getId)).orElseThrow();
        assertEquals(bust, imported.getBustCm());
        assertTrue(new String(unzip(share.exportAll()).get("01_ALL_CHARACTERS.md"), StandardCharsets.UTF_8).contains(bust));
        assertEquals(302, request("POST", "/characters/" + id + "/update", "name=TextBust&gender=" + enc("男") + "&bustCm=" + enc(bust)).statusCode());
        assertNull(fresh(id).getBustCm());
    }
    @Test void colorsAreValidatedPersistentAndIndependentOfProfileUpdates() throws Exception {
        var c = save("Tinted"); String path = "/characters/" + c.getId() + "/card-color";
        assertEquals(200, request("PUT", path, "color=%23AB34EF").statusCode());
        assertEquals("#ab34ef", fresh(c.getId()).getCardColor());
        String html = request("GET", "/", "").body(); assertTrue(html.contains("--card-rgb: 171, 52, 239;"));
        assertTrue(html.contains("has-custom-color"));
        for (String value : List.of("red", "#fff", "#123456;position:fixed", "rgb(0,0,0)", "<script>")) {
            assertEquals(400, request("PUT", path, "color=" + enc(value)).statusCode());
            assertEquals("#ab34ef", fresh(c.getId()).getCardColor());
        }
        assertEquals(404, request("PUT", "/characters/999999/card-color", "color=%23000000").statusCode());
        assertEquals(400, request("POST", path, "red=-1&green=10&blue=256").statusCode());
        assertEquals(302, request("POST", path, "red=0&green=128&blue=255").statusCode());
        assertEquals("#0080ff", fresh(c.getId()).getCardColor());
        assertEquals(302, request("POST", "/characters/" + c.getId() + "/update", "name=Changed").statusCode());
        assertEquals("#0080ff", fresh(c.getId()).getCardColor());
        var revision = history.list(c.getId()).get(0);
        history.restore(c.getId(), revision.getId(), ProfileSnapshot.fingerprint(fresh(c.getId())));
        assertEquals("#0080ff", fresh(c.getId()).getCardColor());
        byte[] bytes = backup.exportArchive(); assertEquals(1, backup.restore(new ByteArrayInputStream(bytes)));
        assertTrue(characters.findAll().stream().allMatch(item -> "#0080ff".equals(item.getCardColor())));
        String colors = new String(unzip(share.exportAll()).get("05_DISPLAY_COLORS.csv"), StandardCharsets.UTF_8);
        assertTrue(colors.contains("#0080ff"));
        assertEquals(200, request("PUT", path, "color=").statusCode()); assertNull(fresh(c.getId()).getCardColor());
        String oldXml = new String(unzip(bytes).get("manifest.xml"), StandardCharsets.UTF_8).replace("version=\"7\"", "version=\"6\"").replaceAll(" cardColor=\"[^\"]*\"", "");
        assertEquals(1, backup.restore(new ByteArrayInputStream(ProfileDiagramTests.archive(oldXml))));
        assertNull(characters.findAll().stream().max(Comparator.comparing(OriginalCharacter::getId)).orElseThrow().getCardColor());
        assertThrows(IllegalArgumentException.class, () -> backup.restore(new ByteArrayInputStream(ProfileDiagramTests.archive(oldXml.replace("favorite=\"false\"", "favorite=\"false\" cardColor=\"bad-css\"")))));
    }
    static Document svg(String content) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }
    static List<Element> groups(Document doc, String clazz) {
        List<Element> result = new ArrayList<>(); var all = doc.getElementsByTagName("g");
        for (int i = 0; i < all.getLength(); i++) if (((Element) all.item(i)).getAttribute("class").equals(clazz)) result.add((Element) all.item(i));
        return result;
    }
    static double[] box(Element group) {
        Element rect = (Element) group.getElementsByTagName("rect").item(0);
        return new double[]{Double.parseDouble(rect.getAttribute("x")), Double.parseDouble(rect.getAttribute("y")), Double.parseDouble(rect.getAttribute("width")), Double.parseDouble(rect.getAttribute("height"))};
    }
    static void checkGeometry(Document doc, int count) {
        var frames = groups(doc, "world-frame").stream().map(PresentationCustomizationTests::box).toList();
        var nodes = groups(doc, "character-node").stream().map(PresentationCustomizationTests::box).toList();
        assertEquals(4, frames.size()); assertEquals(count, nodes.size());
        double width = Double.parseDouble(doc.getDocumentElement().getAttribute("width")), height = Double.parseDouble(doc.getDocumentElement().getAttribute("height"));
        for (var n : nodes) {
            assertTrue(n[0] >= 0 && n[1] >= 0 && n[0] + n[2] <= width && n[1] + n[3] <= height);
            assertEquals(1, frames.stream().filter(f -> n[0] >= f[0] && n[1] >= f[1] + 90 && n[0] + n[2] <= f[0] + f[2] && n[1] + n[3] <= f[1] + f[3]).count());
        }
        for (int i = 0; i < nodes.size(); i++) for (int j = i + 1; j < nodes.size(); j++) {
            var a = nodes.get(i); var b = nodes.get(j);
            assertFalse(a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3], "cards overlap");
        }
    }
    @Test void diagramGrowsInBothDirectionsAndSpacingPropagatesToDownloadsAndShare() throws Exception {
        int smallWidth = 0, smallHeight = 0;
        for (int count : List.of(0, 1, 2, 5, 33, 101, 500)) {
            List<OriginalCharacter> roster = new ArrayList<>();
            for (int i = 0; i < count; i++) { var c = new OriginalCharacter(); c.setId((long) i + 1); c.setName("Person" + i); roster.add(c); }
            var doc = svg(diagrams.render(roster, List.of())); checkGeometry(doc, count);
            if (count == 1) { smallWidth = Integer.parseInt(doc.getDocumentElement().getAttribute("width")); smallHeight = Integer.parseInt(doc.getDocumentElement().getAttribute("height")); }
            if (count == 33) {
                assertTrue(Integer.parseInt(doc.getDocumentElement().getAttribute("width")) > smallWidth);
                assertTrue(Integer.parseInt(doc.getDocumentElement().getAttribute("height")) > smallHeight);
                var wide = svg(diagrams.render(roster, List.of(), 250)); checkGeometry(wide, count);
                assertTrue(Integer.parseInt(wide.getDocumentElement().getAttribute("width")) > Integer.parseInt(doc.getDocumentElement().getAttribute("width")));
            }
        }
        for (CharacterWorld world : CharacterWorld.values()) { var c = save("World-" + world); c.setWorld(world); characters.save(c); }
        String normal = request("GET", "/relationships/diagram.svg", "").body();
        String wide = request("GET", "/relationships/diagram.svg?spacing=200&download=true", "").body();
        assertNotEquals(normal, wide);
        assertEquals(wide, new String(unzip(share.exportAll(List.of(), 200)).get("04_RELATIONSHIPS.svg"), StandardCharsets.UTF_8));
        assertTrue(request("GET", "/relationships?spacing=200", "").body().contains("spacing=200"));
        assertEquals(400, request("GET", "/relationships/diagram.svg?spacing=999999", "").statusCode());
        assertEquals(400, request("GET", "/relationships?spacing=0", "").statusCode());
    }
}
