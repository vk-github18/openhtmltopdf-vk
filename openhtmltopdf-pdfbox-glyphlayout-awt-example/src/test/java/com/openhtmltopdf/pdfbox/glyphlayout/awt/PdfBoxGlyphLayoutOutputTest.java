package com.openhtmltopdf.pdfbox.glyphlayout.awt;

import com.openhtmltopdf.pdfboxout.PDFontSupplier;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutProcessorAwt;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Output correctness tests for glyph layout, fallback fonts, and bidi text. */
public class PdfBoxGlyphLayoutOutputTest {
    private static final String ARABIC_FONT = "/fonts/noto/NotoSansArabic-Regular.ttf";
    private static final String LATIN_FONT = "/fonts/arimo/Arimo-Regular.ttf";
    private static final String HTML_NS = "http://www.w3.org/1999/html";

    private static final class ExtractedOutput {
        final List<TextPosition> positions = new ArrayList<TextPosition>();
        final List<String> lines = new ArrayList<String>();
        String text;
    }

    private byte[] render(String html, boolean kerning, boolean useGlyphLayoutProcessor) throws Exception {
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
            builder.useFontKerning(kerning);
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
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private String bidiHtml(String body) {
        return "<html lang=\"en\" xmlns=\"" + HTML_NS + "\"><head><meta charset=\"utf-8\"/>"
                + "<style>@page { size: A4; margin: 2cm; } body { font-family: 'Noto Sans Arabic', Arimo, sans-serif; font-size: 14pt; }</style>"
                + "</head><body>" + body + "</body></html>";
    }

    private static boolean containsArabic(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            if (codePoint >= 0x0600 && codePoint <= 0x06ff) {
                return true;
            }
            i += Character.charCount(codePoint);
        }
        return false;
    }

    private ExtractedOutput extract(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            final ExtractedOutput output = new ExtractedOutput();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    output.positions.addAll(positions);
                }
            };
            stripper.setSortByPosition(true);
            output.text = stripper.getText(document);

            // PDFTextStripper's writeString callbacks are text runs, not necessarily
            // visual lines. Reconstruct lines from glyph baselines and order glyphs
            // left-to-right so a run split by bidi/font fallback does not look like a break.
            Map<Integer, List<TextPosition>> byBaseline = new TreeMap<Integer, List<TextPosition>>();
            for (TextPosition position : output.positions) {
                int baseline = Math.round(position.getYDirAdj());
                List<TextPosition> line = byBaseline.get(baseline);
                if (line == null) {
                    line = new ArrayList<TextPosition>();
                    byBaseline.put(baseline, line);
                }
                line.add(position);
            }

            for (List<TextPosition> line : byBaseline.values()) {
                Collections.sort(line, new Comparator<TextPosition>() {
                    @Override
                    public int compare(TextPosition first, TextPosition second) {
                        return Float.compare(first.getXDirAdj(), second.getXDirAdj());
                    }
                });
                StringBuilder lineText = new StringBuilder();
                for (TextPosition position : line) {
                    if (position.getUnicode() != null) {
                        lineText.append(position.getUnicode());
                    }
                }
                output.lines.add(lineText.toString().trim());
            }
            return output;
        }
    }

    private String wrappingHtml(int widthPx, boolean kerning) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0) {
                text.append(' ');
            }
            text.append("AVATAR");
        }
        return "<html lang=\"en\" xmlns=\"" + HTML_NS + "\"><head><meta charset=\"utf-8\"/>"
                + "<style>@page { size: A4; margin: 2cm; } body { margin: 0; }"
                + ".sample { width: " + widthPx + "px; font-family: Arimo, sans-serif; font-size: 24px; line-height: 1.3; }</style>"
                + "</head><body><div class=\"sample\">" + text + "</div></body></html>";
    }

    private String withoutWhitespace(String text) {
        return text.replaceAll("\\s+", "");
    }

    private String expectedWrappedText() {
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            expected.append("AVATAR");
        }
        return expected.toString();
    }

    private void assertExpectedText(ExtractedOutput output, String expected) {
        Assert.assertEquals("Rendering must preserve all text without losing or duplicating glyphs",
                expected, withoutWhitespace(output.text));
        Assert.assertFalse("The output should contain positioned glyphs", output.positions.isEmpty());
        Assert.assertFalse("The output should contain at least one visual line", output.lines.isEmpty());
    }

    private boolean hasVisiblePixels(java.awt.image.BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != 0x00ffffff) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    public void testBidiExampleVisualBounds() throws Exception {
        String html = readHtmlResource("/html/GlyphLayoutExampleBidi.html");
        byte[] pdf = render(html, false, true);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            Assert.assertEquals("The bidi example should fit on one page", 1, document.getNumberOfPages());
            java.awt.image.BufferedImage image = new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(0, 96);
            Assert.assertEquals("A4 page width at 96 DPI", 794, image.getWidth());
            Assert.assertEquals("A4 page height at 96 DPI", 1123, image.getHeight());
            Assert.assertTrue("The bidi example should paint visible text", hasVisiblePixels(image));
        }
    }

    @Test
    public void testArabicGlyphsUseFallbackFontWhenPrimaryFontLacksThem() throws Exception {
        String html = "<html lang=\"en\" xmlns=\"" + HTML_NS + "\"><head><meta charset=\"utf-8\"/>"
                + "<style>@page { size: A4; margin: 2cm; } body { font-family: Arimo, 'Noto Sans Arabic', sans-serif; font-size: 20pt; }</style>"
                + "</head><body>Latin fallback: السلام عليكم</body></html>";
        ExtractedOutput output = extract(render(html, false, true));
        boolean sawArabicFromNoto = false;
        for (TextPosition position : output.positions) {
            if (containsArabic(position.getUnicode()) && position.getFont().getName().toLowerCase().contains("noto")) {
                sawArabicFromNoto = true;
            }
        }
        Assert.assertTrue("Arabic glyph positions should be assigned to the Noto fallback font", sawArabicFromNoto);
        Assert.assertTrue("Fallback output should contain the Arabic phrase", output.text.contains("السلام"));
    }

    @Test
    public void testMixedDirectionTextWrapsWithoutClipping() throws Exception {
        String html = "<html lang=\"en\" xmlns=\"" + HTML_NS + "\"><head><meta charset=\"utf-8\"/>"
                + "<style>@page { size: A4; margin: 2cm; } body { font-family: 'Noto Sans Arabic', Arimo, sans-serif; font-size: 18pt; }"
                + ".narrow { width: 145px; border: 1px solid black; }</style></head><body>"
                + "<div class=\"narrow\">Start Guten Tag السلام عليكم 123 End</div></body></html>";
        ExtractedOutput output = extract(render(html, false, true));
        Assert.assertTrue("Mixed-direction text should wrap across multiple visual lines", output.lines.size() >= 3);
        Assert.assertTrue("Line wrapping should preserve Latin, Arabic, and digits",
                output.text.contains("Start") && output.text.contains("Guten Tag")
                        && output.text.contains("السلام") && output.text.contains("123") && output.text.contains("End"));
        for (TextPosition position : output.positions) {
            Assert.assertTrue("Glyph must not be positioned left of the page", position.getXDirAdj() >= 0f);
            Assert.assertTrue("Glyph must not be positioned right of the page",
                    position.getXDirAdj() + position.getWidthDirAdj() <= 595f);
        }
    }

    @Test
    public void testSavedBidiPdfPreservesExpectedUnicodeText() throws Exception {
        String arabic = "نحن الآن في شهر رمضان 1447 هجري";
        String mixed = "Guten Tag السلام عليكم Good afternoon";
        ExtractedOutput output = extract(render(bidiHtml(arabic + "<p/>" + mixed), false, true));
        String normalized = Normalizer.normalize(output.text, Normalizer.Form.NFC);
        Assert.assertTrue("Extracted PDF text should preserve Arabic and the embedded number",
                normalized.contains(Normalizer.normalize(arabic, Normalizer.Form.NFC)));
        Assert.assertTrue("Extracted text should preserve Latin around Arabic",
                normalized.contains("Guten Tag") && normalized.contains("Good afternoon"));
        Assert.assertTrue("Extracted PDF text should preserve Arabic greeting", normalized.contains("السلام"));
    }

    @Test
    public void testLineBreaksWithAndWithoutKerningAndGlyphLayout() throws Exception {
        int widthWhereKerningChangesBreaks = -1;
        ExtractedOutput noKerningNoGlyph = null;
        ExtractedOutput kerningNoGlyph = null;

        // Find an actual wrapping threshold where kerning changes the break positions.
        // This avoids depending on one font's exact metrics while still exercising breaks.
        for (int width = 300; width <= 380; width += 2) {
            String html = wrappingHtml(width, false);
            ExtractedOutput noKern = extract(render(html, false, false));
            ExtractedOutput kern = extract(render(html, true, false));
            assertExpectedText(noKern, expectedWrappedText());
            assertExpectedText(kern, expectedWrappedText());
            if (!noKern.lines.equals(kern.lines)) {
                widthWhereKerningChangesBreaks = width;
                noKerningNoGlyph = noKern;
                kerningNoGlyph = kern;
                break;
            }
        }

        Assert.assertTrue("The test must find a width where enabling kerning changes line breaks",
                widthWhereKerningChangesBreaks >= 0);
        Assert.assertNotEquals("Unkerned and kerned line assignments should differ",
                noKerningNoGlyph.lines, kerningNoGlyph.lines);

        String boundaryHtml = wrappingHtml(widthWhereKerningChangesBreaks, false);
        ExtractedOutput noKerningWithGlyph = extract(render(boundaryHtml, false, true));
        ExtractedOutput kerningWithGlyph = extract(render(boundaryHtml, true, true));
        assertExpectedText(noKerningWithGlyph, expectedWrappedText());
        assertExpectedText(kerningWithGlyph, expectedWrappedText());

        // Glyph layout supplies the shaped advances itself; the independent PDFBox
        // pair-kerning option must not apply a second kerning adjustment.
        Assert.assertEquals("Kerning on/off must produce the same breaks when glyph layout is active",
                noKerningWithGlyph.lines, kerningWithGlyph.lines);
    }
}
