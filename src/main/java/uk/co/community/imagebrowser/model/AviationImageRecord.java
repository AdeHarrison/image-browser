package uk.co.community.imagebrowser.model;

/**
 * One row imported from the AVIATION sheet. {@code folder} and {@code fileName}
 * together with {@code category} are enough to build the output paths:
 *   data/output/{category}/{folder}/FULL-{fileName}
 *   data/output/{category}/{folder}/THUMB-{fileName}
 */
public record AviationImageRecord(
        String category,
        String description,
        String folder,
        String fileName
) {}
