package com.abeyoshihiro.character_manager;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:workflow", "spring.jpa.hibernate.ddl-auto=create-drop"})
class CharacterWorkflowTests {
    @Value("${local.server.port}")
    int port;

    @Autowired
    CharacterRepository repository;

    final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void reset() {
        repository.deleteAll();
    }

    HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    OriginalCharacter save(String name) {
        OriginalCharacter character = new OriginalCharacter();
        character.setName(name);
        character.setPersonality("穏やか");
        character.setBasicSettings("基本設定の内容");
        return repository.save(character);
    }

    @Test
    void searchesPartialNamesIgnoringCaseAndEscapesWildcards() throws Exception {
        save("アリスAlice");
        save("ボブ");
        save("100%ちゃん");
        var japanese = get("/?q=" + URLEncoder.encode(" アリ ", StandardCharsets.UTF_8));
        assertEquals(200, japanese.statusCode());
        assertTrue(japanese.body().contains("アリスAlice"));
        assertFalse(japanese.body().contains("ボブ"));
        assertTrue(get("/?q=alice").body().contains("アリスAlice"));
        var literalPercent = get("/?q=%25");
        assertTrue(literalPercent.body().contains("100%ちゃん"));
        assertFalse(literalPercent.body().contains("アリスAlice"));
        assertTrue(get("/?q=missing").body().contains("該当するキャラクターが見つかりません"));
    }

    @Test
    void rendersCompactListAndSeparateEditorAndNewForm() throws Exception {
        var character = save("<テスト>");
        var list = get("/");
        assertEquals(200, list.statusCode());
        assertTrue(list.body().contains("&lt;テスト&gt;"));
        assertTrue(list.body().contains("/characters/" + character.getId() + "/edit"));
        assertFalse(list.body().contains("textarea"));
        var edit = get("/characters/" + character.getId() + "/edit");
        assertEquals(200, edit.statusCode());
        assertTrue(edit.body().contains("基本設定"));
        assertTrue(edit.body().contains("multipart/form-data"));
        var create = get("/characters/new");
        assertEquals(200, create.statusCode());
        assertTrue(create.body().contains("登録して編集を続ける"));
        assertFalse(create.body().contains("multipart/form-data"));
        assertEquals(200, get("/style.css").statusCode());
        assertTrue(list.body().contains("NOCTALITH"));
        assertTrue(list.body().contains("/palace.js"));
        assertEquals(200, get("/palace.js").statusCode());
        assertEquals(200, get("/images/noctalith-logo.png").statusCode());
        assertEquals(200, get("/fonts/cinzel.woff2").statusCode());
        assertEquals(200, get("/fonts/noto-serif-jp.woff2").statusCode());
    }

    @Test
    void createsUpdatesAndDeletesWithoutLosingImageFlag() throws Exception {
        var created = post("/characters", "name=Alice&personality=kind&basicSettings=story&imageRegistered=true");
        assertEquals(302, created.statusCode());
        var character = repository.findAll().get(0);
        assertFalse(character.isImageRegistered());
        assertEquals("/characters/" + character.getId() + "/edit", URI.create(created.headers().firstValue("location").orElseThrow()).getPath());
        character.setImageRegistered(true);
        repository.save(character);
        var updated = post("/characters/" + character.getId() + "/update", "name=Alice2&personality=brave&basicSettings=new");
        assertEquals(302, updated.statusCode());
        var saved = repository.findById(character.getId()).orElseThrow();
        assertEquals("Alice2", saved.getName());
        assertEquals("brave", saved.getPersonality());
        assertTrue(saved.isImageRegistered());
        assertEquals(1, repository.count());
        assertEquals(302, post("/characters/" + saved.getId() + "/delete", "").statusCode());
        assertEquals(0, repository.count());
    }

    @Test
    void handlesEmptyNamesEmptyListAndMissingCharacters() throws Exception {
        assertTrue(get("/").body().contains("まだキャラクターがいません"));
        assertEquals(302, post("/characters", "name=+++").statusCode());
        assertEquals(0, repository.count());
        assertEquals(404, get("/characters/999999/edit").statusCode());
        assertEquals(404, post("/characters/999999/update", "name=Alice").statusCode());
    }
    String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    @Test
    void savesTagsSearchesExactlyAndCombinesWithName() throws Exception {
        var response = post("/characters", "name=Alice&tagsInput=" + encode(" 魔法, 主人公、魔法 "));
        assertEquals(302, response.statusCode());
        var alice = repository.findAll().get(0);
        assertEquals(java.util.Set.of("魔法", "主人公"), alice.getTags());
        var bob = save("Bob");
        bob.replaceTags(TagParser.parse("魔法使い"));
        repository.save(bob);
        var list = get("/?tag=" + encode("魔法"));
        assertEquals(200, list.statusCode());
        assertTrue(list.body().contains("Alice"));
        assertFalse(list.body().contains("Bob"));
        assertTrue(list.body().contains("class=\"tag\""));
        assertTrue(get("/?q=ali&tag=" + encode("魔法")).body().contains("Alice"));
        assertTrue(get("/?q=Bob&tag=" + encode("魔法")).body().contains("該当するキャラクターが見つかりません"));
        assertTrue(get("/?tag=unknown").body().contains("該当するキャラクターが見つかりません"));
        var edit = get("/characters/" + alice.getId() + "/edit");
        assertEquals(200, edit.statusCode());
        assertTrue(edit.body().contains("主人公"));
        assertTrue(edit.body().contains("name=\"tagsInput\""));
        assertEquals(302, post("/characters/" + alice.getId() + "/update", "name=Alice&tagsInput=").statusCode());
        assertTrue(repository.findById(alice.getId()).orElseThrow().getTags().isEmpty());
        assertEquals(java.util.List.of("魔法使い"), repository.findAllTags());
        post("/characters/" + bob.getId() + "/delete", "");
        assertTrue(repository.findAllTags().isEmpty());
    }

    @Test
    void invalidTagsKeepInputAndDoNotChangeStoredData() throws Exception {
        String tooLong = "あ".repeat(31);
        var create = post("/characters", "name=Alice&tagsInput=" + encode(tooLong));
        assertEquals(200, create.statusCode());
        assertTrue(create.body().contains("タグは1個30文字以内"));
        assertEquals(0, repository.count());
        var alice = save("Alice");
        alice.replaceTags(TagParser.parse("既存"));
        alice.setImageRegistered(true);
        repository.save(alice);
        var update = post("/characters/" + alice.getId() + "/update",
                "name=Changed&tagsInput=" + encode(tooLong));
        assertEquals(200, update.statusCode());
        assertTrue(update.body().contains("/characters/" + alice.getId() + "/update"));
        assertTrue(update.body().contains("Changed"));
        assertTrue(update.body().contains(tooLong));
        var unchanged = repository.findById(alice.getId()).orElseThrow();
        assertEquals("Alice", unchanged.getName());
        assertEquals(java.util.Set.of("既存"), unchanged.getTags());
        var valid = post("/characters/" + alice.getId() + "/update",
                "name=Alice2&tagsInput=" + encode("新規,新規") + "&imageRegistered=false&id=9999");
        assertEquals(302, valid.statusCode());
        var saved = repository.findById(alice.getId()).orElseThrow();
        assertTrue(saved.isImageRegistered());
        assertEquals(java.util.Set.of("新規"), saved.getTags());
        assertEquals(1, repository.count());
    }
    java.util.List<String> displayNumbers(String html) {
        return java.util.regex.Pattern.compile(">NO\\. (\\d+)</span>")
                .matcher(html).results().map(match -> match.group(1)).toList();
    }

    @Test
    void displayNumbersStayConsecutiveAfterDeletionAndFilteringWithoutChangingIds() throws Exception {
        var first = save("Number first");
        var second = save("Number second");
        var third = save("Number third");
        var fourth = save("Number fourth");
        fourth.setImageRegistered(true);
        fourth.replaceTags(TagParser.parse("選択"));
        repository.save(fourth);
        assertEquals(java.util.List.of("001", "002", "003", "004"), displayNumbers(get("/").body()));

        assertEquals(302, post("/characters/" + second.getId() + "/delete", "").statusCode());
        assertEquals(java.util.List.of("001", "002", "003"), displayNumbers(get("/").body()));
        assertEquals(302, post("/characters/" + first.getId() + "/delete", "").statusCode());
        var remaining = get("/").body();
        assertEquals(java.util.List.of("001", "002"), displayNumbers(remaining));
        assertTrue(remaining.contains("/characters/" + third.getId() + "/edit"));
        assertTrue(remaining.contains("/characters/" + fourth.getId() + "/image"));
        assertEquals(java.util.List.of(third.getId(), fourth.getId()),
                repository.findByNameContainingIgnoreCaseOrderByIdAsc("").stream()
                        .map(OriginalCharacter::getId).toList());
        assertTrue(repository.findById(fourth.getId()).orElseThrow().isImageRegistered());
        assertEquals(java.util.List.of("001"), displayNumbers(get("/?q=fourth").body()));
        assertEquals(java.util.List.of("001"), displayNumbers(get("/?tag=" + encode("選択")).body()));
        assertTrue(displayNumbers(get("/?q=missing").body()).isEmpty());
    }
}
