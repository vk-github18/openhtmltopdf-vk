package com.openhtmltopdf.pdfbox.glyphlayout.awt;

import com.openhtmltopdf.pdfboxout.PDFontSupplier;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutProcessorAwt;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.Assert;
import org.junit.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Visual regression coverage for the bidi glyph-layout example.
 */
public class PdfBoxGlyphLayoutVisualTest {
    private static final String BIDI_HTML = "/html/GlyphLayoutExampleBidi.html";
    private static final String ARABIC_FONT = "/fonts/noto/NotoSansArabic-Regular.ttf";
    private static final String LATIN_FONT = "/fonts/arimo/Arimo-Regular.ttf";

    private BufferedImage renderBidiExample(boolean useGlyphLayoutProcessor) throws Exception {
        final byte[] pdfBytes;

        try (InputStream htmlStream = getClass().getResourceAsStream(BIDI_HTML)) {
            Assert.assertNotNull("Bidi HTML resource missing", htmlStream);
            String html = new String(toByteArray(htmlStream), StandardCharsets.UTF_8);

            try (PDDocument document = new PDDocument();
                 InputStream arabicFontStream = getClass().getResourceAsStream(ARABIC_FONT);
                 InputStream latinFontStream = getClass().getResourceAsStream(LATIN_FONT);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                Assert.assertNotNull("Arabic font resource missing", arabicFontStream);
                Assert.assertNotNull("Latin font resource missing", latinFontStream);

                GlyphLayoutProcessorAwt processor = new GlyphLayoutProcessorAwt();
                PDType0Font arabicFont = processor.loadFont(document, arabicFontStream);
                PDType0Font latinFont = processor.loadFont(document, latinFontStream);

                PdfRendererBuilder builder = new PdfRendererBuilder();
                if (useGlyphLayoutProcessor) {
                    builder.useGlyphLayoutProcessor(processor);
                }
                builder.toStream(output)
                        .usePDDocument(document)
                        .useFont(new PDFontSupplier(arabicFont), "Noto Sans Arabic")
                        .useFont(new PDFontSupplier(latinFont), "Arimo")
                        .withHtmlContent(html, "")
                        .run();
                pdfBytes = output.toByteArray();
            }
        }

        try (PDDocument renderedDocument = Loader.loadPDF(pdfBytes)) {
            Assert.assertEquals("The bidi fixture should fit on one page", 1, renderedDocument.getNumberOfPages());
            return new PDFRenderer(renderedDocument).renderImageWithDPI(0, 96);
        }
    }

    private byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int bytesRead;
        while ((bytesRead = input.read(data)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    private long countNonWhitePixels(BufferedImage image) {
        long count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != 0x00ffffff) {
                    count++;
                }
            }
        }
        return count;
    }

    private long countDifferentPixels(BufferedImage first, BufferedImage second) {
        Assert.assertEquals("Rendered images should have identical dimensions", first.getWidth(), second.getWidth());
        Assert.assertEquals("Rendered images should have identical dimensions", first.getHeight(), second.getHeight());

        long count = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    public void testBidiExampleHasVisibleOutputAndGlyphLayoutChangesRendering() throws Exception {
        BufferedImage withoutGlyphLayout = renderBidiExample(false);
        BufferedImage withGlyphLayout = renderBidiExample(true);

        Assert.assertTrue("Bidi example should render visible text without glyph layout",
                countNonWhitePixels(withoutGlyphLayout) > 500);
        Assert.assertTrue("Bidi example should render visible text with glyph layout",
                countNonWhitePixels(withGlyphLayout) > 500);
        Assert.assertTrue("Enabling glyph layout should produce a visible difference for the Arabic/bidi fixture",
                countDifferentPixels(withoutGlyphLayout, withGlyphLayout) > 100);
    }
}
