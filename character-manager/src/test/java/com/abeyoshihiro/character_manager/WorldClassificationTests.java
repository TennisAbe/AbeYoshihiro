package com.abeyoshihiro.character_manager;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:worlds", "spring.jpa.hibernate.ddl-auto=create-drop"})
class WorldClassificationTests {
    @Value("${local.server.port}") int port;
    @Autowired CharacterRepository characters;
    @MockitoSpyBean AffiliationOrderRepository orders;
    @Autowired AffiliationOrderingService ordering;
    @Autowired RelationRepository relations;
    @Autowired PictureRepository pictures;
    @Autowired BackupService backup;
    @Autowired LegacyProfileMigration migration;
    HttpClient client;
    @BeforeEach void reset() {
        relations.deleteAll(); pictures.deleteAll(); characters.deleteAll(); orders.deleteAll();
        client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
    }
    String encode(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
    HttpResponse<String> get(String path) throws Exception { return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString()); }
    HttpResponse<String> post(String path,String body) throws Exception { return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString()); }
    OriginalCharacter save(String name,CharacterWorld world,String affiliation) { OriginalCharacter c=new OriginalCharacter();c.setName(name);c.setWorld(world);c.setAffiliation(affiliation);return characters.save(c); }
    List<Long> ids(String html) { return java.util.regex.Pattern.compile("class=\"card-link\" href=\"/characters/(\\d+)\"").matcher(html).results().map(m->Long.parseLong(m.group(1))).toList(); }
    @Test void groupsWorldsInFixedOrderAndNumbersAcrossSections() throws Exception {
        var modern=save("Modern",CharacterWorld.MODERN_WORLD,"現代の所属");var other=save("Other",CharacterWorld.OTHER_WORLD,"異世界の所属");
        var palace=save("Palace",CharacterWorld.ALTER_PALACE,"宮殿");var blank=save("Blank",CharacterWorld.ALTER_PALACE,"");
        String html=get("/?sort=newest").body();assertEquals(List.of(palace.getId(),blank.getId(),other.getId(),modern.getId()),ids(html));
        int a=html.indexOf("オルタパレスの人物"),b=html.indexOf("異世界の人物"),c=html.indexOf("現代世界の人物");assertTrue(a>=0&&b>a&&c>b);
        var numbers=java.util.regex.Pattern.compile(">NO\\. (\\d+)</span>").matcher(html).results().map(m->m.group(1)).toList();assertEquals(List.of("001","002","003","004"),numbers);
        assertFalse(html.contains(">宮殿</h"));assertFalse(html.contains(">現代の所属</h"));
    }
    @Test void usesIndependentAffiliationOrdersAndSortsOnlyWithinAffiliation() throws Exception {
        var a=save("Zed",CharacterWorld.ALTER_PALACE,"騎士団");var b=save("Beta",CharacterWorld.ALTER_PALACE,"教会");var aa=save("alice",CharacterWorld.ALTER_PALACE,"騎士団");
        var otherA=save("Other knights",CharacterWorld.OTHER_WORLD,"騎士団");var otherB=save("Other church",CharacterWorld.OTHER_WORLD,"教会");
        ordering.save(CharacterWorld.ALTER_PALACE,List.of("騎士団","教会"),List.of(2,1));ordering.save(CharacterWorld.OTHER_WORLD,List.of("騎士団","教会"),List.of(1,2));
        assertEquals(List.of(b.getId(),aa.getId(),a.getId(),otherA.getId(),otherB.getId()),ids(get("/?sort=name").body()));
        a.setFavorite(true);a.replaceTags(TagParser.parse("主人公"));characters.save(a);
        assertEquals(List.of(a.getId()),ids(get("/?q=Zed&tag="+encode("主人公")+"&favorites=true&sort=newest").body()));
        String filtered=get("/?q=missing").body();assertTrue(ids(filtered).isEmpty());assertTrue(filtered.contains("現代世界の人物"));
    }
    @Test void orderSettingsSaveAndRejectInvalidOrStaleRequests() throws Exception {
        save("A",CharacterWorld.ALTER_PALACE,"A");save("B",CharacterWorld.ALTER_PALACE,"B");save("Other",CharacterWorld.OTHER_WORLD,"X");
        assertEquals(200,get("/affiliations/order").statusCode());assertEquals(200,get("/affiliation-order.js").statusCode());
        String url="/affiliations/order/ALTER_PALACE";
        assertEquals(302,post(url,"affiliations=A&affiliations=B&positions=2&positions=1").statusCode());assertEquals(List.of("B","A"),ordering.current(CharacterWorld.ALTER_PALACE));
        assertTrue(get("/affiliations/order").body().contains("保存しました。"));
        for(String body:List.of("affiliations=A&affiliations=B&positions=1&positions=1","affiliations=A&affiliations=B&positions=0&positions=2","affiliations=A&affiliations=Spoof&positions=1&positions=2","affiliations=A&positions=1")){
            assertEquals(302,post(url,body).statusCode());assertEquals(List.of("B","A"),ordering.current(CharacterWorld.ALTER_PALACE));assertTrue(get("/affiliations/order").body().contains("role=\"alert\""));
        }
        save("C",CharacterWorld.ALTER_PALACE,"C");post(url,"affiliations=A&affiliations=B&positions=1&positions=2");assertEquals(List.of("B","A","C"),ordering.current(CharacterWorld.ALTER_PALACE));assertEquals(List.of("X"),ordering.current(CharacterWorld.OTHER_WORLD));
        assertEquals(400,post("/affiliations/order/UNKNOWN","affiliations=A&positions=1").statusCode());
    }
    @Test void appendsNewAffiliationsAndReflectsEditsAndRemovedAffiliations() throws Exception {
        var a=save("A",CharacterWorld.ALTER_PALACE,"A");var b=save("B",CharacterWorld.ALTER_PALACE,"B");var blank=save("No affiliation",CharacterWorld.ALTER_PALACE,"  ");
        ordering.save(CharacterWorld.ALTER_PALACE,List.of("B","A"),List.of(1,2));var c=save("C",CharacterWorld.ALTER_PALACE,"C");
        assertEquals(List.of(b.getId(),a.getId(),c.getId(),blank.getId()),ids(get("/").body()));
        assertEquals(302,post("/characters/"+b.getId()+"/update","name=B&world=OTHER_WORLD&affiliation=B").statusCode());
        assertEquals(List.of("A","C"),ordering.current(CharacterWorld.ALTER_PALACE));assertEquals(List.of("B"),ordering.current(CharacterWorld.OTHER_WORLD));
        assertEquals(List.of(a.getId(),c.getId(),blank.getId(),b.getId()),ids(get("/").body()));
        assertFalse(get("/affiliations/order").body().contains("value=\"  \""));
    }
    @Test void validatesWorldAndMigratesLegacyCharactersWithoutChangingAffiliations() throws Exception {
        var legacy=save("Legacy",null," 宮殿 ");migration.run(null);assertEquals(CharacterWorld.ALTER_PALACE,characters.findById(legacy.getId()).orElseThrow().getWorld());assertEquals(" 宮殿 ",characters.findById(legacy.getId()).orElseThrow().getAffiliation());
        assertEquals(302,post("/characters","name=Modern&world=MODERN_WORLD&affiliation="+encode("現代組")).statusCode());
        var modern=characters.findAll().stream().filter(c->c.getName().equals("Modern")).findFirst().orElseThrow();assertEquals(CharacterWorld.MODERN_WORLD,modern.getWorld());
        assertEquals(200,post("/characters/"+modern.getId()+"/update","name=Bad&world=INVALID&affiliation=bad").statusCode());assertEquals("Modern",characters.findById(modern.getId()).orElseThrow().getName());assertEquals(CharacterWorld.MODERN_WORLD,characters.findById(modern.getId()).orElseThrow().getWorld());
        assertEquals(200,post("/characters","name=Bad&world=INVALID").statusCode());assertEquals(2,characters.count());
        assertTrue(get("/characters/"+modern.getId()+"/edit").body().contains("世界"));
    }
    byte[] zip(String xml) throws IOException { ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){zip.putNextEntry(new ZipEntry("manifest.xml"));zip.write(xml.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}return out.toByteArray(); }
    String manifest(byte[] bytes) throws IOException { try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry e;while((e=zip.getNextEntry())!=null)if(e.getName().equals("manifest.xml"))return new String(zip.readAllBytes(),StandardCharsets.UTF_8);}throw new IOException("missing manifest"); }
    void seedBackup() { save("A",CharacterWorld.ALTER_PALACE,"A");save("B",CharacterWorld.ALTER_PALACE,"B");save("Other",CharacterWorld.OTHER_WORLD,"X");save("Modern",CharacterWorld.MODERN_WORLD,"Y");ordering.save(CharacterWorld.ALTER_PALACE,List.of("B","A"),List.of(1,2)); }
    @Test void restoresWorldsAndAffiliationOrdersAndPreservesExistingPreferences() throws Exception {
        seedBackup();byte[] archive=backup.exportArchive();assertTrue(manifest(archive).contains("version=\"7\""));characters.deleteAll();orders.deleteAll();
        assertEquals(4,backup.restore(new ByteArrayInputStream(archive)));assertEquals(List.of("B","A"),ordering.current(CharacterWorld.ALTER_PALACE));assertEquals(List.of("X"),ordering.current(CharacterWorld.OTHER_WORLD));assertEquals(List.of("Y"),ordering.current(CharacterWorld.MODERN_WORLD));
        var found=characters.findAll();assertEquals(CharacterWorld.MODERN_WORLD,found.stream().filter(c->c.getName().equals("Modern")).findFirst().orElseThrow().getWorld());
        ordering.save(CharacterWorld.ALTER_PALACE,List.of("A","B"),List.of(1,2));assertEquals(4,backup.restore(new ByteArrayInputStream(archive)));assertEquals(8,characters.count());assertEquals(List.of("A","B"),ordering.current(CharacterWorld.ALTER_PALACE));
    }
    @Test void restoresVersionTwoCharactersIntoDefaultWorldWithoutLosingProfiles() throws Exception {
        var c=save("Old v2",CharacterWorld.MODERN_WORLD,"旧所属");c.setSpeechStyle("丁寧");c.setBasicSettings("基本設定");characters.save(c);
        String xml=manifest(backup.exportArchive()).replace("version=\"7\"","version=\"2\"").replaceAll(" world=\"[A-Z_]+\"","").replaceAll("<affiliationOrders>.*?</affiliationOrders>","");
        assertEquals(1,backup.restore(new ByteArrayInputStream(zip(xml))));
        var restored=characters.findAll().stream().filter(x->!x.getId().equals(c.getId())).findFirst().orElseThrow();assertEquals(CharacterWorld.ALTER_PALACE,restored.getWorld());assertEquals("旧所属",restored.getAffiliation());assertEquals("丁寧",restored.getSpeechStyle());
    }
    @Test void rejectsInvalidBackupWorldsAndOrdersBeforeChangingData() throws Exception {
        seedBackup();String xml=manifest(backup.exportArchive());
        List<String> invalid=List.of(xml.replace("world=\"ALTER_PALACE\"","world=\"INVALID\""),xml.replaceFirst(" world=\"ALTER_PALACE\"",""),xml.replaceAll("<affiliationOrders>.*?</affiliationOrders>",""),xml.replace("position=\"1\"","position=\"0\""),xml.replace("affiliation=\"B\"","affiliation=\"Unknown\""));
        for(String value:invalid){assertThrows(IllegalArgumentException.class,()->backup.restore(new ByteArrayInputStream(zip(value))));assertEquals(4,characters.count());assertEquals(List.of("B","A"),ordering.current(CharacterWorld.ALTER_PALACE));}
    }
    @Test void rollsBackRestoredCharactersAndOrderChangesOnSettingsFailure() throws Exception {
        seedBackup();byte[] archive=backup.exportArchive();long before=orders.count();
        org.mockito.Mockito.doThrow(new IllegalStateException("simulated settings failure")).when(orders).save(org.mockito.ArgumentMatchers.any(AffiliationOrder.class));
        assertThrows(IllegalStateException.class,()->backup.restore(new ByteArrayInputStream(archive)));assertEquals(4,characters.count());assertEquals(before,orders.count());assertEquals(List.of("B","A"),ordering.current(CharacterWorld.ALTER_PALACE));
    }
}
