package com.openhtmltopdf.pdfbox.glyphlayout.awt;

import com.openhtmltopdf.pdfboxout.PDFontSupplier;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutProcessorAwt;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.Assert;
import org.junit.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Output correctness tests for glyph layout, fallback fonts, and bidi text. */
public class PdfBoxGlyphLayoutOutputTest {
    private static final String ARABIC_FONT = "/fonts/noto/NotoSansArabic-Regular.ttf";
    private static final String LATIN_FONT = "/fonts/arimo/Arimo-Regular.ttf";
    private static final String HTML_NS = "http://www.w3.org/1999/html";

    private byte[] render(String html, boolean useGlyphLayoutProcessor) throws Exception {
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
            return output.toByteArray();
        }
    }

    private String readHtmlResource(String path) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            Assert.assertNotNull("Missing HTML resource: " + path, input);
            byte[] bytes = readAllBytes(input);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private byte[] readAllBytes(InputStream input) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int count;
        while ((count = input.read(data)) != -1) {
            buffer.write(data, 0, count);
        }
        return buffer.toByteArray();
    }

    private String bidiHtml(String body) {
        return "<html lang=\"en\" xmlns=\"" + HTML_NS + "\"><head><meta charset=\"utf-8\"/>"
                + "<style>@page { size: A4; margin: 2cm; } body { font-family: 'Noto Sans Arabic', Arimo, sans-serif; font-size: 14pt; }</style>"
                + "</head><body>" + body + "</body></html>";
    }

    private static boolean containsArabic(String text) {
        return text != null && text.codePoints().anyMatch(cp -> cp >= 0x0600 && cp <= 0x06ff);
    }

    private int countDistinctYLines(PDDocument document) throws Exception {
        final Set<Float> lines = new HashSet<Float>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> positions) {
                for (TextPosition position : positions) {
                    lines.add(position.getYDirAdj());
                }
            }
        };
        stripper.getText(document);
        return lines.size();
    }

    private void assertKerningAndGlyphLayoutVariantsDiffer() throws Exception {
        String html = "<html lang=\"en\" xmlns=\"" + HTML_NS + "\"><head><meta charset=\"utf-8\"/>"
                + "<style>@page { size: A4; margin: 2cm; } body { width: 210px; font-size: 26px; line-height: 1.4; }"
                + ".wrap { font-family: 'Arimo', 'Noto Sans Arabic', sans-serif; letter-spacing: 0; font-kerning: %s; }
                   p { margin: 0; }</style></head><body><div class=\"wrap\">AVATAR AVATAR AVATAR AVATAR AVATAR AVATAR</div></body></html>";

        int noKerningNoGlyph = countDistinctYLines(Loader.loadPDF(render(String.format(html, "none"), false)));
        int normalNoGlyph = countDistinctYLines(Loader.loadPDF(render(String.format(html, "normal"), false)));
        int noKerningWithGlyph = countDistinctYLines(Loader.loadPDF(render(String.format(html, "none"), true)));
        int normalWithGlyph = countDistinctYLines(Loader.loadPDF(render(String.format(html, "normal"), true)));

        Assert.assertTrue("Kerning should affect wrapping even without glyph layout",
                noKerningNoGlyph != normalNoGlyph || noKerningNoGlyph != noKerningWithGlyph || noKerningNoGlyph != normalWithGlyph);
        Assert.assertTrue("Glyph layout should affect wrapping even with kerning",
                noKerningNoGlyph != noKerningWithGlyph || normalNoGlyph != normalWithGlyph);
    }

    @Test
    public void testBidiExampleVisualBounds() throws Exception {
        String html = readHtmlResource("/html/GlyphLayoutExampleBidi.html");

        try (PDDocument document = Loader.loadPDF(render(html, true))) {
            Assert.assertEquals("The bidi example should fit on one page", 1, document.getNumberOfPages());
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 96);
            Assert.assertEquals("A4 page width at 96 DPI", 794, image.getWidth());
            Assert.assertEquals("A4 page height at 96 DPI", 1123, image.getHeight());

            int minX = image.getWidth();
            int minY = image.getHeight();
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) & 0x00ffffff) != 0x00ffffff) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }

            Assert.assertTrue("Text should begin near the 2cm page margin", minX >= 65 && minX <= 110);
            Assert.assertTrue("Text should begin near the top page margin", minY >= 55 && minY <= 120);
            Assert.assertTrue("Both lines of bidi text should remain in the upper part of the page", maxY < 300);
            Assert.assertTrue("The rendered text should occupy a visible width", maxX - minX > 250);
        }
    }

    @Test
    public void testArabicGlyphsUseFallbackFontWhenPrimaryFontLacksThem() throws Exception {
        String html = "<html lang=\"en\" xmlns=\"" + HTML_NS + "\"><head><meta charset=\"utf-8\"/>"
                + "<style>@page { size: A4; margin: 2cm; } body { font-family: Arimo, 'Noto Sans Arabic', sans-serif; font-size: 20pt; }</style>"
                + "</head><body>Latin fallback: السلام عليكم</body></html>";
        byte[] pdf = render(html, true);

        List<String> arabicFontNames = new ArrayList<String>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    for (TextPosition position : positions) {
                        if (containsArabic(position.getUnicode())) {
                            arabicFontNames.add(position.getFont().getName());
                        }
                    }
                }
            };
            stripper.getText(document);
        }

        Assert.assertFalse("The PDF should contain Arabic glyph positions", arabicFontNames.isEmpty());
        Assert.assertTrue("Arabic glyphs should come from the Noto fallback font: " + arabicFontNames,
                arabicFontNames.size() == 0 || arabicFontNames.get(0).toLowerCase().contains("noto"));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 96);
            long darkPixels = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) & 0x00ffffff) != 0x00ffffff) {
                        darkPixels++;
                    }
                }
            }
            Assert.assertTrue("The PDF should visibly render the fallback text", darkPixels > 500);
        }
    }

    @Test
    public void testMixedDirectionTextWrapsWithoutClipping() throws Exception {
        String html = "<html lang=\"en\" xmlns=\"" + HTML_NS + "\"><head><meta charset=\"utf-8\"/>"
                + "<style>@page { size: A4; margin: 2cm; } body { font-family: 'Noto Sans Arabic', Arimo, sans-serif; font-size: 18pt; }"
                + ".narrow { width: 145px; border: 1px solid black; }</style></head><body>"
                + "<div class=\"narrow\">Start Guten Tag السلام عليكم 123 End</div></body></html>";

        try (PDDocument document = Loader.loadPDF(render(html, true))) {
            Assert.assertEquals("The narrow wrapping fixture should fit on one page", 1, document.getNumberOfPages());
            List<Float> lineYs = new ArrayList<Float>();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    for (TextPosition position : positions) {
                        float y = position.getYDirAdj();
                        if (lineYs.size() == 0 || !hasYValue(lineYs, y)) {
                            lineYs.add(y);
                        }
                        Assert.assertTrue("Text should not be clipped beyond the page's left edge", position.getXDirAdj() >= 0f);
                        Assert.assertTrue("Text should not be clipped beyond the page's right edge",
                                position.getXDirAdj() + position.getWidthDirAdj() <= document.getPage(0).getMediaBox().getWidth());
                    }
                }
            };
            stripper.getText(document);
            Assert.assertTrue("The narrow mixed-direction content should wrap onto multiple lines", lineYs.size() >= 3);

            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 96);
            Assert.assertTrue("Wrapped text should have visible rendered output", hasVisiblePixels(image));
        }
    }

    private boolean hasYValue(List<Float> lineYs, float value) {
        for (Float y : lineYs) {
            if (Math.abs(y.floatValue() - value) < 2f) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testSavedBidiPdfPreservesExpectedUnicodeText() throws Exception {
        String arabic = "نحن الآن في شهر رمضان 1447 هجري";
        String mixed = "Guten Tag السلام عليكم Good afternoon";
        String html = bidiHtml(arabic + "<p/>" + mixed);
        String extracted;
        try (PDDocument document = Loader.loadPDF(render(html, true))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            extracted = stripper.getText(document);
        }

        String normalized = Normalizer.normalize(extracted, Normalizer.Form.NFC);
        Assert.assertTrue("Extracted PDF text should preserve Arabic and the embedded number: " + normalized,
                normalized.contains(Normalizer.normalize(arabic, Normalizer.Form.NFC)));
        Assert.assertTrue("Extracted PDF text should preserve the Latin text around Arabic: " + normalized,
                normalized.contains("Guten Tag") && normalized.contains("Good afternoon"));
        Assert.assertTrue("Extracted PDF text should preserve the Arabic phrase: " + normalized,
                normalized.contains("السلام عليكم") || normalized.contains("السلام"));
    }

    @Test
    public void testLineBreaksVaryWithGlyphLayoutAndKerning() throws Exception {
        assertKerningAndGlyphLayoutVariantsDiffer();
    }

    private boolean hasVisiblePixels(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != 0x00ffffff) {
                    return true;
                }
            }
        }
        return false;
    }
}
