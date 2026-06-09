package uk.co.community.imagebrowser.model;

/**
 * Full image record including BLOBs — use sparingly, only when bytes are needed.
 */
public record ImageRecord(
        long   id,
        String filename,
        String sheetName,
        String cellRef,
        String mimeType,
        byte[] fullImage,
        byte[] thumbnail,
        int    widthPx,
        int    heightPx,
        String tags,
        String description
) {}
