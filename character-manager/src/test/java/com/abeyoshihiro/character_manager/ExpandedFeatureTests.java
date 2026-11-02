package com.abeyoshihiro.character_manager;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:expanded", "spring.jpa.hibernate.ddl-auto=create-drop"})
class ExpandedFeatureTests {
    static final Path UPLOADS = tempDirectory();
    static Path tempDirectory() { try { return Files.createTempDirectory("noctalith-test-"); } catch (IOException e) { throw new UncheckedIOException(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("noctalith.upload-directory", UPLOADS::toString); }
    @Value("${local.server.port}") int port;
    @Autowired CharacterRepository characters;
    @MockitoSpyBean PictureRepository pictures;
    @Autowired RelationRepository relations;
    @Autowired GalleryService gallery;
    @Autowired BackupService backup;
    @Autowired ComicShareService share;
    @Autowired LegacyProfileMigration profileMigration;
    HttpClient client;
    @BeforeEach void reset() throws IOException {
        relations.deleteAll(); pictures.deleteAll(); characters.deleteAll();
        try (var files = Files.list(UPLOADS)) { for (Path p : files.toList()) Files.delete(p); }
        client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }
    String url(String path) { return "http://localhost:" + port + path; }
    String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    HttpResponse<byte[]> request(HttpRequest request) throws Exception { return client.send(request, HttpResponse.BodyHandlers.ofByteArray()); }
    HttpResponse<byte[]> get(String path) throws Exception { return request(HttpRequest.newBuilder(URI.create(url(path))).GET().build()); }
    HttpResponse<byte[]> post(String path, String body) throws Exception {
        return request(HttpRequest.newBuilder(URI.create(url(path))).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }
    String text(HttpResponse<byte[]> response) { return new String(response.body(), StandardCharsets.UTF_8); }
    HttpResponse<byte[]> multipart(String path, String field, byte[] bytes, Map<String, String> fields) throws Exception {
        String boundary = "noctalith-test-boundary"; ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (var entry : fields.entrySet()) out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+entry.getKey()+"\"\r\n\r\n"+entry.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+field+"\"; filename=\"test.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        return request(HttpRequest.newBuilder(URI.create(url(path))).header("Content-Type", "multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build());
    }
    byte[] png(int color) throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,color);
        ByteArrayOutputStream out = new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();
    }
    OriginalCharacter save(String name) { OriginalCharacter c = new OriginalCharacter();c.setName(name);return characters.save(c); }
    List<Long> cardIds(String html) { return java.util.regex.Pattern.compile("class=\"card-link\" href=\"/characters/(\\d+)\"").matcher(html).results().map(m -> Long.parseLong(m.group(1))).toList(); }
    @Test void profileFieldsPersistAndValidateWithoutChangingOtherState() throws Exception {
        String body = "name=Alice&affiliation="+encode("騎士団")+"&basicSettings="+encode("基本設定".repeat(300))
                +"&personality="+encode("穏やか")+"&speechStyle="+encode("丁寧な口調")+"&firstPerson="+encode("私")
                +"&feelingsTowardMikado="+encode("大切に思っている")+"&likes="+encode("紅茶")+"&dislikes="+encode("嘘")+"&abilities="+encode("魔法".repeat(200));
        assertEquals(302, post("/characters",body).statusCode()); OriginalCharacter c = characters.findAll().get(0);
        assertEquals("騎士団",c.getAffiliation()); assertEquals(1200,c.getBasicSettings().length());assertEquals("穏やか",c.getPersonality());
        assertEquals("丁寧な口調",c.getSpeechStyle());assertEquals("私",c.getFirstPerson());assertEquals("大切に思っている",c.getFeelingsTowardMikado());assertEquals("紅茶",c.getLikes());assertEquals("嘘",c.getDislikes());assertEquals(400,c.getAbilities().length());
        c.setFavorite(true);characters.save(c);
        assertEquals(302,post("/characters/"+c.getId()+"/update",body.replace("name=Alice","name=Alice2")).statusCode());assertTrue(characters.findById(c.getId()).orElseThrow().isFavorite());
        assertEquals(200,post("/characters/"+c.getId()+"/update","name=Bad&firstPerson="+encode("あ".repeat(121))).statusCode());assertEquals("Alice2",characters.findById(c.getId()).orElseThrow().getName());
        assertEquals(200,post("/characters","name="+"a".repeat(256)).statusCode());assertEquals(1,characters.count());assertTrue(text(get("/characters/"+c.getId()+"/edit")).contains("丁寧な口調"));
    }

    @Test void favoritesSortsAndCombinedFiltersWork() throws Exception {
        OriginalCharacter z=save("Zed"), a=save("alice"), b=save("Beta");a.replaceTags(TagParser.parse("主人公"));characters.save(a);
        assertEquals(List.of(z.getId(),a.getId(),b.getId()),cardIds(text(get("/"))));assertEquals(List.of(b.getId(),a.getId(),z.getId()),cardIds(text(get("/?sort=newest"))));assertEquals(List.of(a.getId(),b.getId(),z.getId()),cardIds(text(get("/?sort=name"))));
        assertEquals(302,post("/characters/"+a.getId()+"/favorite","q=ali&tag="+encode("主人公")+"&sort=name&favorites=true").statusCode());assertTrue(characters.findById(a.getId()).orElseThrow().isFavorite());
        assertEquals(List.of(a.getId(),z.getId(),b.getId()),cardIds(text(get("/?sort=favorite"))));assertEquals(List.of(a.getId()),cardIds(text(get("/?q=ali&tag="+encode("主人公")+"&favorites=true"))));
        post("/characters/"+a.getId()+"/favorite","");assertTrue(cardIds(text(get("/?favorites=true"))).isEmpty());assertEquals(List.of(z.getId(),a.getId(),b.getId()),cardIds(text(get("/?sort=invalid"))));assertEquals(404,post("/characters/999999/favorite","").statusCode());
    }
    @Test void galleryAddsSelectsCoverAndDeletesWithOwnershipChecks() throws Exception {
        OriginalCharacter a=save("Alice"), b=save("Bob");byte[] red=png(0xff0000), blue=png(0x0000ff);
        assertEquals(302,multipart("/characters/"+a.getId()+"/image","image",red,Map.of("title","立ち絵")).statusCode());assertEquals(302,multipart("/characters/"+a.getId()+"/image","image",blue,Map.of("title","衣装違い")).statusCode());
        var images=gallery.list(a.getId());assertEquals(2,images.size());assertArrayEquals(red,get("/characters/"+a.getId()+"/image").body());
        assertEquals(404,get("/characters/"+b.getId()+"/images/"+images.get(0).getId()).statusCode());assertEquals(404,post("/characters/"+b.getId()+"/images/"+images.get(0).getId()+"/cover","").statusCode());
        assertEquals(302,post("/characters/"+a.getId()+"/images/"+images.get(1).getId()+"/cover","").statusCode());assertArrayEquals(blue,get("/characters/"+a.getId()+"/image").body());assertTrue(text(get("/characters/"+a.getId()+"/edit")).contains("衣装違い"));
        Path second=gallery.path(images.get(1).getStorageName());assertEquals(302,post("/characters/"+a.getId()+"/images/"+images.get(1).getId()+"/delete","").statusCode());assertFalse(Files.exists(second));assertArrayEquals(red,get("/characters/"+a.getId()+"/image").body());
        post("/characters/"+a.getId()+"/images/"+images.get(0).getId()+"/delete","");assertFalse(characters.findById(a.getId()).orElseThrow().isImageRegistered());assertEquals(404,get("/characters/"+a.getId()+"/image").statusCode());
    }
    @Test void invalidImagesAndLimitsLeaveGalleryUnchanged() throws Exception {
        OriginalCharacter c=save("Alice");
        for(byte[] bytes : List.of(new byte[0],"invalid".getBytes(StandardCharsets.UTF_8),Arrays.copyOf(png(0),33))){assertEquals(302,multipart("/characters/"+c.getId()+"/image","image",bytes,Map.of()).statusCode());assertTrue(gallery.list(c.getId()).isEmpty());}
        assertThrows(IllegalArgumentException.class,()->gallery.upload(c.getId(),new MockMultipartFile("image",new byte[5*1024*1024+1]),""));
        byte[] large=png(0);java.nio.ByteBuffer.wrap(large,16,8).putInt(4000).putInt(4000);CRC32 crc=new CRC32();crc.update(large,12,17);java.nio.ByteBuffer.wrap(large,29,4).putInt((int)crc.getValue());assertThrows(IllegalArgumentException.class,()->gallery.normalize(large));
        for(int i=0;i<20;i++)gallery.upload(c.getId(),new MockMultipartFile("image",png(i)),"image"+i);
        assertThrows(IllegalArgumentException.class,()->gallery.upload(c.getId(),new MockMultipartFile("image",png(0)),"extra"));assertEquals(20,gallery.list(c.getId()).size());
    }
    @Test void relationsAreEditableDirectionalAndRemovedWithCharacter() throws Exception {
        OriginalCharacter a=save("Alice"),b=save("Bob");String base="/characters/"+a.getId()+"/relations";
        assertEquals(302,post(base,"targetId="+b.getId()+"&relationType="+encode("師匠")+"&description="+encode("<大切な人>")).statusCode());CharacterRelation r=relations.findAll().get(0);
        assertTrue(text(get("/characters/"+a.getId()+"/edit")).contains("Bob"));String incoming=text(get("/characters/"+b.getId()+"/edit"));assertTrue(incoming.contains("Alice"));assertTrue(incoming.contains("&lt;大切な人&gt;"));
        assertEquals(404,post("/characters/"+b.getId()+"/relations/"+r.getId()+"/update","relationType=bad").statusCode());assertEquals(302,post(base+"/"+r.getId()+"/update","relationType="+encode("友人")+"&description=changed").statusCode());assertEquals("友人",relations.findById(r.getId()).orElseThrow().getRelationType());
        post(base,"targetId="+a.getId()+"&relationType=self");assertEquals(1,relations.count());post(base,"targetId="+b.getId()+"&relationType="+"a".repeat(61));assertEquals(1,relations.count());assertEquals(404,post(base,"targetId=999999&relationType=friend").statusCode());
        post(base+"/"+r.getId()+"/delete","");assertEquals(0,relations.count());post(base,"targetId="+b.getId()+"&relationType=friend");
        gallery.upload(b.getId(),new MockMultipartFile("image",png(0)),"image");Path image=gallery.path(gallery.list(b.getId()).get(0).getStorageName());assertEquals(302,post("/characters/"+b.getId()+"/delete","").statusCode());assertEquals(0,relations.count());assertEquals(0,pictures.count());assertFalse(Files.exists(image));
    }
    @Test void backupDownloadAndAdditiveRestorePreserveAllFeaturesAndRemapIds() throws Exception {
        OriginalCharacter a=save("Alice"),b=save("Bob");a.setBasicSettings("長い基本設定");a.setSpeechStyle("丁寧");a.setFirstPerson("私");a.setFeelingsTowardMikado("信頼");a.setLikes("紅茶");a.setDislikes("嘘");a.setAffiliation("騎士団");a.setAbilities("魔法");a.setPersonality("穏やか");a.setFavorite(true);a.replaceTags(TagParser.parse("主人公,魔法使い"));characters.save(a);
        gallery.upload(a.getId(),new MockMultipartFile("image",png(0xff0000)),"通常");gallery.upload(a.getId(),new MockMultipartFile("image",png(0x0000ff)),"衣装違い");gallery.cover(a.getId(),gallery.list(a.getId()).get(1).getId());
        post("/characters/"+a.getId()+"/relations","targetId="+b.getId()+"&relationType="+encode("友人")+"&description="+encode("長い付き合い"));
        var download=get("/backup/download");assertEquals(200,download.statusCode());assertTrue(download.headers().firstValue("Content-Disposition").orElseThrow().contains(".zip"));
        assertEquals(302,multipart("/backup/restore","archive",download.body(),Map.of("confirmed","true")).statusCode());assertTrue(text(get("/backup")).contains("2件のキャラクターを追加復元しました。"));assertEquals(4,characters.count());assertEquals(4,pictures.count());assertEquals(2,relations.count());
        var restored=characters.findAll().stream().filter(c->c.getName().equals("Alice")&&!c.getId().equals(a.getId())).findFirst().orElseThrow();assertEquals(ProfileFields.values(characters.findById(a.getId()).orElseThrow()),ProfileFields.values(restored));assertTrue(restored.isFavorite());assertEquals(a.getTags(),restored.getTags());assertEquals(2,gallery.list(restored.getId()).size());assertArrayEquals(get("/characters/"+a.getId()+"/image").body(),get("/characters/"+restored.getId()+"/image").body());
        var relation=relations.findBySourceIdOrderByIdAsc(restored.getId()).get(0);assertNotEquals(b.getId(),relation.getTarget().getId());assertEquals("Bob",relation.getTarget().getName());assertEquals("長い付き合い",relation.getDescription());assertEquals(302,multipart("/backup/restore","archive",download.body(),Map.of()).statusCode());assertEquals(4,characters.count());
    }
    Map<String,byte[]> unzip(byte[] archive) throws IOException {Map<String,byte[]> entries=new LinkedHashMap<>();try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive))){ZipEntry entry;while((entry=zip.getNextEntry())!=null)entries.put(entry.getName(),zip.readAllBytes());}return entries;}
    byte[] zip(Map<String,byte[]> entries) throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){for(var entry:entries.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}}return out.toByteArray();}
    @Test void rejectsCorruptUnsafeAndInconsistentBackupsWithoutChangingExistingData() throws Exception {
        OriginalCharacter c=save("Existing");gallery.upload(c.getId(),new MockMultipartFile("image",png(0)),"portrait");Map<String,byte[]> entries=unzip(backup.exportArchive());String xml=new String(entries.get("manifest.xml"),StandardCharsets.UTF_8);
        List<byte[]> invalid=new ArrayList<>();invalid.add("not a zip".getBytes());invalid.add(zip(Map.of("../escape.png",png(0))));invalid.add(zip(Map.of("manifest.xml",new byte[5*1024*1024+1])));invalid.add(Arrays.copyOf(backup.exportArchive(),20));
        for(String changed:List.of(xml.replace("version=\"7\"","version=\"999\""),xml.replace("favorite=\"false\"","favorite=\"invalid\""),xml.replace("<relations/>","<relations><relation source=\"99999\" target=\"99998\" type=\"friend\"/></relations>"),xml.replace("<name>Existing</name>",""),xml.replace("cover=\""+characters.findById(c.getId()).orElseThrow().getCoverImageId()+"\"","cover=\"999999\""),"<!DOCTYPE noctalith [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><noctalith version=\"1\">&xxe;</noctalith>")){Map<String,byte[]> altered=new LinkedHashMap<>(entries);altered.put("manifest.xml",changed.getBytes(StandardCharsets.UTF_8));invalid.add(zip(altered));}
        Map<String,byte[]> broken=new LinkedHashMap<>(entries);broken.remove(broken.keySet().stream().filter(k->k.startsWith("images/")).findFirst().orElseThrow());invalid.add(zip(broken));Map<String,byte[]> badImage=new LinkedHashMap<>(entries);badImage.put(badImage.keySet().stream().filter(k->k.startsWith("images/")).findFirst().orElseThrow(),"not image".getBytes());invalid.add(zip(badImage));
        for(byte[] bytes:invalid){assertThrows(Exception.class,()->backup.restore(new ByteArrayInputStream(bytes)));assertEquals(1,characters.count());assertEquals(1,pictures.count());assertEquals(0,relations.count());}
        assertEquals(302,multipart("/backup/restore","archive",invalid.get(0),Map.of("confirmed","true")).statusCode());assertTrue(text(get("/backup")).contains("バックアップZIP"));assertEquals(1,characters.count());
    }
    @Test void legacyImagesMigrateIdempotentlyAndCanBeBackedUp() throws Exception {
        OriginalCharacter c=save("Legacy");c.setImageRegistered(true);characters.save(c);byte[] image=png(0x123456);Files.write(UPLOADS.resolve(c.getId()+".png"),image);gallery.run(null);gallery.run(null);assertEquals(1,gallery.list(c.getId()).size());assertNotNull(characters.findById(c.getId()).orElseThrow().getCoverImageId());assertArrayEquals(image,get("/characters/"+c.getId()+"/image").body());assertTrue(backup.exportArchive().length>0);
    }
    @Test void missingStoredImagesDoNotProduceIncompleteBackup() throws Exception {
        OriginalCharacter c=save("Missing");c.setImageRegistered(true);characters.save(c);assertThrows(IllegalArgumentException.class,()->backup.exportArchive());assertEquals(302,get("/backup/download").statusCode());assertTrue(text(get("/backup")).contains("画像ファイル"));
    }
    @Test void restoreRollsBackDatabaseAndNewFilesWhenPersistenceFails() throws Exception {
        OriginalCharacter c=save("Original");gallery.upload(c.getId(),new MockMultipartFile("image",png(0)),"portrait");
        byte[] archive=backup.exportArchive();
        Set<Path> before;
        try(var files=Files.list(UPLOADS)){before=new HashSet<>(files.toList());}
        org.mockito.Mockito.doThrow(new IllegalStateException("simulated database failure")).when(pictures).saveAndFlush(org.mockito.ArgumentMatchers.any(CharacterPicture.class));
        assertThrows(IllegalStateException.class,()->backup.restore(new ByteArrayInputStream(archive)));
        assertEquals(1,characters.count());assertEquals(1,pictures.count());assertEquals("Original",characters.findAll().get(0).getName());
        try(var files=Files.list(UPLOADS)){assertEquals(before,new HashSet<>(files.toList()));}
    }
    @Test void legacyProfileMigrationPreservesDataAndDoesNotRepopulateClearedSettings() throws Exception {
        OriginalCharacter c=save("Legacy profile");c.setMemo("旧メモ");c.setAge("18歳");c.setBirthday("4月12日");c.setHeight("165cm");c.setSpecies("エルフ");
        c.setPersonality("穏やか");c.setAbilities("魔法");c.setAffiliation("騎士団");c.setFavorite(true);characters.save(c);
        profileMigration.run(null);profileMigration.run(null);
        var migrated=characters.findById(c.getId()).orElseThrow();
        for(String value:List.of("旧メモ","18歳","4月12日","165cm","エルフ"))assertTrue(migrated.getBasicSettings().contains(value));
        assertEquals("旧メモ",migrated.getMemo());assertEquals("穏やか",migrated.getPersonality());assertEquals("魔法",migrated.getAbilities());assertTrue(migrated.isFavorite());
        assertEquals(302,post("/characters/"+c.getId()+"/update","name=Legacy&affiliation="+encode("騎士団")+"&basicSettings=&age=999").statusCode());
        profileMigration.run(null);assertEquals("",characters.findById(c.getId()).orElseThrow().getBasicSettings());assertEquals("18歳",characters.findById(c.getId()).orElseThrow().getAge());
    }
    String legacyArchiveXml() {
        String first="<character id=\"101\" favorite=\"true\" cover=\"11\"><name>旧キャラ</name><personality>穏やか</personality><memo>旧設定</memo><age>18歳</age><birthday>4月12日</birthday><height>165cm</height><species>エルフ</species><affiliation>騎士団</affiliation><abilities>魔法</abilities><tags><tag>主人公</tag></tags><picture id=\"11\" title=\"立ち絵\"/></character>";
        String second="<character id=\"202\" favorite=\"false\"><name>相手</name><personality/><memo/><age/><birthday/><height/><species/><affiliation/><abilities/><tags/></character>";
        return "<noctalith version=\"1\"><characters>"+first+second+"</characters><relations><relation source=\"101\" target=\"202\" type=\"友人\">昔からの仲間</relation></relations></noctalith>";
    }
    @Test void restoresVersionOneBackupWithLegacyFieldsImagesAndRelations() throws Exception {
        OriginalCharacter existing=save("Existing");
        byte[] archive=zip(Map.of("manifest.xml",legacyArchiveXml().getBytes(StandardCharsets.UTF_8),"images/11.png",png(0xff0000)));
        assertEquals(2,backup.restore(new ByteArrayInputStream(archive)));assertEquals(3,characters.count());
        var c=characters.findAll().stream().filter(x->x.getName().equals("旧キャラ")).findFirst().orElseThrow();
        assertNotEquals(101L,c.getId());assertTrue(c.getBasicSettings().contains("旧設定"));assertTrue(c.getBasicSettings().contains("18歳"));assertTrue(c.getBasicSettings().contains("エルフ"));
        assertEquals("穏やか",c.getPersonality());assertEquals("魔法",c.getAbilities());assertEquals("騎士団",c.getAffiliation());assertTrue(c.isFavorite());
        assertArrayEquals(png(0xff0000),get("/characters/"+c.getId()+"/image").body());assertEquals("相手",relations.findBySourceIdOrderByIdAsc(c.getId()).get(0).getTarget().getName());
        assertEquals("Existing",characters.findById(existing.getId()).orElseThrow().getName());
        String xml=new String(unzip(backup.exportArchive()).get("manifest.xml"),StandardCharsets.UTF_8);assertTrue(xml.contains("version=\"7\""));assertTrue(xml.contains("speechStyle"));
        byte[] invalid=zip(Map.of("manifest.xml",legacyArchiveXml().replace("<age>18歳</age>","<age>"+"a".repeat(41)+"</age>").getBytes(StandardCharsets.UTF_8),"images/11.png",png(0)));
        assertThrows(IllegalArgumentException.class,()->backup.restore(new ByteArrayInputStream(invalid)));assertEquals(3,characters.count());
    }
    @Test void rendersExactRequestedProfileOrderAndRejectsIncompleteVersionTwoBackup() throws Exception {
        var c=save("Order");String html=text(get("/characters/"+c.getId()+"/edit"));
        int previous=-1;
        for(String field:List.of("name","affiliation","basicSettings","personality","speechStyle","firstPerson","feelingsTowardMikado","likes","dislikes","abilities","tagsInput")){
            int index=html.indexOf("name=\""+field+"\"");assertTrue(index>previous,field+" order");previous=index;
        }
        for(String old:List.of("age","birthday","height","species","memo"))assertFalse(html.contains("name=\""+old+"\""));
        var entries=unzip(backup.exportArchive());String xml=new String(entries.get("manifest.xml"),StandardCharsets.UTF_8);
        String missing=xml.replaceAll("<speechStyle(?:/>|>.*?</speechStyle>)","");assertNotEquals(xml,missing);
        entries.put("manifest.xml",missing.getBytes(StandardCharsets.UTF_8));byte[] invalid=zip(entries);
        assertThrows(IllegalArgumentException.class,()->backup.restore(new ByteArrayInputStream(invalid)));assertEquals(1,characters.count());
    }
    @Test void validatesEveryProfileFieldAtAndBeyondItsBoundary() throws Exception {
        for(var field:ProfileFields.limits().entrySet()){
            if(Set.of("name", "gender", "heightCm", "bustCm").contains(field.getKey()))continue;
            String valid="あ".repeat(field.getValue());assertEquals(302,post("/characters","name=Boundary&"+field.getKey()+"="+encode(valid)).statusCode());
            OriginalCharacter c=characters.findAll().stream().max(Comparator.comparing(OriginalCharacter::getId)).orElseThrow();
            assertEquals(valid,ProfileFields.values(c).get(field.getKey()));
            assertEquals(200,post("/characters/"+c.getId()+"/update","name=Changed&"+field.getKey()+"="+encode(valid+"あ")).statusCode());
            var unchanged=characters.findById(c.getId()).orElseThrow();assertEquals("Boundary",unchanged.getName());assertEquals(valid,ProfileFields.values(unchanged).get(field.getKey()));
        }
    }
    @Test void protagonistGallerySupportsUploadCoverDisplayBackupSharingAndDelete() throws Exception {
        var hero=save("Hero");hero.setWorld(CharacterWorld.PROTAGONIST);hero.setBasicSettings("Story");characters.save(hero);
        String base="/characters/"+hero.getId();byte[] red=png(0xff0000),blue=png(0x0000ff);
        String edit=text(get(base+"/edit"));assertTrue(edit.contains("name=\"image\""));assertTrue(edit.contains("画像を追加する"));
        assertFalse(edit.contains("name=\"bustCm\""));assertFalse(edit.contains("name=\"personality\""));
        assertEquals(302,multipart(base+"/image","image",red,Map.of("title","主人公の立ち絵")).statusCode());
        assertEquals(302,multipart(base+"/image","image",blue,Map.of("title","主人公の別衣装")).statusCode());
        var images=gallery.list(hero.getId());assertEquals(2,images.size());assertArrayEquals(red,get(base+"/image").body());
        assertEquals(302,post(base+"/images/"+images.get(1).getId()+"/cover","").statusCode());assertArrayEquals(blue,get(base+"/image").body());
        assertTrue(text(get("/")).contains("src=\""+base+"/image\""));assertTrue(text(get(base)).contains("src=\""+base+"/image\""));
        assertTrue(text(get(base+"/edit")).contains("主人公の別衣装"));
        var shared=unzip(share.exportAll());String reference=String.format(java.util.Locale.ROOT,"C%06d",hero.getId());
        assertTrue(shared.containsKey("images/"+reference+"/"+String.format(java.util.Locale.ROOT,"I%06d",images.get(0).getId())+".png"));
        assertTrue(shared.containsKey("images/"+reference+"/"+String.format(java.util.Locale.ROOT,"I%06d",images.get(1).getId())+".png"));
        assertTrue(new String(shared.get("01_ALL_CHARACTERS.md"),StandardCharsets.UTF_8).contains("主人公の別衣装"));
        byte[] archive=backup.exportArchive();assertEquals(1,backup.restore(new ByteArrayInputStream(archive)));
        var restored=characters.findAll().stream().filter(c->!c.getId().equals(hero.getId())).findFirst().orElseThrow();
        assertTrue(restored.isProtagonist());assertEquals("Story",restored.getBasicSettings());assertEquals(2,gallery.list(restored.getId()).size());
        assertArrayEquals(blue,get("/characters/"+restored.getId()+"/image").body());
        Path cover=gallery.path(images.get(1).getStorageName());assertEquals(302,post(base+"/images/"+images.get(1).getId()+"/delete","").statusCode());
        assertFalse(Files.exists(cover));assertArrayEquals(red,get(base+"/image").body());
        assertEquals(302,post(base+"/images/"+images.get(0).getId()+"/delete","").statusCode());assertEquals(404,get(base+"/image").statusCode());
        assertFalse(characters.findById(hero.getId()).orElseThrow().isImageRegistered());
    }
    @Test void protagonistRejectsInvalidImagesWithoutLosingExistingCover() throws Exception {
        var hero=save("Hero");hero.setWorld(CharacterWorld.PROTAGONIST);characters.save(hero);byte[] image=png(0xff0000);
        gallery.upload(hero.getId(),new MockMultipartFile("image",image),"Cover");
        assertEquals(302,multipart("/characters/"+hero.getId()+"/image","image","not an image".getBytes(StandardCharsets.UTF_8),Map.of()).statusCode());
        assertEquals(1,gallery.list(hero.getId()).size());assertArrayEquals(image,get("/characters/"+hero.getId()+"/image").body());
        assertTrue(text(get("/characters/"+hero.getId()+"/edit")).contains("PNG・JPEG・GIF画像を選択してください。"));
    }

}
