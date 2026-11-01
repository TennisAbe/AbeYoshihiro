package com.abeyoshihiro.character_manager;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:revisions", "spring.jpa.hibernate.ddl-auto=create-drop"})
class RevisionFeatureTests {
    @Value("${local.server.port}") int port;
    @Autowired CharacterRepository characters;
    @Autowired RevisionService history;
    @MockitoSpyBean RevisionRepository revisions;
    @Autowired RelationRepository relations;
    @Autowired PictureRepository pictures;
    @Autowired AffiliationOrderRepository orders;
    @Autowired BackupService backup;
    @Autowired GalleryService gallery;
    HttpClient client;
    @BeforeEach void reset() {
        relations.deleteAll(); pictures.deleteAll(); characters.deleteAll(); orders.deleteAll();
        assertEquals(0,revisions.count());
        client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
    }
    String encode(String text) { return URLEncoder.encode(text,StandardCharsets.UTF_8); }
    HttpResponse<String> get(String path) throws Exception { return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString()); }
    HttpResponse<String> post(String path,String body) throws Exception { return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString()); }
    OriginalCharacter input(String name) {
        OriginalCharacter c=new OriginalCharacter();c.setName(name);c.setWorld(CharacterWorld.OTHER_WORLD);c.setAffiliation("騎士団");
        c.setBasicSettings("星を守る使命");c.setPersonality("穏やか");c.setSpeechStyle("丁寧");c.setFirstPerson("私");c.setFeelingsTowardMikado("信頼");c.setLikes("紅茶");c.setDislikes("嘘");c.setAbilities("星の魔法");c.setTagsInput("主人公,魔法");return c;
    }
    OriginalCharacter save(String name) { OriginalCharacter c=new OriginalCharacter();ProfileFields.copy(input(name),c);return characters.save(c); }
    String body(OriginalCharacter input) {
        List<String> fields=new ArrayList<>();ProfileFields.values(input).forEach((key,value)->fields.add(key+"="+encode(value==null?"":value)));
        fields.add("world="+input.getWorld().name());fields.add("tagsInput="+encode(input.getTagsInput()));return String.join("&",fields);
    }
    String path(Long id,Long revision) { return "/characters/"+id+"/history/"+revision; }
    String fingerprint(Long id) { return ProfileSnapshot.fingerprint(characters.findById(id).orElseThrow()); }
    @Test void displaysAffiliationAndReadOnlyDetailSeparateFromEditor() throws Exception {
        var c=save("<シズ>");c.setAffiliation("<宮殿>");c.setBasicSettings("<script>alert(1)</script>\n大切な設定");characters.save(c);
        var list=get("/");assertTrue(list.body().contains("class=\"card-affiliation\""));assertTrue(list.body().contains("&lt;宮殿&gt;"));assertTrue(list.body().contains("href=\"/characters/"+c.getId()+"\""));
        var detail=get("/characters/"+c.getId());assertEquals(200,detail.statusCode());
        assertTrue(detail.body().contains("&lt;script&gt;alert(1)&lt;/script&gt;"));assertFalse(detail.body().contains("<script>alert(1)</script>"));assertFalse(detail.body().contains("<textarea"));assertFalse(detail.body().contains("<form"));
        assertTrue(detail.body().contains("/characters/"+c.getId()+"/edit"));assertTrue(detail.body().contains("dossier-copy"));assertTrue(detail.body().contains("世界"));assertFalse(detail.body().contains("特大分類"));
        var edit=get("/characters/"+c.getId()+"/edit");assertEquals(200,edit.statusCode());assertTrue(edit.body().contains("for=\"world\">世界</label>"));assertTrue(edit.body().contains("<textarea"));assertEquals(404,get("/characters/999999").statusCode());
    }
    @Test void recordsChangesOnlyAndKeepsExactlyThreeMostRecentStates() throws Exception {
        var c=save("Version0");
        for(int i=1;i<=4;i++)assertEquals(302,post("/characters/"+c.getId()+"/update",body(input("Version"+i))).statusCode());
        var entries=history.list(c.getId());assertEquals(3,entries.size());assertEquals(List.of("Version3","Version2","Version1"),entries.stream().map(e->e.getFields().get("name")).toList());
        assertTrue(entries.stream().allMatch(e->e.getSavedAt()!=null&&e.getTimestamp().matches("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}")));
        assertEquals(302,post("/characters/"+c.getId()+"/update",body(input("Version4"))).statusCode());assertEquals(3,history.list(c.getId()).size());assertEquals(entries.get(0).getId(),history.list(c.getId()).get(0).getId());
        assertEquals(200,post("/characters/"+c.getId()+"/update",body(input("Bad"))+"&firstPerson="+encode("あ".repeat(121))).statusCode());
        assertEquals("Version4",characters.findById(c.getId()).orElseThrow().getName());assertEquals(3,history.list(c.getId()).size());
    }
    @Test void showsCurrentVersusHistoricalDifferencesAndRequiresConfirmation() throws Exception {
        var c=save("Before");var changed=input("After");changed.setWorld(CharacterWorld.MODERN_WORLD);changed.setAffiliation("現代組");changed.setLikes("<危険ではない文字>");changed.setTagsInput("新タグ");history.update(c.getId(),changed);
        var revision=history.list(c.getId()).get(0);String url=path(c.getId(),revision.getId());
        var preview=get(url);assertEquals(200,preview.statusCode());for(String value:List.of("Before","After","現在の内容","履歴の内容（復元後）","異世界","現代世界","所属","タグ","JST"))assertTrue(preview.body().contains(value),value);
        assertTrue(preview.body().contains("&lt;危険ではない文字&gt;"));
        assertEquals(302,post(url+"/restore","fingerprint="+fingerprint(c.getId())).statusCode());assertEquals("After",characters.findById(c.getId()).orElseThrow().getName());
        assertEquals(302,post(url+"/restore","confirmed=true").statusCode());assertEquals("After",characters.findById(c.getId()).orElseThrow().getName());
        assertEquals(302,post(url+"/restore","confirmed=true&fingerprint="+fingerprint(c.getId())).statusCode());
        assertEquals("Before",characters.findById(c.getId()).orElseThrow().getName());assertEquals(CharacterWorld.OTHER_WORLD,characters.findById(c.getId()).orElseThrow().getWorld());
        assertEquals(Set.of("主人公","魔法"),characters.findById(c.getId()).orElseThrow().getTags());assertEquals("RESTORE",history.list(c.getId()).get(0).getReason());assertEquals("After",history.list(c.getId()).get(0).getFields().get("name"));
    }
    @Test void staleConfirmationAndOtherCharactersHistoryCannotOverwriteData() throws Exception {
        var c=save("Before");var other=save("Other");history.update(c.getId(),input("After"));var revision=history.list(c.getId()).get(0);String old=fingerprint(c.getId());history.update(c.getId(),input("Newest"));
        String url=path(c.getId(),revision.getId());assertEquals(302,post(url+"/restore","confirmed=true&fingerprint="+old).statusCode());assertEquals("Newest",characters.findById(c.getId()).orElseThrow().getName());assertTrue(get(url).body().contains("確認後にプロフィールが更新"));
        assertEquals(404,get(path(other.getId(),revision.getId())).statusCode());assertEquals(404,post(path(other.getId(),revision.getId())+"/restore","confirmed=true&fingerprint="+fingerprint(other.getId())).statusCode());assertEquals(404,get(path(c.getId(),999999L)).statusCode());
    }
    @Test void restoringOldestRetainedStatePreservesMediaRelationsAndFavorite() throws Exception {
        var c=save("V0");var other=save("Other");c.setFavorite(true);characters.save(c);
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);ByteArrayOutputStream bytes=new ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        gallery.upload(c.getId(),new org.springframework.mock.web.MockMultipartFile("image",bytes.toByteArray()),"portrait");var cover=characters.findById(c.getId()).orElseThrow().getCoverImageId();
        var relation=new CharacterRelation();relation.setSource(c);relation.setTarget(other);relation.setRelationType("友人");relations.save(relation);
        for(int i=1;i<=3;i++)history.update(c.getId(),input("V"+i));var oldest=history.list(c.getId()).get(2);
        assertTrue(history.restore(c.getId(),oldest.getId(),fingerprint(c.getId())));var restored=characters.findById(c.getId()).orElseThrow();
        assertEquals("V0",restored.getName());assertEquals(c.getId(),restored.getId());assertTrue(restored.isFavorite());assertEquals(cover,restored.getCoverImageId());assertEquals(1,pictures.count());assertEquals(1,relations.count());assertEquals(3,history.list(c.getId()).size());
        assertArrayEquals(bytes.toByteArray(),java.nio.file.Files.readAllBytes(gallery.coverPath(c.getId())));
    }
    @Test void concurrentUpdatesRetainEachIntermediateState() throws Exception {
        var c=save("Initial");var pool=Executors.newFixedThreadPool(2);
        try {var a=pool.submit(()->history.update(c.getId(),input("A")));var b=pool.submit(()->history.update(c.getId(),input("B")));a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);}
        finally {pool.shutdownNow();}
        var versions=new HashSet<>(history.list(c.getId()).stream().map(r->r.getFields().get("name")).toList());versions.add(characters.findById(c.getId()).orElseThrow().getName());assertEquals(Set.of("Initial","A","B"),versions);assertEquals(2,history.list(c.getId()).size());
    }
    @Test void historyPruningFailureRollsBackBothProfileAndHistory() {
        var c=save("V0");for(int i=1;i<=3;i++)history.update(c.getId(),input("V"+i));
        var before=history.list(c.getId()).stream().map(CharacterRevision::getId).toList();
        org.mockito.Mockito.doThrow(new IllegalStateException("simulated prune failure")).when(revisions).deleteAll(org.mockito.ArgumentMatchers.anyList());
        assertThrows(IllegalStateException.class,()->history.update(c.getId(),input("V4")));assertEquals("V3",characters.findById(c.getId()).orElseThrow().getName());assertEquals(before,history.list(c.getId()).stream().map(CharacterRevision::getId).toList());
    }
    String manifest(byte[] archive) throws IOException {try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive))){ZipEntry e;while((e=zip.getNextEntry())!=null)if(e.getName().equals("manifest.xml"))return new String(zip.readAllBytes(),StandardCharsets.UTF_8);}throw new IOException("missing manifest");}
    byte[] zip(String xml) throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){zip.putNextEntry(new ZipEntry("manifest.xml"));zip.write(xml.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}return out.toByteArray();}
    @Test void backupRestoresHistoryWithTimestampsAndNewOwnerIds() throws Exception {
        var c=save("V0");history.update(c.getId(),input("V1"));history.update(c.getId(),input("V2"));
        var before=history.list(c.getId());byte[] archive=backup.exportArchive();assertTrue(manifest(archive).contains("version=\"4\""));assertEquals(1,backup.restore(new ByteArrayInputStream(archive)));
        var imported=characters.findAll().stream().filter(x->!x.getId().equals(c.getId())).findFirst().orElseThrow();var restored=history.list(imported.getId());assertEquals(2,restored.size());
        for(int i=0;i<2;i++){assertEquals(before.get(i).getFields(),restored.get(i).getFields());assertEquals(before.get(i).getSavedAt(),restored.get(i).getSavedAt());}
        assertTrue(history.restore(imported.getId(),restored.get(1).getId(),fingerprint(imported.getId())));assertEquals("V0",characters.findById(imported.getId()).orElseThrow().getName());assertEquals("V2",characters.findById(c.getId()).orElseThrow().getName());
    }
    @Test void oldArchivesRestoreWithoutInventingHistoryAndInvalidHistoryIsRejected() throws Exception {
        var c=save("Before");history.update(c.getId(),input("After"));String xml=manifest(backup.exportArchive());
        String old=xml.replace("version=\"4\"","version=\"3\"").replaceAll("<history>.*?</history>","");assertEquals(1,backup.restore(new ByteArrayInputStream(zip(old))));
        var imported=characters.findAll().stream().filter(x->!x.getId().equals(c.getId())).findFirst().orElseThrow();assertTrue(history.list(imported.getId()).isEmpty());
        String row=java.util.regex.Pattern.compile("<revision .*?</revision>").matcher(xml).results().findFirst().orElseThrow().group();
        List<String> invalid=List.of(xml.replaceAll("<history>.*?</history>",""),xml.replace("</history>",row.repeat(3)+"</history>"),xml.replaceFirst("at=\"[^\"]+\"","at=\"bad-time\""),xml.replace("reason=\"UPDATE\"","reason=\"INVALID\""),xml.replace("name=\"likes\"","name=\"unknown\""),xml.replace("<field name=\"world\">OTHER_WORLD</field>","<field name=\"world\">INVALID</field>"));
        for(String value:invalid){assertNotEquals(xml,value);assertThrows(IllegalArgumentException.class,()->backup.restore(new ByteArrayInputStream(zip(value))));assertEquals(2,characters.count());assertEquals(1,revisions.count());}
    }
    @Test void deletingCharacterAlsoRemovesRevisions() throws Exception {
        var c=save("Before");history.update(c.getId(),input("After"));assertEquals(1,revisions.count());assertEquals(302,post("/characters/"+c.getId()+"/delete","").statusCode());assertEquals(0,revisions.count());assertEquals(404,get("/characters/"+c.getId()).statusCode());
    }
}
