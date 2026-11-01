package com.abeyoshihiro.character_manager;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BackupService {
    private static final long MAX_TOTAL = 250L * 1024 * 1024;
    private final CharacterRepository characters;
    private final PictureRepository pictures;
    private final RelationRepository relations;
    private final GalleryService gallery;
    private final TransactionTemplate transactions;
    public BackupService(CharacterRepository characters, PictureRepository pictures, RelationRepository relations,
                         GalleryService gallery, TransactionTemplate transactions) {
        this.characters = characters; this.pictures = pictures; this.relations = relations; this.gallery = gallery; this.transactions = transactions;
    }
    private static DocumentBuilderFactory factory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false); factory.setExpandEntityReferences(false); return factory;
    }
    private static Element append(Document doc, Element parent, String name, String value) {
        Element child = doc.createElement(name); child.setTextContent(value == null ? "" : value); parent.appendChild(child); return child;
    }
    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) if (n instanceof Element e && e.getTagName().equals(name)) result.add(e);
        return result;
    }
    private static long id(String value) {
        try { long id = Long.parseLong(value); if (id <= 0) throw new NumberFormatException(); return id; }
        catch (NumberFormatException e) { throw new IllegalArgumentException("バックアップ内のIDが不正です。"); }
    }
    private static String limited(String value, int max) {
        if (value.length() > max) throw new IllegalArgumentException("バックアップ内の文字数が上限を超えています。");
        return value;
    }
    @Transactional(readOnly = true)
    public byte[] exportArchive() throws Exception {
        synchronized (gallery) {
            Document doc = factory().newDocumentBuilder().newDocument();
            Element root = doc.createElement("noctalith"); root.setAttribute("version", "1"); doc.appendChild(root);
            Element cs = append(doc, root, "characters", "");
            Map<String, Path> images = new LinkedHashMap<>(); long total = 0;
            List<OriginalCharacter> allCharacters = characters.findAll();
            List<CharacterRelation> allRelations = relations.findAll();
            if (allCharacters.size() > 5000 || allRelations.size() > 20000) throw new IllegalArgumentException("バックアップの登録件数が上限を超えています。");
            for (OriginalCharacter c : allCharacters) {
                Element ce = append(doc, cs, "character", ""); ce.setAttribute("id", c.getId().toString());
                ce.setAttribute("favorite", Boolean.toString(c.isFavorite()));
                if (c.getCoverImageId() != null) ce.setAttribute("cover", c.getCoverImageId().toString());
                ProfileFields.values(c).forEach((key, value) -> append(doc, ce, key, value));
                Element tags = append(doc, ce, "tags", ""); for (String tag : c.getTags()) append(doc, tags, "tag", tag);
                List<CharacterPicture> items = gallery.list(c.getId());
                if (c.isImageRegistered() && items.isEmpty()) throw new IllegalArgumentException("画像ファイルが見つからないキャラクターがあります。保存先を確認してください。");
                for (CharacterPicture p : items) {
                    Element pe = append(doc, ce, "picture", ""); pe.setAttribute("id", p.getId().toString());
                    pe.setAttribute("title", p.getTitle() == null ? "" : p.getTitle());
                    Path path = gallery.path(p.getStorageName());
                    if (!Files.isRegularFile(path)) throw new IllegalArgumentException("保存済みの画像ファイルが見つかりません。");
                    total += Files.size(path);
                    if (Files.size(path) > 20 * 1024 * 1024 || total > MAX_TOTAL) throw new IllegalArgumentException("バックアップが上限250MBを超えています。");
                    // Do not offer an archive that cannot be restored under the same image rules.
                    try { gallery.normalize(Files.readAllBytes(path)); }
                    catch (IOException exception) { throw new IllegalArgumentException("読み込めない画像があります。画像を登録し直してください。", exception); }
                    images.put("images/" + p.getId() + ".png", path);
                    if (images.size() >= 10000) throw new IllegalArgumentException("バックアップの画像枚数が上限を超えています。");
                }
            }
            Element rs = append(doc, root, "relations", "");
            for (CharacterRelation r : allRelations) {
                Element re = append(doc, rs, "relation", r.getDescription());
                re.setAttribute("source", r.getSource().getId().toString()); re.setAttribute("target", r.getTarget().getId().toString()); re.setAttribute("type", r.getRelationType());
            }
            TransformerFactory tf = TransformerFactory.newInstance(); tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = tf.newTransformer(); transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            ByteArrayOutputStream manifest = new ByteArrayOutputStream(); transformer.transform(new DOMSource(doc), new StreamResult(manifest));
            if (manifest.size() > 5 * 1024 * 1024) throw new IllegalArgumentException("バックアップの登録件数が多すぎます。");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                zip.putNextEntry(new ZipEntry("manifest.xml")); zip.write(manifest.toByteArray()); zip.closeEntry();
                for (var entry : images.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); Files.copy(entry.getValue(), zip); zip.closeEntry(); }
            }
            if (output.size() > 100 * 1024 * 1024) throw new IllegalArgumentException("ZIPは100MB以内にしてください。");
            return output.toByteArray();
        }
    }
    private Map<String, byte[]> unpack(InputStream input) throws IOException {
        Map<String, byte[]> entries = new HashMap<>(); long total = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || !name.matches("manifest\\.xml|images/[1-9][0-9]*\\.png") || entries.containsKey(name) || entries.size() >= 10000)
                    throw new IllegalArgumentException("不正なファイルを含むZIPです。");
                int limit = name.equals("manifest.xml") ? 5 * 1024 * 1024 : 20 * 1024 * 1024;
                ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
                while ((count = zip.read(buffer)) != -1) {
                    total += count;
                    if ((long) out.size() + count > limit || total > MAX_TOTAL) throw new IllegalArgumentException("ZIP展開後のサイズが上限を超えています。");
                    out.write(buffer, 0, count);
                }
                entries.put(name, out.toByteArray());
            }
        }
        if (!entries.containsKey("manifest.xml")) throw new IllegalArgumentException("NOCTALITHのバックアップZIPを選択してください。");
        return entries;
    }
    private record CharacterData(long id, OriginalCharacter value, Long cover) { }
    private record PictureData(long id, long owner, String title, byte[] bytes) { }
    private record RelationData(long source, long target, String type, String description) { }

    public int restore(InputStream stream) throws Exception {
        Map<String, byte[]> zip = unpack(stream);
        Document doc = factory().newDocumentBuilder().parse(new ByteArrayInputStream(zip.get("manifest.xml")));
        Element root = doc.getDocumentElement();
        if (!root.getTagName().equals("noctalith") || !root.getAttribute("version").equals("1")) throw new IllegalArgumentException("対応していないバックアップ形式です。");
        if (children(root, "characters").size() != 1 || children(root, "relations").size() != 1) throw new IllegalArgumentException("バックアップの構成が不正です。");
        Map<Long, CharacterData> cs = new LinkedHashMap<>(); Map<Long, PictureData> ps = new LinkedHashMap<>(); List<RelationData> rs = new ArrayList<>();
        for (Element ce : children(children(root, "characters").get(0), "character")) {
            long key = id(ce.getAttribute("id")); OriginalCharacter c = new OriginalCharacter(); Map<String, String> fields = new HashMap<>();
            for (var field : ProfileFields.limits().entrySet()) {
                List<Element> values = children(ce, field.getKey());
                if (values.size() != 1) throw new IllegalArgumentException("プロフィールが不足または重複しています。");
                fields.put(field.getKey(), limited(values.get(0).getTextContent(), field.getValue()));
            }
            ProfileFields.read(fields, c); ProfileFields.validate(c); c.setName(c.getName().strip());
            if (!Set.of("true", "false").contains(ce.getAttribute("favorite"))) throw new IllegalArgumentException("お気に入りの値が不正です。");
            c.setFavorite(Boolean.parseBoolean(ce.getAttribute("favorite")));
            if (children(ce, "tags").size() != 1) throw new IllegalArgumentException("タグの構成が不正です。");
            Set<String> tags = new LinkedHashSet<>();
            for (Element tag : children(children(ce, "tags").get(0), "tag")) {
                String value = limited(tag.getTextContent(), 30);
                if (value.isBlank() || !TagParser.parse(value).equals(Set.of(value))) throw new IllegalArgumentException("タグが不正です。");
                tags.add(value);
            }
            if (tags.size() > 20) throw new IllegalArgumentException("タグが多すぎます。");
            c.replaceTags(tags);
            Long cover = ce.hasAttribute("cover") ? id(ce.getAttribute("cover")) : null;
            if (cs.putIfAbsent(key, new CharacterData(key, c, cover)) != null || cs.size() > 5000) throw new IllegalArgumentException("キャラクターIDが重複、または件数上限超過です。");
            List<Element> items = children(ce, "picture");
            if (items.size() > GalleryService.MAX_IMAGES) throw new IllegalArgumentException("画像の枚数が上限を超えています。");
            for (Element pe : items) {
                long pictureId = id(pe.getAttribute("id")); String title = limited(pe.getAttribute("title"), 100);
                byte[] bytes = zip.remove("images/" + pictureId + ".png");
                if (bytes == null || ps.containsKey(pictureId)) throw new IllegalArgumentException("画像が不足または重複しています。");
                ps.put(pictureId, new PictureData(pictureId, key, title, gallery.normalize(bytes)));
            }
        }
        if (zip.size() != 1) throw new IllegalArgumentException("バックアップに未参照の画像があります。");
        for (CharacterData c : cs.values()) {
            List<PictureData> owned = ps.values().stream().filter(p -> p.owner == c.id).toList();
            if ((!owned.isEmpty() && c.cover == null) || (c.cover != null && (!ps.containsKey(c.cover) || ps.get(c.cover).owner != c.id)))
                throw new IllegalArgumentException("代表画像の参照が不正です。");
        }
        for (Element re : children(children(root, "relations").get(0), "relation")) {
            long source = id(re.getAttribute("source")); long target = id(re.getAttribute("target"));
            String type = limited(re.getAttribute("type"), 60); String description = limited(re.getTextContent(), 2000);
            if (source == target || !cs.containsKey(source) || !cs.containsKey(target) || type.isBlank() || rs.size() >= 20000) throw new IllegalArgumentException("関係の参照が不正です。");
            rs.add(new RelationData(source, target, type, description));
        }
        synchronized (gallery) {
            Files.createDirectories(gallery.directory()); List<Path> createdFiles = new ArrayList<>();
            try {
                transactions.executeWithoutResult(status -> {
                    Map<Long, OriginalCharacter> mapped = new HashMap<>(); Map<Long, Long> imageIds = new HashMap<>();
                    for (CharacterData c : cs.values()) mapped.put(c.id, characters.saveAndFlush(c.value));
                    for (PictureData p : ps.values()) {
                        String name = UUID.randomUUID() + ".png"; Path path = gallery.path(name); createdFiles.add(path);
                        try { Files.write(path, p.bytes, StandardOpenOption.CREATE_NEW); } catch (IOException e) { throw new UncheckedIOException(e); }
                        CharacterPicture picture = new CharacterPicture(); picture.setCharacter(mapped.get(p.owner)); picture.setTitle(p.title); picture.setStorageName(name);
                        imageIds.put(p.id, pictures.saveAndFlush(picture).getId());
                    }
                    for (CharacterData c : cs.values()) {
                        OriginalCharacter saved = mapped.get(c.id); saved.setCoverImageId(c.cover == null ? null : imageIds.get(c.cover)); saved.setImageRegistered(c.cover != null); characters.save(saved);
                    }
                    for (RelationData r : rs) {
                        CharacterRelation relation = new CharacterRelation(); relation.setSource(mapped.get(r.source)); relation.setTarget(mapped.get(r.target));
                        relation.setRelationType(r.type); relation.setDescription(r.description); relations.save(relation);
                    }
                });
            } catch (RuntimeException failure) {
                for (Path path : createdFiles) try { Files.deleteIfExists(path); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
        return cs.size();
    }
}
