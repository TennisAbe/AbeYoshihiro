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
        for(String field:List.of("heightCm")) for(String invalid:List.of("-1","0","NaN","10001","1.234","1e2","<script>")) {
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
        assertFalse(svg.contains(String.format("C%06d",a.getId())));assertFalse(svg.contains(String.format("C%06d",b.getId())));
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
        String xml=new String(unpack(bytes).get("manifest.xml"),StandardCharsets.UTF_8).replace("version=\"7\"","version=\"4\"")
            .replaceAll("<(gender|heightCm|bustCm)(?:/>|>.*?</\\1>)", "").replaceAll("<field name=\"(gender|heightCm|bustCm)\"[^>]*>.*?</field>","");
        assertEquals(1,backup.restore(new ByteArrayInputStream(archive(xml))));
        var old=characters.findAll().stream().max(Comparator.comparing(OriginalCharacter::getId)).orElseThrow();assertNull(old.getGender());assertNull(old.getHeightCm());
        var entry=history.list(old.getId()).get(0);assertTrue(history.restore(old.getId(),entry.getId(),ProfileSnapshot.fingerprint(old)));
        String shared=new String(unpack(share.exportAll()).get("01_ALL_CHARACTERS.md"),StandardCharsets.UTF_8);assertTrue(shared.contains("身長（cm）"));assertTrue(shared.contains("胸囲"));assertTrue(shared.contains("85"));
    }
    @Test void protagonistHasFourFieldsAndIsFirstInRosterAndBackups() throws Exception {
        var ordinary=save("Ordinary");
        String form=request("GET","/protagonists/new", "").body();
        for(String field:List.of("name","gender","heightCm","basicSettings")) assertTrue(form.contains("name=\""+field+"\""),field);
        for(String field:List.of("bustCm","affiliation","tagsInput","personality","speechStyle","firstPerson","feelingsTowardMikado","likes","dislikes","abilities","image","title")) assertFalse(form.contains("name=\""+field+"\""),field);
        assertEquals(302,request("POST","/characters","world=PROTAGONIST&name=Hero&gender="+enc("女")+"&heightCm=170&basicSettings=Story&bustCm=Invalid&personality=Ignored&tagsInput=Ignored").statusCode());
        var hero=characters.findAll().stream().filter(OriginalCharacter::isProtagonist).findFirst().orElseThrow();
        assertEquals("Hero",hero.getName());assertEquals("170",hero.getHeightCm());assertEquals("Story",hero.getBasicSettings());assertNull(hero.getBustCm());assertNull(hero.getPersonality());assertTrue(hero.getTags().isEmpty());
        String index=request("GET","/", "").body();assertTrue(index.indexOf("data-world=\"PROTAGONIST\"")<index.indexOf("data-world=\"ALTER_PALACE\""));
        assertEquals(200,request("GET","/characters/"+hero.getId()+"/edit", "").statusCode());
        CharacterRelation r=new CharacterRelation();r.setSource(hero);r.setTarget(ordinary);r.setRelationType("仲間");relations.save(r);
        assertTrue(diagrams.exportSvg().contains("Hero"));
        assertEquals(302,request("POST","/characters/"+hero.getId()+"/update","world=PROTAGONIST&name=HeroNew&gender="+enc("男")+"&heightCm=171&basicSettings=New").statusCode());
        var revision=history.list(hero.getId()).get(0);history.restore(hero.getId(),revision.getId(),ProfileSnapshot.fingerprint(fresh(hero.getId())));
        assertEquals("Hero",fresh(hero.getId()).getName());assertTrue(fresh(hero.getId()).isProtagonist());
        var zip=unpack(share.exportAll());String all=new String(zip.get("01_ALL_CHARACTERS.md"),StandardCharsets.UTF_8);assertTrue(all.contains("Hero"));assertTrue(all.contains("Story"));
        assertTrue(new String(zip.get("04_RELATIONSHIPS.svg"),StandardCharsets.UTF_8).contains("Hero"));
        byte[] archive=backup.exportArchive();assertEquals(2,backup.restore(new ByteArrayInputStream(archive)));
        assertEquals(2,characters.findAll().stream().filter(OriginalCharacter::isProtagonist).count());assertEquals(2,relations.count());
    }
    CharacterRelation relate(OriginalCharacter a, OriginalCharacter b, String type) {
        var r=new CharacterRelation();r.setSource(a);r.setTarget(b);r.setRelationType(type);return relations.save(r);
    }
    @Test void selectedCharactersIncludeOnlyIncidentRelationsAndOneHopNeighbours() throws Exception {
        var hero=save("CentralHero");hero.setWorld(CharacterWorld.PROTAGONIST);characters.save(hero);
        var a=save("SelectedA");var b=save("NeighbourB");var c=save("UnrelatedC");var d=save("SelectedD");var e=save("NeighbourE");
        relate(hero,a,"H-A");relate(a,b,"A-B");relate(b,c,"B-C");relate(d,e,"D-E");
        String query="ids="+a.getId()+"&ids="+d.getId();
        String svg=request("GET","/relationships/diagram.svg?"+query, "").body();
        for(String name:List.of("CentralHero","SelectedA","NeighbourB","SelectedD","NeighbourE")) assertTrue(svg.contains(name),name);
        assertFalse(svg.contains("UnrelatedC"));assertFalse(svg.contains("B-C"));assertTrue(svg.contains("A-B"));assertTrue(svg.contains("D-E"));
        String page=request("GET","/relationships?"+query, "").body();assertTrue(page.contains("5人 / 3件の関係"));
        var filtered=unpack(share.exportAll(List.of(a.getId(),d.getId())));
        assertEquals(svg,new String(filtered.get("04_RELATIONSHIPS.svg"),StandardCharsets.UTF_8));
        assertTrue(new String(filtered.get("01_ALL_CHARACTERS.md"),StandardCharsets.UTF_8).contains("UnrelatedC"));
        assertEquals(400,request("GET","/relationships?ids=999999", "").statusCode());
        assertEquals(400,request("GET","/relationships/diagram.svg?ids=999999", "").statusCode());
        assertEquals(400,request("GET","/relationships?ids=oops", "").statusCode());
        String all=request("GET","/relationships/diagram.svg", "").body();assertTrue(all.contains("UnrelatedC"));assertTrue(all.contains("B-C"));
        relations.deleteAll();String isolated=request("GET","/relationships/diagram.svg?ids="+a.getId(), "").body();assertTrue(isolated.contains("SelectedA"));assertTrue(isolated.contains("CentralHero"));assertFalse(isolated.contains("NeighbourB"));
    }
    @Test void worldFramesAreCompactDarkAndDoNotDisplayInternalReferences() throws Exception {
        for(CharacterWorld world:CharacterWorld.values()) {
            var c=save("Name-"+world.name());c.setWorld(world);characters.save(c);
        }
        String svg=diagrams.exportSvg();
        for(String label:List.of("主人公","オルタパレス","異世界","現代世界")) assertTrue(svg.contains(label));
        assertTrue(svg.contains("url(#night)"));assertTrue(svg.contains("THE PROTAGONIST"));
        assertFalse(java.util.regex.Pattern.compile("C[0-9]{6}").matcher(svg).find());
        assertFalse(svg.contains("全キャラクター（関係のない人物も含む）"));
        var factory=DocumentBuilderFactory.newInstance();var doc=factory.newDocumentBuilder().parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
        assertTrue(Integer.parseInt(doc.getDocumentElement().getAttribute("width")) > 1600);
        var nodes=doc.getElementsByTagName("text");
        for(int i=0;i<nodes.getLength();i++) assertFalse(nodes.item(i).getTextContent().matches("C[0-9]+.*"));
    }

}
