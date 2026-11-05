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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Output correctness tests for glyph layout, fallback fonts, bidi, and line wrapping. */
public class PdfBoxGlyphLayoutOutputTest {
    private static final String ARABIC_FONT = "/fonts/noto/NotoSansArabic-Regular.ttf";
    private static final String LATIN_FONT = "/fonts/arimo/Arimo-Regular.ttf";
    private static final String HTML_NS = "http://www.w3.org/1999/html";
    private static final float LINE_BASELINE_TOLERANCE = 2f;
    private static final float WORD_GAP_THRESHOLD = 2f;

    private static final class ExtractedOutput {
        final List<TextPosition> positions = new ArrayList<TextPosition>();
        final List<String> lines = new ArrayList<String>();
        final List<List<String>> wordsByLine = new ArrayList<List<String>>();
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
            buildVisualLines(output);
            return output;
        }
    }

    /**
     * TextStripper callbacks are text runs, not necessarily visual lines. Group
     * positions by baseline, then order each line by x. This fixture is LTR Latin,
     * so physical x order is also its expected logical order.
     */
    private void buildVisualLines(ExtractedOutput output) {
        List<TextPosition> ordered = new ArrayList<TextPosition>(output.positions);
        Collections.sort(ordered, new Comparator<TextPosition>() {
            @Override
            public int compare(TextPosition first, TextPosition second) {
                int byBaseline = Float.compare(first.getYDirAdj(), second.getYDirAdj());
                return byBaseline != 0 ? byBaseline : Float.compare(first.getXDirAdj(), second.getXDirAdj());
            }
        });

        List<List<TextPosition>> lines = new ArrayList<List<TextPosition>>();
        List<TextPosition> line = null;
        float lineBaseline = Float.NaN;
        for (TextPosition position : ordered) {
            if (line == null || Math.abs(position.getYDirAdj() - lineBaseline) > LINE_BASELINE_TOLERANCE) {
                line = new ArrayList<TextPosition>();
                lines.add(line);
                lineBaseline = position.getYDirAdj();
            }
            line.add(position);
        }

        for (List<TextPosition> linePositions : lines) {
            Collections.sort(linePositions, new Comparator<TextPosition>() {
                @Override
                public int compare(TextPosition first, TextPosition second) {
                    return Float.compare(first.getXDirAdj(), second.getXDirAdj());
                }
            });
            List<String> words = wordsOnLine(linePositions);
            if (!words.isEmpty()) {
                output.wordsByLine.add(words);
                StringBuilder lineText = new StringBuilder();
                for (String word : words) {
                    if (lineText.length() > 0) {
                        lineText.append(' ');
                    }
                    lineText.append(word);
                }
                output.lines.add(lineText.toString());
            }
        }
    }

    private List<String> wordsOnLine(List<TextPosition> positions) {
        List<String> words = new ArrayList<String>();
        StringBuilder currentWord = new StringBuilder();
        float previousRight = Float.NaN;

        for (TextPosition position : positions) {
            String unicode = position.getUnicode();
            if (unicode == null || unicode.length() == 0) {
                continue;
            }

            boolean whitespace = isWhitespace(unicode);
            float gap = Float.isNaN(previousRight) ? 0f : position.getXDirAdj() - previousRight;
            if (whitespace || (!Float.isNaN(previousRight) && gap > WORD_GAP_THRESHOLD)) {
                appendWord(words, currentWord);
            }
            if (!whitespace) {
                currentWord.append(unicode);
            }
            previousRight = position.getXDirAdj() + position.getWidthDirAdj();
        }
        appendWord(words, currentWord);
        return words;
    }

    private boolean isWhitespace(String text) {
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                return false;
            }
            i += Character.charCount(codePoint);
        }
        return true;
    }

    private void appendWord(List<String> words, StringBuilder currentWord) {
        if (currentWord.length() > 0) {
            words.add(currentWord.toString());
            currentWord.setLength(0);
        }
    }

    private String wrappingHtml(int widthPx) {
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

    private List<String> expectedWords() {
        List<String> expected = new ArrayList<String>();
        for (int i = 0; i < 12; i++) {
            expected.add("AVATAR");
        }
        return expected;
    }

    private List<String> flatten(List<List<String>> wordsByLine) {
        List<String> allWords = new ArrayList<String>();
        for (List<String> line : wordsByLine) {
            allWords.addAll(line);
        }
        return allWords;
    }

    private void assertExpectedWrappedText(ExtractedOutput output) {
        Assert.assertEquals("The visual lines must retain every input word, exactly once and in source order",
                expectedWords(), flatten(output.wordsByLine));
        Assert.assertTrue("The fixture should wrap onto at least two visual lines", output.wordsByLine.size() >= 2);
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

    @Test
    public void testBidiExampleVisualBounds() throws Exception {
        String html = readHtmlResource("/html/GlyphLayoutExampleBidi.html");
        byte[] pdf = render(html, false, true);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            Assert.assertEquals("The bidi example should fit on one page", 1, document.getNumberOfPages());
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 96);
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
        for (int width = 300; width <= 380; width += 2) {
            String html = wrappingHtml(width);
            ExtractedOutput noKern = extract(render(html, false, false));
            ExtractedOutput kern = extract(render(html, true, false));
            assertExpectedWrappedText(noKern);
            assertExpectedWrappedText(kern);
            if (!noKern.wordsByLine.equals(kern.wordsByLine)) {
                widthWhereKerningChangesBreaks = width;
                noKerningNoGlyph = noKern;
                kerningNoGlyph = kern;
                break;
            }
        }

        Assert.assertTrue("The tested widths must include a case where kerning changes the word-to-line assignment",
                widthWhereKerningChangesBreaks >= 0);
        Assert.assertNotEquals(noKerningNoGlyph.wordsByLine, kerningNoGlyph.wordsByLine);

        String htmlAtBoundary = wrappingHtml(widthWhereKerningChangesBreaks);
        ExtractedOutput noKerningWithGlyph = extract(render(htmlAtBoundary, false, true));
        ExtractedOutput kerningWithGlyph = extract(render(htmlAtBoundary, true, true));
        assertExpectedWrappedText(noKerningWithGlyph);
        assertExpectedWrappedText(kerningWithGlyph);

        // Glyph layout computes advances for the shaped run. The separate PDFBox
        // pair-kerning option must not be added again when glyph layout is active.
        Assert.assertEquals("Toggling pair kerning must not move words to different lines with glyph layout enabled",
                noKerningWithGlyph.wordsByLine, kerningWithGlyph.wordsByLine);
    }
}
