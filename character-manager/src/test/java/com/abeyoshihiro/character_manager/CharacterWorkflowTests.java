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
        character.setMemo("設定メモ");
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
        assertTrue(edit.body().contains("設定メモ"));
        assertTrue(edit.body().contains("multipart/form-data"));
        var create = get("/characters/new");
        assertEquals(200, create.statusCode());
        assertTrue(create.body().contains("登録して編集を続ける"));
        assertFalse(create.body().contains("multipart/form-data"));
        assertEquals(200, get("/style.css").statusCode());
    }

    @Test
    void createsUpdatesAndDeletesWithoutLosingImageFlag() throws Exception {
        var created = post("/characters", "name=Alice&personality=kind&memo=story&imageRegistered=true");
        assertEquals(302, created.statusCode());
        var character = repository.findAll().get(0);
        assertFalse(character.isImageRegistered());
        assertEquals("/characters/" + character.getId() + "/edit", URI.create(created.headers().firstValue("location").orElseThrow()).getPath());
        character.setImageRegistered(true);
        repository.save(character);
        var updated = post("/characters/" + character.getId() + "/update", "name=Alice2&personality=brave&memo=new");
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
}
