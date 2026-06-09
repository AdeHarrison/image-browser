package uk.co.community.imagebrowser.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import uk.co.community.imagebrowser.model.ImageSummary;
import uk.co.community.imagebrowser.service.ImageCacheService;
import uk.co.community.imagebrowser.service.ImageSearchService;

import java.util.List;

@Controller
public class ImageController {

    private static final String CACHE_HEADER = "public, max-age=86400";

    private final ImageSearchService searchService;
    private final ImageCacheService  cacheService;

    public ImageController(ImageSearchService searchService,
                           ImageCacheService  cacheService) {
        this.searchService = searchService;
        this.cacheService  = cacheService;
    }

    // ---------------------------------------------------------------
    // Main page
    // ---------------------------------------------------------------

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("totalImages", searchService.count());
        return "index";
    }

    // ---------------------------------------------------------------
    // Search — returns HTML fragment for HTMX
    // ---------------------------------------------------------------

    @GetMapping("/search")
    @ResponseBody
    public String search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page) {

        List<ImageSummary> results = searchService.search(q, page);

        if (results.isEmpty()) {
            return "<p class='no-results'>No images found.</p>";
        }

        var sb = new StringBuilder();
        for (ImageSummary r : results) {
            sb.append("""
                <div class="card"
                     hx-get="/image/%d/modal"
                     hx-target="#modal-content"
                     hx-swap="innerHTML"
                     hx-on:htmx:after-swap="openModal()">
                    <img src="/thumbnail/%d"
                         alt="%s"
                         loading="lazy"
                         width="128" height="128">
                    <span class="card-name">%s</span>
                    <span class="card-sheet">%s</span>
                </div>
            """.formatted(r.id(), r.id(),
                          escapeHtml(r.filename()),
                          escapeHtml(truncate(r.filename(), 20)),
                          escapeHtml(r.sheetName())));
        }

        // Infinite scroll trigger if there may be more results
        if (results.size() == 30) {
            sb.append("""
                <div class="load-more"
                     hx-get="/search?q=%s&page=%d"
                     hx-trigger="revealed"
                     hx-swap="outerHTML">
                    <span>Loading more...</span>
                </div>
            """.formatted(escapeHtml(q), page + 1));
        }

        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Modal content — HTMX fragment
    // ---------------------------------------------------------------

    @GetMapping("/image/{id}/modal")
    @ResponseBody
    public String modal(@PathVariable long id) {
        return searchService.findById(id).map(r -> """
            <button class="modal-close" onclick="closeModal()" aria-label="Close">✕</button>
            <img src="/thumbnail/%d" alt="%s" class="modal-thumb">
            <div class="meta-grid">
                <span class="meta-label">Filename</span>  <span>%s</span>
                <span class="meta-label">Sheet</span>     <span>%s</span>
                <span class="meta-label">Cell</span>      <span>%s</span>
                <span class="meta-label">Tags</span>      <span>%s</span>
                <span class="meta-label">Description</span><span>%s</span>
            </div>
            <button class="btn-primary view-full-btn"
                    hx-get="/image/%d/viewer"
                    hx-target="#viewer-container"
                    hx-swap="innerHTML"
                    onclick="closeModal()">
                View Full Image ▶
            </button>
        """.formatted(
                r.id(), escapeHtml(r.filename()),
                escapeHtml(r.filename()),
                escapeHtml(r.sheetName()),
                escapeHtml(r.cellRef()),
                escapeHtml(r.tags()),
                escapeHtml(r.description()),
                r.id())
        ).orElse("<p>Image not found.</p>");
    }

    // ---------------------------------------------------------------
    // Full image viewer — HTMX fragment
    // ---------------------------------------------------------------

    @GetMapping("/image/{id}/viewer")
    @ResponseBody
    public String viewer(@PathVariable long id) {
        var nav = searchService.getNavigationContext(id);
        return searchService.findById(id).map(r -> """
            <div id="viewer">
                <img src="/image/%d/bytes"
                     alt="%s"
                     class="full-image">
                <div class="nav-bar">
                    <button %s
                            hx-get="/image/%d/viewer"
                            hx-target="#viewer"
                            hx-swap="outerHTML"
                            title="First">⏮ First</button>
                    <button %s
                            hx-get="/image/%d/viewer"
                            hx-target="#viewer"
                            hx-swap="outerHTML"
                            title="Previous">◀ Prev</button>
                    <span class="nav-label">%s</span>
                    <button %s
                            hx-get="/image/%d/viewer"
                            hx-target="#viewer"
                            hx-swap="outerHTML"
                            title="Next">Next ▶</button>
                    <button %s
                            hx-get="/image/%d/viewer"
                            hx-target="#viewer"
                            hx-swap="outerHTML"
                            title="Last">Last ⏭</button>
                </div>
                <p class="image-caption">%s — %s</p>
            </div>
        """.formatted(
                r.id(), escapeHtml(r.filename()),
                nav.isFirst() ? "disabled" : "", nav.firstId() != null ? nav.firstId() : id,
                !nav.hasPrev() ? "disabled" : "", nav.prevId() != null ? nav.prevId() : id,
                escapeHtml(r.filename()),
                !nav.hasNext() ? "disabled" : "", nav.nextId() != null ? nav.nextId() : id,
                nav.isLast()  ? "disabled" : "", nav.lastId() != null ? nav.lastId() : id,
                escapeHtml(r.filename()),
                escapeHtml(r.sheetName()))
        ).orElse("<p>Image not found.</p>");
    }

    // ---------------------------------------------------------------
    // Binary endpoints
    // ---------------------------------------------------------------

    @GetMapping("/thumbnail/{id}")
    public ResponseEntity<byte[]> thumbnail(@PathVariable long id) {
        return cacheService.getThumbnail(id)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, CACHE_HEADER)
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(bytes))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/image/{id}/bytes")
    public ResponseEntity<byte[]> fullImage(@PathVariable long id) {
        return cacheService.getFullImage(id)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, CACHE_HEADER)
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(bytes))
                .orElse(ResponseEntity.notFound().build());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
