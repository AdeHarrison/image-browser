package uk.co.community.imagebrowser.model;

/**
 * One row imported from the AVIATION sheet. {@code folder} is the zero-padded
 * "Folder/Page No" (e.g. "01") and {@code fileName} already includes that same
 * folder prefix as it appears on disk (e.g. "01-01.jpg" for
 * data/input/AVIATION/01/01-01.jpg) — both are used as-is when building output paths.
 */
public record AviationImageRecord(
        String category,
        String date,
        String description,
        String folder,
        String fileName
) {}
