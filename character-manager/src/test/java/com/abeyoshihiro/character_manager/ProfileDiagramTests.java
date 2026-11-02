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

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:profile-diagram", "spring.jpa.hibernate.ddl-auto=create-drop"})
class ProfileDiagramTests {
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
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type", "application/x-www-form-urlencoded")
                .method(method,HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    OriginalCharacter save(String name) { OriginalCharacter c = new OriginalCharacter(); c.setName(name); c.setBasicSettings(""); return characters.save(c); }
    OriginalCharacter fresh(Long id) { return characters.findById(id).orElseThrow(); }
    static Map<String, byte[]> unpack(byte[] bytes) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) { ZipEntry e; while((e=zip.getNextEntry())!=null) files.put(e.getName(),zip.readAllBytes()); }
        return files;
    }
    static byte[] archive(String xml) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try(ZipOutputStream zip = new ZipOutputStream(out)) { zip.putNextEntry(new ZipEntry("manifest.xml")); zip.write(xml.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
        return out.toByteArray();
    }
    @Test void profileCreateEditValidationAndHistory() throws Exception {
        assertEquals(302,request("POST","/characters","name=Test&gender="+enc("女")+"&heightCm=165.5&bustCm=90").statusCode());
        OriginalCharacter c=characters.findAll().get(0); Long id=c.getId();
        assertEquals("女",c.getGender());assertEquals("165.5",c.getHeightCm());assertEquals("90",c.getBustCm());
        String detail=request("GET","/characters/"+id,"").body(); assertTrue(detail.contains("身長（cm）")); assertTrue(detail.contains("90"));
        assertEquals(302,request("POST","/characters/"+id+"/update","name=Test&gender="+enc("男")+"&heightCm=180&bustCm=99").statusCode());
        assertNull(fresh(id).getBustCm());assertEquals("180",fresh(id).getHeightCm());
        var revision=history.list(id).get(0);assertEquals("90",revision.getFields().get("bustCm"));
        history.restore(id,revision.getId(),ProfileSnapshot.fingerprint(fresh(id)));assertEquals("90",fresh(id).getBustCm());
        for(String field:List.of("heightCm","bustCm")) for(String invalid:List.of("-1","0","NaN","10001","1.234","1e2","<script>")) {
            assertEquals(200,request("POST","/characters/"+id+"/update","name=Invalid&gender="+enc("女")+"&"+field+"="+enc(invalid)).statusCode());
            assertEquals("Test",fresh(id).getName());
        }
        assertEquals(200,request("POST","/characters","name=Invalid&gender=invalid").statusCode());
        for(String gender:List.of("男","女","その他")) assertEquals(302,request("POST","/characters","name=Boundary&gender="+enc(gender)+"&heightCm=10000&bustCm=0.01").statusCode());
        assertEquals(200,request("GET","/characters/"+id+"/edit","").statusCode());
    }
    @Test void favoriteIsIdempotentJsonAndSearchChangesOnNextRequest() throws Exception {
        var c=save("Star");String path="/characters/"+c.getId()+"/favorite";
        for(int i=0;i<2;i++){var response=request("PUT",path,"favorite=true");assertEquals(200,response.statusCode());assertEquals("{\"favorite\":true}",response.body());assertFalse(response.headers().firstValue("location").isPresent());}
        assertTrue(fresh(c.getId()).isFavorite());assertTrue(request("GET","/?favorites=true","").body().contains("Star"));
        assertEquals(200,request("PUT",path,"favorite=false").statusCode());assertFalse(fresh(c.getId()).isFavorite());
        assertFalse(request("GET","/?favorites=true","").body().contains("Star"));
        assertEquals(404,request("PUT","/characters/999999/favorite","favorite=true").statusCode());
    }
    @Test void diagramDirectionsDuplicatesEscapingDownloadsAndZip() throws Exception {
        var a=save("同名<script>&\""); var b=save("同名<script>&\""); save("孤立");
        for(var pair:List.of(List.of(a,b),List.of(b,a),List.of(a,b))) {
            CharacterRelation r=new CharacterRelation();r.setSource(pair.get(0));r.setTarget(pair.get(1));r.setRelationType("友人<&");r.setDescription("説明<script>");relations.save(r);
        }
        String svg=diagrams.exportSvg();assertTrue(svg.contains("孤立"));assertTrue(svg.contains("marker-end"));assertFalse(svg.contains("<script>"));assertTrue(svg.contains("&lt;script&gt;"));
        assertTrue(svg.contains(String.format("C%06d",a.getId())));assertTrue(svg.contains(String.format("C%06d",b.getId())));
        var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);var doc=factory.newDocumentBuilder().parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));assertEquals("svg",doc.getDocumentElement().getLocalName());
        var response=request("GET","/relationships/diagram.svg?download=true","");assertEquals(200,response.statusCode());assertTrue(response.headers().firstValue("content-disposition").orElseThrow().contains("attachment"));
        assertTrue(response.headers().firstValue("content-type").orElseThrow().contains("image/svg+xml"));assertEquals(200,request("GET","/relationships","").statusCode());
        var zip=unpack(share.exportAll());assertEquals(svg,new String(zip.get("04_RELATIONSHIPS.svg"),StandardCharsets.UTF_8));
        relations.deleteAll();assertTrue(diagrams.exportSvg().contains("関係はまだ登録されていません"));characters.deleteAll();assertEquals(200,request("GET","/relationships","").statusCode());
    }
    @Test void newBackupAndLegacyHistoryRemainRestorable() throws Exception {
        var c=save("Profile");c.setGender("女");c.setHeightCm("160");c.setBustCm("85");characters.save(c);
        OriginalCharacter next=new OriginalCharacter();ProfileSnapshot.apply(ProfileSnapshot.of(c),next);next.setName("ProfileNew");next.setHeightCm("161");history.update(c.getId(),next);
        byte[] bytes=backup.exportArchive();assertEquals(1,backup.restore(new ByteArrayInputStream(bytes)));
        var imported=characters.findAll().stream().max(Comparator.comparing(OriginalCharacter::getId)).orElseThrow();assertEquals("女",imported.getGender());assertEquals("161",imported.getHeightCm());assertEquals("85",imported.getBustCm());assertEquals("160",history.list(imported.getId()).get(0).getFields().get("heightCm"));
        String xml=new String(unpack(bytes).get("manifest.xml"),StandardCharsets.UTF_8).replace("version=\"5\"","version=\"4\"")
            .replaceAll("<(gender|heightCm|bustCm)(?:/>|>.*?</\\1>)", "").replaceAll("<field name=\"(gender|heightCm|bustCm)\"[^>]*>.*?</field>","");
        assertEquals(1,backup.restore(new ByteArrayInputStream(archive(xml))));
        var old=characters.findAll().stream().max(Comparator.comparing(OriginalCharacter::getId)).orElseThrow();assertNull(old.getGender());assertNull(old.getHeightCm());
        var entry=history.list(old.getId()).get(0);assertTrue(history.restore(old.getId(),entry.getId(),ProfileSnapshot.fingerprint(old)));
        String shared=new String(unpack(share.exportAll()).get("01_ALL_CHARACTERS.md"),StandardCharsets.UTF_8);assertTrue(shared.contains("身長（cm）"));assertTrue(shared.contains("胸囲（cm）"));assertTrue(shared.contains("85"));
    }
}
