package uk.co.community.imagebrowser.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Generates JPEG thumbnails from raw image bytes.
 * Uses bilinear interpolation for quality downscaling.
 */
@Service
public class ThumbnailService {

    private final int    maxPx;
    private final float  jpegQuality;

    public ThumbnailService(
            @Value("${app.thumbnail.max-px:128}")      int   maxPx,
            @Value("${app.thumbnail.jpeg-quality:0.75}") float jpegQuality) {
        this.maxPx       = maxPx;
        this.jpegQuality = jpegQuality;
    }

    /**
     * Generate a JPEG thumbnail from raw image bytes.
     * Returns empty array if the image cannot be decoded (e.g. vector formats).
     */
    public byte[] createThumbnail(byte[] imageBytes) throws IOException {
        BufferedImage original = decode(imageBytes);
        if (original == null) {
            return new byte[0];
        }
        BufferedImage scaled = scale(original, maxPx);
        return encodeAsJpeg(scaled, jpegQuality);
    }

    /**
     * Returns true if the given file extension is a vector/unsupported format.
     */
    public boolean isVectorFormat(String extension) {
        if (extension == null) return false;
        return switch (extension.toLowerCase()) {
            case "emf", "wmf", "svg" -> true;
            default -> false;
        };
    }

    // ---------------------------------------------------------------
    // Package-private for testing
    // ---------------------------------------------------------------

    BufferedImage decode(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    BufferedImage scale(BufferedImage src, int maxEdgePx) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();

        if (srcW == 0 || srcH == 0) {
            return src;
        }

        double scale = (double) maxEdgePx / Math.max(srcW, srcH);

        // Already smaller than target — no upscaling needed
        if (scale >= 1.0) return src;

        int dstW = Math.max(1, (int) (srcW * scale));
        int dstH = Math.max(1, (int) (srcH * scale));

        // ARGB preserves transparency for PNG logos etc.
        BufferedImage dst = new BufferedImage(dstW, dstH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                               RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                               RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                               RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, dstW, dstH, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    byte[] encodeAsJpeg(BufferedImage img, float quality) throws IOException {
        // Convert ARGB → RGB for JPEG (no alpha channel in JPEG)
        BufferedImage rgb = new BufferedImage(
                img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.drawImage(img, 0, 0, null);
        } finally {
            g.dispose();
        }

        var baos   = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        var param  = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        writer.setOutput(ImageIO.createImageOutputStream(baos));
        writer.write(null, new IIOImage(rgb, null, null), param);
        writer.dispose();
        return baos.toByteArray();
    }
}
