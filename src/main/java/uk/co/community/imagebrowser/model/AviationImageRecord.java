package uk.co.community.imagebrowser.model;

/**
 * One row imported from the AVIATION sheet. {@code fileName} is the spreadsheet's
 * short form (e.g. "3.jpg"); the physical file on disk is "{folder}-{fileName}"
 * (e.g. data/input/AVIATION/4/4-3.jpg), so building output paths requires
 * reattaching the folder prefix:
 *   data/output/{category}/{folder}/FULL-{folder}-{fileName}
 *   data/output/{category}/{folder}/THUMB-{folder}-{fileName}
 */
public record AviationImageRecord(
        String category,
        String description,
        String folder,
        String fileName
) {}
