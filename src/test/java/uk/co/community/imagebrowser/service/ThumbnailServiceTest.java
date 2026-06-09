package uk.co.community.imagebrowser.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ThumbnailService")
class ThumbnailServiceTest {

    private ThumbnailService service;

    @BeforeEach
    void setUp() {
        service = new ThumbnailService(128, 0.75f);
    }

    // ---------------------------------------------------------------
    // isVectorFormat
    // ---------------------------------------------------------------

    @Test
    @DisplayName("identifies EMF as vector format")
    void isVectorFormat_WhenEmf_ShouldReturnTrue() {
        assertThat(service.isVectorFormat("emf")).isTrue();
    }

    @Test
    @DisplayName("identifies WMF as vector format")
    void isVectorFormat_WhenWmf_ShouldReturnTrue() {
        assertThat(service.isVectorFormat("wmf")).isTrue();
    }

    @Test
    @DisplayName("identifies SVG as vector format")
    void isVectorFormat_WhenSvg_ShouldReturnTrue() {
        assertThat(service.isVectorFormat("svg")).isTrue();
    }

    @Test
    @DisplayName("PNG is not a vector format")
    void isVectorFormat_WhenPng_ShouldReturnFalse() {
        assertThat(service.isVectorFormat("png")).isFalse();
    }

    @Test
    @DisplayName("JPEG is not a vector format")
    void isVectorFormat_WhenJpeg_ShouldReturnFalse() {
        assertThat(service.isVectorFormat("jpeg")).isFalse();
    }

    @Test
    @DisplayName("null extension is not a vector format")
    void isVectorFormat_WhenNull_ShouldReturnFalse() {
        assertThat(service.isVectorFormat(null)).isFalse();
    }

    @Test
    @DisplayName("extension matching is case-insensitive")
    void isVectorFormat_WhenExtensionUppercase_ShouldMatchCaseInsensitively() {
        assertThat(service.isVectorFormat("EMF")).isTrue();
        assertThat(service.isVectorFormat("WMF")).isTrue();
    }

    // ---------------------------------------------------------------
    // scale
    // ---------------------------------------------------------------

    @Test
    @DisplayName("scales image down so longest edge equals maxPx")
    void scale_WhenLandscapeLargerThanMax_ShouldScaleLongestEdgeToMax() {
        BufferedImage src = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        BufferedImage dst = service.scale(src, 128);
        assertThat(dst.getWidth()).isEqualTo(128);
        assertThat(dst.getHeight()).isEqualTo(64);
    }

    @Test
    @DisplayName("scales portrait image so height equals maxPx")
    void scale_WhenPortraitLargerThanMax_ShouldScaleHeightToMax() {
        BufferedImage src = new BufferedImage(100, 400, BufferedImage.TYPE_INT_RGB);
        BufferedImage dst = service.scale(src, 128);
        assertThat(dst.getHeight()).isEqualTo(128);
        assertThat(dst.getWidth()).isEqualTo(32);
    }

    @Test
    @DisplayName("does not upscale images smaller than maxPx")
    void scale_WhenImageSmallerThanMax_ShouldNotUpscale() {
        BufferedImage src = new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB);
        BufferedImage dst = service.scale(src, 128);
        // No upscaling — original returned
        assertThat(dst.getWidth()).isEqualTo(64);
        assertThat(dst.getHeight()).isEqualTo(32);
    }

    @Test
    @DisplayName("handles square image")
    void scale_WhenSquareImage_ShouldScaleBothDimensionsToMax() {
        BufferedImage src = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        BufferedImage dst = service.scale(src, 128);
        assertThat(dst.getWidth()).isEqualTo(128);
        assertThat(dst.getHeight()).isEqualTo(128);
    }

    // ---------------------------------------------------------------
    // createThumbnail
    // ---------------------------------------------------------------

    @Test
    @DisplayName("creates non-empty thumbnail bytes from valid PNG")
    void createThumbnail_WhenValidPng_ShouldReturnNonEmptyBytes() throws IOException {
        byte[] pngBytes = createTestPng(300, 200);
        byte[] thumb    = service.createThumbnail(pngBytes);
        assertThat(thumb).isNotEmpty();
    }

    @Test
    @DisplayName("returns empty array for invalid/undecodable bytes")
    void createThumbnail_WhenBytesInvalid_ShouldReturnEmptyArray() throws IOException {
        byte[] garbage = new byte[]{0x00, 0x01, 0x02, 0x03};
        byte[] thumb   = service.createThumbnail(garbage);
        assertThat(thumb).isEmpty();
    }

    @Test
    @DisplayName("returns empty array for empty input")
    void createThumbnail_WhenInputEmpty_ShouldReturnEmptyArray() throws IOException {
        byte[] thumb = service.createThumbnail(new byte[0]);
        assertThat(thumb).isEmpty();
    }

    // ---------------------------------------------------------------
    // encodeAsJpeg
    // ---------------------------------------------------------------

    @Test
    @DisplayName("encodes BufferedImage as JPEG bytes")
    void encodeAsJpeg_WhenGivenImage_ShouldReturnJpegBytes() throws IOException {
        BufferedImage img  = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        byte[]        jpeg = service.encodeAsJpeg(img, 0.75f);
        assertThat(jpeg).isNotEmpty();
        // JPEG magic bytes: FF D8
        assertThat(jpeg[0] & 0xFF).isEqualTo(0xFF);
        assertThat(jpeg[1] & 0xFF).isEqualTo(0xD8);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private byte[] createTestPng(int width, int height) throws IOException {
        var img  = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
