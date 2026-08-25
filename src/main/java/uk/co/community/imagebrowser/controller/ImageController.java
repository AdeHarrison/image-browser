package uk.co.community.imagebrowser.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import uk.co.community.imagebrowser.model.AviationImageSummary;
import uk.co.community.imagebrowser.service.AviationImageService;

import java.util.List;
import java.util.Optional;

@Controller
public class ImageController {

    private static final String CACHE_HEADER = "public, max-age=86400";

    // Sanity cap on the client-supplied browse page size (fits-the-screen calc), so a
    // crafted request can't force an enormous single-page query.
    private static final int MAX_BROWSE_PAGE_SIZE = 300;

    private final AviationImageService imageService;

    public ImageController(AviationImageService imageService) {
        this.imageService = imageService;
    }

    // ---------------------------------------------------------------
    // Main page
    // ---------------------------------------------------------------

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("totalImages", imageService.count());
        return "index";
    }

    // ---------------------------------------------------------------
    // Search — returns HTML fragment for HTMX. Partial, case-insensitive
    // match against description; results show thumbnail + folder.
    // ---------------------------------------------------------------

    @GetMapping("/search")
    @ResponseBody
    public String search(@RequestParam(defaultValue = "") String q) {
        List<AviationImageSummary> results = imageService.search(q);

        // Clears out any leftover browse-mode pagination bar from a previous "*" search.
        String clearBrowseNav = "<div id=\"browse-nav\" hx-swap-oob=\"true\"></div>";

        if (results.isEmpty()) {
            return "<p class='no-results'>No images found.</p>" + clearBrowseNav;
        }

        return renderCards(results) + clearBrowseNav;
    }

    // ---------------------------------------------------------------
    // Browse all — "*" bypasses the search box's 3-character minimum and
    // pages through every image, newest-inserted-last (id order).
    // ---------------------------------------------------------------

    @GetMapping("/browse")
    @ResponseBody
    public String browse(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(required = false) Integer size) {
        int  pageSize = clampPageSize(size);
        long total    = imageService.count();
        int  lastPage = total == 0 ? 0 : (int) ((total - 1) / pageSize);

        page = Math.max(0, Math.min(page, lastPage));

        List<AviationImageSummary> results = imageService.browse(page, pageSize);
        String grid = results.isEmpty()
                ? "<p class='no-results'>No images found.</p>"
                : renderCards(results);

        return grid + renderBrowseNav(page, lastPage);
    }

    private int clampPageSize(Integer requested) {
        if (requested == null) {
            return imageService.browsePageSize();
        }
        return Math.max(1, Math.min(requested, MAX_BROWSE_PAGE_SIZE));
    }

    // ---------------------------------------------------------------
    // Fragment rendering helpers
    // ---------------------------------------------------------------

    private String renderCards(List<AviationImageSummary> results) {
        var sb = new StringBuilder();
        for (AviationImageSummary r : results) {
            String description = escapeHtml(r.description());
            sb.append("""
                <div class="card" data-id="%d" data-description="%s"
                     tabindex="0" role="button" aria-label="%s">
                    <img src="/thumbnail/%d"
                         alt="%s"
                         loading="lazy"
                         width="128" height="128">
                    <span class="card-sheet">Folder: %s</span>
                </div>
            """.formatted(r.id(), description, description, r.id(), description, escapeHtml(r.folder())));
        }
        return sb.toString();
    }

    private String renderBrowseNav(int page, int lastPage) {
        boolean atFirst = page <= 0;
        boolean atLast  = page >= lastPage;

        return """
            <div id="browse-nav" class="browse-nav" hx-swap-oob="true">
                <button type="button" hx-get="/browse?page=0" hx-target="#grid" %s>First</button>
                <button type="button" hx-get="/browse?page=%d" hx-target="#grid" %s>Previous</button>
                <span class="browse-page">Page %d of %d</span>
                <button type="button" hx-get="/browse?page=%d" hx-target="#grid" %s>Next</button>
                <button type="button" hx-get="/browse?page=%d" hx-target="#grid" %s>Last</button>
            </div>
            """.formatted(
                atFirst ? "disabled" : "",
                Math.max(0, page - 1), atFirst ? "disabled" : "",
                page + 1, lastPage + 1,
                Math.min(lastPage, page + 1), atLast ? "disabled" : "",
                lastPage, atLast ? "disabled" : "");
    }

    // ---------------------------------------------------------------
    // Binary endpoints
    // ---------------------------------------------------------------

    @GetMapping("/thumbnail/{id}")
    public ResponseEntity<byte[]> thumbnail(@PathVariable long id) {
        return imageService.getThumbnail(id)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, CACHE_HEADER)
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(bytes))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/image/{id}")
    public ResponseEntity<byte[]> fullImage(@PathVariable long id) {
        Optional<AviationImageSummary> summary = imageService.findById(id);
        if (summary.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return imageService.getFullImage(id)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, CACHE_HEADER)
                        .contentType(contentTypeFor(summary.get().fileName()))
                        .body(bytes))
                .orElse(ResponseEntity.notFound().build());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private MediaType contentTypeFor(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String ext = dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
        return switch (ext) {
            case "png"         -> MediaType.IMAGE_PNG;
            case "gif"         -> MediaType.IMAGE_GIF;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            default            -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
