package uk.co.community.imagebrowser.model;

/**
 * One row read back from the "images" table for display — enough to locate
 * the thumbnail file on disk (data/output/{category}/{folder}/THUMB-{fileName})
 * and render a search result card.
 */
public record AviationImageSummary(
        long id,
        String category,
        String folder,
        String fileName,
        String description
) {}
