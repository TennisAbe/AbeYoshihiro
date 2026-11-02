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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:comic-share", "spring.jpa.hibernate.ddl-auto=create-drop"})
class ComicShareTests {
    static final Path UPLOADS = temporary();
    static Path temporary() { try { return Files.createTempDirectory("noctalith-share-tests-"); } catch (IOException e) { throw new UncheckedIOException(e); } }
    @DynamicPropertySource static void directory(DynamicPropertyRegistry registry) { registry.add("noctalith.upload-directory", UPLOADS::toString); }
    @Value("${local.server.port}") int port;
    @Autowired ComicShareService share;
    @Autowired CharacterRepository characters;
    @Autowired RelationRepository relations;
    @Autowired PictureRepository pictures;
    @Autowired RevisionRepository revisions;
    @Autowired RevisionService history;
    @Autowired GalleryService gallery;
    @Autowired AffiliationOrderingService ordering;
    @Autowired AffiliationOrderRepository orders;
    HttpClient client;
    @BeforeEach void reset() throws IOException {
        relations.deleteAll();pictures.deleteAll();characters.deleteAll();orders.deleteAll();
        try(var files=Files.list(UPLOADS)){for(Path path:files.toList())Files.delete(path);}
        client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
    }
    OriginalCharacter save(String name,CharacterWorld world,String affiliation) {
        OriginalCharacter c=new OriginalCharacter();c.setName(name);c.setWorld(world);c.setAffiliation(affiliation);c.setBasicSettings("登録された基本設定");c.setPersonality("穏やか");c.setSpeechStyle("丁寧な口調");c.setFirstPerson("私");c.setFeelingsTowardMikado("強い信頼");c.setLikes("紅茶");c.setDislikes("嘘");c.setAbilities("星の魔法");c.replaceTags(TagParser.parse("主人公,星"));return characters.save(c);
    }
    String ref(Long id){return String.format(Locale.ROOT,"C%06d",id);}
    String imageRef(Long id){return String.format(Locale.ROOT,"I%06d",id);}
    Map<String,byte[]> unpack(byte[] bytes) throws IOException {
        Map<String,byte[]> result=new LinkedHashMap<>();try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){assertFalse(entry.getName().contains(".."));assertNull(result.put(entry.getName(),zip.readAllBytes()));}}return result;
    }
    String text(Map<String,byte[]> zip,String path){return new String(zip.get(path),StandardCharsets.UTF_8);}
    byte[] png(int color) throws IOException {BufferedImage image=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,color);ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();}
    HttpResponse<byte[]> get(String path) throws Exception {return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());}
    @Test void packagesAllCurrentCharactersImagesCaptionsAndRelationshipsWithStableReferences() throws Exception {
        var a=save("シズ",CharacterWorld.ALTER_PALACE,"宮殿");var b=save("シズ",CharacterWorld.OTHER_WORLD,"騎士団");save("A/../B",CharacterWorld.MODERN_WORLD,"");
        gallery.upload(a.getId(),new MockMultipartFile("image",png(0xff0000)),"通常衣装");gallery.upload(a.getId(),new MockMultipartFile("image",png(0x0000ff)),"笑顔,\"衣装違い\"");var images=gallery.list(a.getId());gallery.cover(a.getId(),images.get(1).getId());
        var relation=new CharacterRelation();relation.setSource(a);relation.setTarget(b);relation.setRelationType("友人");relation.setDescription("昔からの仲間");relations.save(relation);
        var state=ProfileSnapshot.of(characters.findById(a.getId()).orElseThrow());byte[] original=Files.readAllBytes(gallery.path(images.get(0).getStorageName()));
        var zip=unpack(share.exportAll());assertEquals(10,zip.size());
        for(String path:List.of("00_START_HERE.md","01_ALL_CHARACTERS.md","02_IMAGE_INDEX.csv","03_CHATGPT_PROMPT.txt","characters/"+ref(a.getId())+".md","characters/"+ref(b.getId())+".md"))assertTrue(zip.containsKey(path),path);
        String all=text(zip,"01_ALL_CHARACTERS.md");
        for(String expected:List.of("シズ","登録された基本設定","丁寧な口調","私","強い信頼","紅茶","嘘","星の魔法","主人公","昔からの仲間","オルタパレス","異世界","現代世界"))assertTrue(all.contains(expected),expected);
        assertTrue(all.contains("["+ref(a.getId())+"] シズ → ["+ref(b.getId())+"] シズ"));
        String rootImage="images/"+ref(a.getId())+"/"+imageRef(images.get(1).getId())+".png";
        assertTrue(all.contains(rootImage));assertTrue(all.contains("（代表画像）"));assertTrue(text(zip,"characters/"+ref(a.getId())+".md").contains("../"+rootImage));
        assertArrayEquals(png(0x0000ff),zip.get(rootImage));assertTrue(text(zip,"02_IMAGE_INDEX.csv").contains("\"笑顔,\"\"衣装違い\"\"\""));assertTrue(text(zip,"03_CHATGPT_PROMPT.txt").contains("画像を実際に確認できない場合"));
        assertEquals(3,characters.count());assertEquals(2,pictures.count());assertEquals(1,relations.count());assertEquals(0,revisions.count());assertEquals(state,ProfileSnapshot.of(characters.findById(a.getId()).orElseThrow()));assertArrayEquals(original,Files.readAllBytes(gallery.path(images.get(0).getStorageName())));
    }
    @Test void includesCurrentSettingsOnlyAndOmitsHistoryAndLegacyColumns() throws Exception {
        var c=save("Current",CharacterWorld.ALTER_PALACE,"宮殿");c.setBasicSettings("OLD_SETTING_NOT_FOR_SHARE");c.setMemo("LEGACY_COLUMN_NOT_FOR_SHARE");characters.save(c);
        OriginalCharacter update=new OriginalCharacter();ProfileSnapshot.apply(ProfileSnapshot.of(c),update);update.setBasicSettings("CURRENT_SETTING_FOR_SHARE");history.update(c.getId(),update);
        var zip=unpack(share.exportAll());String documents=String.join("\n",zip.entrySet().stream().filter(e->e.getKey().endsWith(".md")).map(e->new String(e.getValue(),StandardCharsets.UTF_8)).toList());
        assertTrue(documents.contains("CURRENT_SETTING_FOR_SHARE"));assertFalse(documents.contains("OLD_SETTING_NOT_FOR_SHARE"));assertFalse(documents.contains("LEGACY_COLUMN_NOT_FOR_SHARE"));assertFalse(zip.containsKey("manifest.xml"));assertTrue(zip.keySet().stream().noneMatch(k->k.contains("history")));assertEquals(1,revisions.count());
    }
    @Test void rejectsEmptyArchiveAndProvidesDownloadHeadersAndCompleteScope() throws Exception {
        assertThrows(IllegalArgumentException.class,share::exportAll);assertEquals(200,get("/share").statusCode());assertEquals(302,get("/share/download").statusCode());assertTrue(new String(get("/share").body(),StandardCharsets.UTF_8).contains("まだいません"));
        save("A",CharacterWorld.ALTER_PALACE,"宮殿");save("B",CharacterWorld.OTHER_WORLD,"騎士団");
        var response=get("/share/download?q=A&favorites=true");assertEquals(200,response.statusCode());assertEquals("application/zip",response.headers().firstValue("content-type").orElseThrow());assertEquals("no-store",response.headers().firstValue("cache-control").orElseThrow());assertTrue(response.headers().firstValue("content-disposition").orElseThrow().contains("noctalith-comic-reference-"));
        var zip=unpack(response.body());assertEquals(2,zip.keySet().stream().filter(k->k.startsWith("characters/")).count());assertTrue(text(zip,"01_ALL_CHARACTERS.md").contains("画像未登録"));assertEquals(1,text(zip,"02_IMAGE_INDEX.csv").lines().count());assertEquals(200,get("/comic-share.js").statusCode());
    }
    @Test void missingAndBrokenImagesDoNotProduceAnIncompleteDownload() throws Exception {
        var c=save("Missing",CharacterWorld.ALTER_PALACE,"");c.setImageRegistered(true);characters.save(c);assertThrows(IllegalArgumentException.class,share::exportAll);
        c.setImageRegistered(false);characters.save(c);gallery.upload(c.getId(),new MockMultipartFile("image",png(0)),"portrait");var image=gallery.list(c.getId()).get(0);Path path=gallery.path(image.getStorageName());
        Files.delete(path);assertThrows(IllegalArgumentException.class,share::exportAll);assertEquals(302,get("/share/download").statusCode());assertTrue(new String(get("/share").body(),StandardCharsets.UTF_8).contains("見つかりません"));
        Files.writeString(path,"broken image");assertThrows(IllegalArgumentException.class,share::exportAll);assertEquals(1,pictures.count());assertEquals(1,characters.count());
    }
    @Test void rejectsOversizedImageBeforeReadingItsContent() throws Exception {
        var c=save("Large",CharacterWorld.ALTER_PALACE,"");gallery.upload(c.getId(),new MockMultipartFile("image",png(0)),"portrait");Path path=gallery.path(gallery.list(c.getId()).get(0).getStorageName());
        try(RandomAccessFile file=new RandomAccessFile(path.toFile(),"rw")){file.setLength(20L*1024*1024+1);}
        assertThrows(IllegalArgumentException.class,share::exportAll);assertEquals(1,characters.count());assertEquals(1,pictures.count());
    }
    @Test void preservesLiteralProfileTextAndUsesSafeFileNames() throws Exception {
        var c=save("A/../[X]|<tag>",CharacterWorld.ALTER_PALACE,"");c.setBasicSettings("```\n# 設定内の見出し\n<script>literal</script>\n```");characters.save(c);
        var zip=unpack(share.exportAll());String doc=text(zip,"characters/"+ref(c.getId())+".md");assertTrue(doc.contains("````\n```\n# 設定内の見出し"));assertTrue(doc.contains("<script>literal</script>"));assertTrue(doc.contains("\\[X\\]\\|\\<tag\\>"));
        assertTrue(zip.keySet().stream().filter(k->k.startsWith("characters/")).allMatch(k->k.matches("characters/C[0-9]+\\.md")));
    }
    @Test void followsWorldAndConfiguredAffiliationOrder() throws Exception {
        var modern=save("Modern",CharacterWorld.MODERN_WORLD,"Y");var a=save("Palace A",CharacterWorld.ALTER_PALACE,"A");var b=save("Palace B",CharacterWorld.ALTER_PALACE,"B");ordering.save(CharacterWorld.ALTER_PALACE,List.of("B","A"),List.of(1,2));
        String doc=text(unpack(share.exportAll()),"01_ALL_CHARACTERS.md");assertTrue(doc.indexOf("## ["+ref(b.getId())+"]")<doc.indexOf("## ["+ref(a.getId())+"]"));assertTrue(doc.indexOf("## ["+ref(a.getId())+"]")<doc.indexOf("## ["+ref(modern.getId())+"]"));
    }
}
