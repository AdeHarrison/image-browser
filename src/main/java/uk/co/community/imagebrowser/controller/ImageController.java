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

@Controller
public class ImageController {

    private static final String CACHE_HEADER = "public, max-age=86400";

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

        if (results.isEmpty()) {
            return "<p class='no-results'>No images found.</p>";
        }

        var sb = new StringBuilder();
        for (AviationImageSummary r : results) {
            sb.append("""
                <div class="card">
                    <img src="/thumbnail/%d"
                         alt="%s"
                         loading="lazy"
                         width="128" height="128">
                    <span class="card-sheet">Folder: %s</span>
                </div>
            """.formatted(r.id(), escapeHtml(r.description()), escapeHtml(r.folder())));
        }

        return sb.toString();
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
}
