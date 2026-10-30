package com.sep490.backend.modules.learning.importing.web;

import static com.sep490.backend.modules.learning.importing.web.WebsiteImportModel.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

/** Deterministic extractor: no summarisation, translation, question generation, or AI rewriting. */
@Component
public class WebsiteCourseExtractor {
  private static final Pattern BACKGROUND = Pattern.compile("background(?:-image)?\\s*:\\s*url\\(['\"]?([^)'\"]+)", Pattern.CASE_INSENSITIVE);
  private static final Set<String> LEAF = Set.of("h1","h2","h3","h4","h5","h6","p","ul","ol","blockquote","pre","table","figure","img","audio","video","iframe");
  private static final Safelist SAFE_HTML = Safelist.relaxed()
      .addTags("figure","figcaption","audio","video","source","iframe","details","summary","mark","u","s","thead","tbody","tfoot")
      .addAttributes(":all", "lang", "dir")
      .addAttributes("img", "src", "srcset", "sizes", "alt", "width", "height", "loading")
      .addAttributes("audio", "src", "controls", "preload")
      .addAttributes("video", "src", "controls", "preload", "poster", "width", "height")
      .addAttributes("source", "src", "srcset", "type", "media")
      .addAttributes("iframe", "src", "title", "width", "height", "allow", "allowfullscreen")
      .addAttributes("td", "colspan", "rowspan", "scope")
      .addAttributes("th", "colspan", "rowspan", "scope")
      .addProtocols("img", "src", "http", "https")
      .addProtocols("audio", "src", "http", "https")
      .addProtocols("video", "src", "http", "https")
      .addProtocols("source", "src", "http", "https")
      .addProtocols("iframe", "src", "http", "https");

  private final SafeWebFetcher fetcher;
  public WebsiteCourseExtractor(SafeWebFetcher fetcher) { this.fetcher = fetcher; }

  public Extraction extractUrl(String sourceUrl, String mode) {
    SafeWebFetcher.Fetched home = fetcher.fetch(sourceUrl);
    Document document = Jsoup.parse(new String(home.body(), StandardCharsets.UTF_8), home.finalUri().toString());
    Metadata metadata = metadata(document, home.finalUri().toString());
    if ("SINGLE_LESSON".equals(mode)) {
      LessonDraft lesson = lesson(document, home.finalUri().toString(), "lesson-1", true);
      return new Extraction(metadata.title(), metadata.author(), metadata.license(), metadata.attribution(),
          List.of(new ModuleDraft("module-1", moduleTitle(document), true, List.of(lesson))), List.of());
    }

    List<TocEntry> entries = toc(document, home.finalUri());
    if (entries.isEmpty()) throw new IllegalArgumentException("Không tìm thấy mục lục hợp lệ. Hãy import một bài hoặc tải file XHTML/XML chính thức.");
    Map<String,List<LessonDraft>> grouped = new LinkedHashMap<>();
    List<Diagnostic> diagnostics = new ArrayList<>();
    int index = 0;
    for (TocEntry entry : entries) {
      try {
        var fetched = fetcher.fetch(entry.url());
        var page = Jsoup.parse(new String(fetched.body(), StandardCharsets.UTF_8), fetched.finalUri().toString());
        grouped.computeIfAbsent(entry.module(), ignored -> new ArrayList<>())
            .add(lesson(page, fetched.finalUri().toString(), "lesson-" + (++index), true));
      } catch (RuntimeException failure) {
        diagnostics.add(new Diagnostic("ERROR", "PAGE_LOAD_FAILED", failure.getMessage(), entry.url()));
      }
    }
    if (grouped.isEmpty()) throw new IllegalArgumentException("Không có trang nội dung nào tải thành công.");
    List<ModuleDraft> modules = new ArrayList<>(); int moduleIndex = 0;
    for (var group : grouped.entrySet()) modules.add(new ModuleDraft("module-" + (++moduleIndex), group.getKey(), true, group.getValue()));
    return new Extraction(metadata.title(), metadata.author(), metadata.license(), metadata.attribution(), modules, diagnostics);
  }

  public Extraction extractFile(byte[] bytes, String filename) {
    if (bytes == null || bytes.length == 0 || bytes.length > 10 * 1024 * 1024)
      throw new IllegalArgumentException("File XHTML/XML phải có dung lượng từ 1 byte đến 10 MiB.");
    String lower = Objects.toString(filename, "").toLowerCase(Locale.ROOT);
    if (!(lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".xml")))
      throw new IllegalArgumentException("Chỉ hỗ trợ file .xhtml, .html hoặc .xml.");
    String source = new String(bytes, StandardCharsets.UTF_8);
    if (lower.endsWith(".xml") && source.contains("<item")) return wordpressXml(source, filename);
    Document document = Jsoup.parse(source, "", Parser.htmlParser());
    Metadata metadata = metadata(document, null);
    Elements chapters = document.select("section.chapter, article.chapter, div.chapter, [data-type=chapter]");
    if (chapters.isEmpty()) {
      LessonDraft lesson = lesson(document, null, "lesson-1", false);
      return new Extraction(metadata.title(), metadata.author(), metadata.license(), metadata.attribution(),
          List.of(new ModuleDraft("module-1", "Nội dung nhập", true, List.of(lesson))), List.of());
    }
    Map<String,List<LessonDraft>> grouped = new LinkedHashMap<>(); int index = 0;
    for (Element chapter : chapters) {
      String module = nearestPartTitle(chapter);
      Document shell = Document.createShell(""); shell.body().appendChild(chapter.clone());
      grouped.computeIfAbsent(module, ignored -> new ArrayList<>())
          .add(lesson(shell, null, "lesson-" + (++index), false));
    }
    return new Extraction(metadata.title(), metadata.author(), metadata.license(), metadata.attribution(), modules(grouped), List.of());
  }

  Extraction extractHtml(String html, String sourceUrl) {
    Document document = Jsoup.parse(html, sourceUrl == null ? "" : sourceUrl);
    Metadata metadata = metadata(document, sourceUrl);
    return new Extraction(metadata.title(), metadata.author(), metadata.license(), metadata.attribution(),
        List.of(new ModuleDraft("module-1", moduleTitle(document), true,
            List.of(lesson(document, sourceUrl, "lesson-1", false)))), List.of());
  }

  private Extraction wordpressXml(String source, String filename) {
    Document xml = Jsoup.parse(source, "", Parser.xmlParser());
    Map<String,String> parts = new LinkedHashMap<>();
    for (Element item : xml.select("item")) if ("part".equals(tag(item,"wp:post_type")))
      parts.put(tag(item,"wp:post_id"), defaultText(tag(item,"title"), "Nội dung nhập"));
    Map<String,List<LessonDraft>> grouped = new LinkedHashMap<>(); int index = 0;
    for (Element item : xml.select("item")) {
      String type = tag(item,"wp:post_type");
      if (!("chapter".equals(type) || "front-matter".equals(type) || "back-matter".equals(type))) continue;
      String title = defaultText(tag(item,"title"), "Bài nhập " + (index + 1));
      String html = tag(item,"content:encoded");
      Document page = Jsoup.parseBodyFragment(html, tag(item,"link"));
      page.title(title); page.body().prependElement("h1").text(title);
      String module = parts.getOrDefault(tag(item,"wp:post_parent"), type.equals("chapter") ? "Nội dung chính" : "Phần đầu/cuối sách");
      grouped.computeIfAbsent(module, ignored -> new ArrayList<>())
          .add(lesson(page, tag(item,"link"), "lesson-" + (++index), false));
    }
    if (grouped.isEmpty()) throw new IllegalArgumentException("File Pressbooks XML không chứa chapter hợp lệ.");
    String title = defaultText(tag(xml,"title"), filename.replaceFirst("\\.[^.]+$", ""));
    String author = tag(xml,"dc:creator");
    return new Extraction(title, author, null, author == null ? null : title + " — " + author, modules(grouped), List.of());
  }

  private LessonDraft lesson(Document document, String sourceUrl, String key, boolean probeMedia) {
    String title = firstText(document, "h1.entry-title", "article h1", "main h1", "h1");
    if (title == null) title = defaultText(document.title(), "Bài học nhập");
    Element root = first(document, ".entry-content", ".chapter-content", "article .entry-content", "main article", "article", "main", "body");
    List<BlockDraft> blocks = new ArrayList<>();
    for (Node node : root.childNodes()) visit(node, sourceUrl, blocks, probeMedia);
    List<BlockDraft> ordered = new ArrayList<>();
    for (int i = 0; i < blocks.size(); i++) {
      BlockDraft b = blocks.get(i);
      ordered.add(new BlockDraft(b.key(), b.type(), i + 1, b.title(), b.html(), b.text(), b.mediaUrl(), b.originalUrl(),
          b.sourcePageUrl(), b.mimeType(), b.width(), b.height(), b.altText(), b.caption(), b.attribution(), b.license(), b.importStatus(), b.reviewRequired()));
    }
    if (ordered.isEmpty()) ordered.add(textBlock(key + "-empty", 1, "<p>Không tìm thấy nội dung trong vùng bài học.</p>", sourceUrl, true));
    return new LessonDraft(key, title, sourceUrl, true, ordered);
  }

  private void visit(Node node, String sourceUrl, List<BlockDraft> blocks, boolean probeMedia) {
    if (node instanceof TextNode text) {
      if (!text.text().isBlank()) blocks.add(textBlock(blockKey(blocks), blocks.size()+1, "<p>" + Entities.escape(text.text()) + "</p>", sourceUrl, false));
      return;
    }
    if (!(node instanceof Element element)) return;
    String tag = element.normalName();
    if (Set.of("script","style","noscript","nav","header","footer","form").contains(tag)
        || element.classNames().stream().anyMatch(c -> c.matches("(?i).*(navigation|breadcrumb|sidebar|share|advert|footer).*"))) return;
    if (tag.equals("figure")) { figure(element, sourceUrl, blocks, probeMedia); return; }
    if (tag.equals("img")) { image(element, null, sourceUrl, blocks, probeMedia); return; }
    if (tag.equals("audio")) { media("AUDIO", element, sourceUrl, blocks, probeMedia, false); return; }
    if (tag.equals("video") || tag.equals("iframe")) { media("VIDEO", element, sourceUrl, blocks, probeMedia, tag.equals("iframe")); return; }
    if (tag.equals("table")) { blocks.add(htmlBlock("TABLE", element, sourceUrl, blocks, false)); return; }
    if (tag.matches("h[1-6]")) { blocks.add(htmlBlock("HEADING", element, sourceUrl, blocks, false)); return; }
    if (Set.of("p","ul","ol","blockquote","pre").contains(tag) && !hasDirectMedia(element)) {
      blocks.add(htmlBlock(tag.equals("p") ? "PARAGRAPH" : "RICH_TEXT", element, sourceUrl, blocks, false)); return;
    }
    Matcher background = BACKGROUND.matcher(element.attr("style"));
    if (background.find()) {
      String url = absolute(sourceUrl, background.group(1));
      addMediaBlock("IMAGE", url, url, element, null, sourceUrl, blocks, probeMedia, false);
    }
    boolean descended = false;
    for (Node child : element.childNodes()) {
      if (child instanceof Element ce && (LEAF.contains(ce.normalName()) || hasRecognized(ce))) descended = true;
      visit(child, sourceUrl, blocks, probeMedia);
    }
    if (!descended && !element.ownText().isBlank())
      blocks.add(textBlock(blockKey(blocks), blocks.size()+1, "<p>" + Entities.escape(element.ownText()) + "</p>", sourceUrl, false));
  }

  private void figure(Element figure, String sourceUrl, List<BlockDraft> blocks, boolean probeMedia) {
    String caption = Optional.ofNullable(figure.selectFirst("figcaption")).map(Element::text).orElse(null);
    Elements images = figure.select("img");
    if (!images.isEmpty()) for (Element image : images) image(image, caption, sourceUrl, blocks, probeMedia);
    else {
      Element media = figure.selectFirst("audio,video,iframe");
      if (media != null) media(media.normalName().equals("audio") ? "AUDIO" : "VIDEO", media, sourceUrl, blocks, probeMedia, media.normalName().equals("iframe"));
      else blocks.add(htmlBlock("RICH_TEXT", figure, sourceUrl, blocks, true));
    }
  }

  private void image(Element image, String caption, String sourceUrl, List<BlockDraft> blocks, boolean probeMedia) {
    String original = firstNonBlank(image.attr("data-original"), image.attr("data-src"), image.attr("src"));
    String best = bestSrcset(firstNonBlank(image.attr("data-srcset"), image.attr("srcset")));
    if (best == null && image.parent() != null && image.parent().normalName().equals("picture")) {
      for (Element source : image.parent().select("source[srcset], source[data-srcset]")) {
        String candidate = bestSrcset(firstNonBlank(source.attr("data-srcset"), source.attr("srcset")));
        if (candidate != null) best = candidate;
      }
    }
    String url = absolute(sourceUrl, firstNonBlank(best, original));
    addMediaBlock("IMAGE", url, absolute(sourceUrl, original), image, caption, sourceUrl, blocks, probeMedia, false);
  }

  private void media(String type, Element element, String sourceUrl, List<BlockDraft> blocks, boolean probeMedia, boolean review) {
    Element source = element.selectFirst("source[src]");
    String raw = firstNonBlank(element.attr("src"), source == null ? null : source.attr("src"));
    String url = absolute(sourceUrl, raw);
    addMediaBlock(type, url, url, element, nearbyCaption(element), sourceUrl, blocks, probeMedia, review);
  }

  private void addMediaBlock(String type, String url, String original, Element element, String caption,
      String sourcePage, List<BlockDraft> blocks, boolean probeMedia, boolean review) {
    MediaProbe probe = url == null ? new MediaProbe("MISSING_URL", null, null)
        : probeMedia ? fetcher.probe(url) : new MediaProbe("PENDING_CHECK", null, null);
    String status = "AVAILABLE_EXTERNAL".equals(probe.status()) ? "PENDING_RIGHTS" : probe.status();
    boolean needsReview = review || !"AVAILABLE_EXTERNAL".equals(probe.status());
    blocks.add(new BlockDraft(blockKey(blocks), type, blocks.size()+1, null, null, null, url, original,
        sourcePage, probe.mimeType(), integer(element.attr("width")), integer(element.attr("height")),
        blankToNull(element.attr("alt")), caption, nearbyAttribution(element), null, status, needsReview));
  }

  private BlockDraft htmlBlock(String type, Element element, String sourceUrl, List<BlockDraft> blocks, boolean review) {
    String html = sanitizeImportedHtml(element.outerHtml(), sourceUrl);
    return new BlockDraft(blockKey(blocks), type, blocks.size()+1, element.text(), html, element.text(), null, null,
        sourceUrl, "text/html", null, null, null, null, null, null, review ? "NEEDS_REVIEW" : "READY", review);
  }

  private BlockDraft textBlock(String key, int order, String html, String sourceUrl, boolean review) {
    String safe = sanitizeImportedHtml(html, sourceUrl);
    return new BlockDraft(key, "PARAGRAPH", order, null, safe, Jsoup.parse(safe).text(), null, null, sourceUrl,
        "text/html", null, null, null, null, null, null, review ? "NEEDS_REVIEW" : "READY", review);
  }

  private List<TocEntry> toc(Document document, URI base) {
    LinkedHashMap<String,TocEntry> unique = new LinkedHashMap<>();
    for (Element link : document.select(".book-contents a[href], #content a[href], .toc a[href]")) {
      String href = absolute(base.toString(), link.attr("href"));
      if (href == null) continue;
      URI uri;
      try { uri = URI.create(href); } catch (Exception ignored) { continue; }
      String path = Objects.toString(uri.getPath(), "");
      if (!base.getHost().equalsIgnoreCase(uri.getHost()) || !(path.contains("/chapter/") || path.contains("/front-matter/") || path.contains("/back-matter/"))) continue;
      String clean = withoutFragment(uri).toString();
      unique.putIfAbsent(clean, new TocEntry(tocModule(link), defaultText(link.text(), clean), clean));
      if (unique.size() >= 100) break;
    }
    return new ArrayList<>(unique.values());
  }

  private static String tocModule(Element link) {
    Element part = link.closest(".part, li.part, section.part, [data-type=part]");
    if (part != null) {
      Element heading = directHeading(part);
      if (heading != null && !heading.text().isBlank()) return heading.text();
    }
    String path = link.absUrl("href");
    if (path.contains("front-matter")) return "Phần mở đầu";
    if (path.contains("back-matter")) return "Phần cuối sách";
    return "Nội dung chính";
  }

  private static Metadata metadata(Document document, String sourceUrl) {
    String title = firstNonBlank(attr(document,"meta[property=og:title]","content"), attr(document,"meta[name=citation_title]","content"), document.title(), firstText(document,"h1"));
    String author = firstNonBlank(attr(document,"meta[name=citation_author]","content"), attr(document,"meta[name=author]","content"), firstText(document,".book-author", ".author"));
    Element licenseLink = document.selectFirst("a[rel=license], a[href*=creativecommons.org/licenses]");
    String license = licenseLink == null ? null : firstNonBlank(licenseLink.text(), licenseLink.attr("href"));
    if (license == null) {
      String text = document.text(); Matcher match = Pattern.compile("(?i)CC\\s+BY(?:-[A-Z]+)*\\s+[0-9.]+|Creative Commons Attribution[^.]{0,80}").matcher(text);
      if (match.find()) license = match.group();
    }
    String attribution = title == null ? null : title + (author == null ? "" : " — " + author) + (sourceUrl == null ? "" : " — " + sourceUrl);
    return new Metadata(defaultText(title,"Khóa học nhập"), author, license, attribution);
  }

  static String sanitizeImportedHtml(String html, String base) {
    return Jsoup.clean(html, Objects.toString(base,""), SAFE_HTML, new Document.OutputSettings().prettyPrint(false));
  }
  private static boolean hasDirectMedia(Element e) { return e.children().stream().anyMatch(c -> Set.of("img","audio","video","iframe","figure","table").contains(c.normalName())); }
  private static boolean hasRecognized(Element e) { return e.selectFirst("h1,h2,h3,h4,h5,h6,p,ul,ol,table,figure,img,audio,video,iframe") != null; }
  private static String nearbyCaption(Element e) { Element c=e.parent()==null?null:e.parent().selectFirst("figcaption,.wp-caption-text,.caption"); return c==null?null:c.text(); }
  private static String nearbyAttribution(Element e) { Element c=e.parent()==null?null:e.parent().selectFirst(".attribution,.credit,.media-credit"); return c==null?null:c.text(); }
  private static String moduleTitle(Document d) { return defaultText(firstText(d,".part-title", ".book-header h1", "h1"), "Nội dung nhập"); }
  private static String nearestPartTitle(Element e) { Element p=e.closest("section.part,div.part,[data-type=part]"); Element h=p==null?null:directHeading(p); return h==null?"Nội dung nhập":defaultText(h.text(),"Nội dung nhập"); }
  private static Element directHeading(Element parent) { for(Element child:parent.children()) if(Set.of("h1","h2","h3","a").contains(child.normalName())) return child; return null; }
  private static List<ModuleDraft> modules(Map<String,List<LessonDraft>> grouped) { List<ModuleDraft> result=new ArrayList<>(); int i=0; for(var e:grouped.entrySet()) result.add(new ModuleDraft("module-"+(++i),e.getKey(),true,e.getValue())); return result; }
  private static Element first(Document d,String...selectors) { for(String s:selectors){Element e=d.selectFirst(s);if(e!=null)return e;} return d.body(); }
  private static String firstText(Element e,String...selectors) { for(String s:selectors){Element f=e.selectFirst(s);if(f!=null&&!f.text().isBlank())return f.text();} return null; }
  private static String attr(Document d,String selector,String attribute){Element e=d.selectFirst(selector);return e==null?null:blankToNull(e.attr(attribute));}
  private static String tag(Element e,String name){Elements found=e.getElementsByTag(name);return found.isEmpty()?null:blankToNull(found.first().text());}
  private static String blockKey(List<BlockDraft> blocks){return "block-"+(blocks.size()+1);}
  private static String defaultText(String value,String fallback){return value==null||value.isBlank()?fallback:value.trim();}
  private static String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
  private static String firstNonBlank(String...values){for(String v:values)if(v!=null&&!v.isBlank())return v.trim();return null;}
  private static Integer integer(String value){try{return value==null||value.isBlank()?null:Integer.valueOf(value.replaceAll("[^0-9]",""));}catch(Exception ignored){return null;}}
  private static String bestSrcset(String value){if(value==null||value.isBlank())return null;String best=null;int score=-1;for(String part:value.split(",")){String[] bits=part.trim().split("\\s+");int current=bits.length>1?integer(Objects.toString(bits[1],"0"))==null?0:integer(bits[1]):0;if(current>=score){score=current;best=bits[0];}}return best;}
  private static String absolute(String base,String raw){if(raw==null||raw.isBlank()||raw.startsWith("data:")||raw.startsWith("javascript:"))return null;try{URI resolved=base==null||base.isBlank()?URI.create(raw):URI.create(base).resolve(raw);return resolved.isAbsolute()?resolved.toString():null;}catch(Exception ignored){return null;}}
  private static URI withoutFragment(URI uri){try{return new URI(uri.getScheme(),uri.getAuthority(),uri.getPath(),uri.getQuery(),null);}catch(Exception e){return uri;}}

  private record TocEntry(String module,String title,String url) {}
  private record Metadata(String title,String author,String license,String attribution) {}
  public record Extraction(String title,String author,String license,String attribution,List<ModuleDraft> modules,List<Diagnostic> diagnostics) {}
}
