package uk.co.community.imagebrowser.model;

/**
 * Lightweight metadata record — no BLOBs. Used for search results and grid display.
 */
public record ImageSummary(
        long   id,
        String filename,
        String sheetName,
        String cellRef,
        int    widthPx,
        int    heightPx,
        String tags,
        String description
) {}
