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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Real PDF assertions for glyph-layout correctness. */
public class PdfBoxGlyphLayoutOutputTest {
    private static final String BIDI_HTML_PATH = "/html/GlyphLayoutExampleBidi.html";
    private static final String ARABIC_FONT = "/fonts/noto/NotoSansArabic-Regular.ttf";
    private static final String LATIN_FONT = "/fonts/arimo/Arimo-Regular.ttf";
    private static final String HTML_NS = "http://www.w3.org/1999/html";

    private static class TextPositionCollector extends PDFTextStripper {
        private final List<TextPosition> positions = new ArrayList<>();

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            positions.addAll(textPositions);
        }
    }

    private PDDocument renderDocument(String html, boolean useGlyphLayoutProcessor) throws Exception {
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

            return Loader.loadPDF(output.toByteArray());
        }
    }

    private String readHtmlResource(String resource) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            Assert.assertNotNull("Resource missing: " + resource, input);
            byte[] bytes = toByteArray(input);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = input.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    private TextPositionCollector collectPositions(PDDocument doc) throws Exception {
        TextPositionCollector collector = new TextPositionCollector();
        collector.setSortByPosition(true);
        collector.getText(doc);
        return collector;
    }

    private static boolean containsArabic(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.codePoints().anyMatch(cp -> cp >= 0x0600 && cp <= 0x06FF);
    }

    @Test
    public void testBidiExamplePreservesExpectedArabicAndLatinText() throws Exception {
        String html = readHtmlResource(BIDI_HTML_PATH);

        try (PDDocument doc = renderDocument(html, true)) {
            String extracted = new PDFTextStripper().getText(doc);
            Assert.assertTrue("PDF should contain Arabic text", extracted.contains("نحن الآن في شهر رمضان 1447 هجري"));
            Assert.assertTrue("PDF should contain the mixed Arabic/Latin sentence", extracted.contains("Guten Tag"));
            Assert.assertTrue("PDF should contain the Arabic greeting", extracted.contains("السلام عليكم"));
            Assert.assertTrue("PDF should contain the Latin closing sentence", extracted.contains("Good afternoon"));
        }
    }

    @Test
    public void testGlyphLayoutChangesTextPositionsForArabic() throws Exception {
        String html = readHtmlResource(BIDI_HTML_PATH);

        try (PDDocument without = renderDocument(html, false);
             PDDocument with = renderDocument(html, true)) {

            TextPositionCollector withoutCollector = collectPositions(without);
            TextPositionCollector withCollector = collectPositions(with);

            List<TextPosition> arabicWithout = withoutCollector.positions.stream()
                    .filter(p -> containsArabic(p.getUnicode()))
                    .sorted(Comparator.comparingDouble(TextPosition::getXDirAdj))
                    .collect(Collectors.toList());

            List<TextPosition> arabicWith = withCollector.positions.stream()
                    .filter(p -> containsArabic(p.getUnicode()))
                    .sorted(Comparator.comparingDouble(TextPosition::getXDirAdj))
                    .collect(Collectors.toList());

            Assert.assertFalse("Arabic text should be present without glyph layout", arabicWithout.isEmpty());
            Assert.assertFalse("Arabic text should be present with glyph layout", arabicWith.isEmpty());

            double diff = Math.abs(arabicWith.get(0).getXDirAdj() - arabicWithout.get(0).getXDirAdj());
            Assert.assertTrue("Glyph layout should change the positioned output for Arabic text", diff > 0.1d);
        }
    }

    @Test
    public void testMixedDirectionTextWrapsAcrossMultipleLines() throws Exception {
        String html = "<html lang=\"en\" xmlns=\"" + HTML_NS + "\"><head><meta charset=\"utf-8\"/>"
                + "<style>@page { size: A4; margin: 2cm; } body { font-family: 'Noto Sans Arabic', Arimo, sans-serif; font-size: 18pt; }"
                + ".narrow { width: 150px; border: 1px solid black; } </style></head><body>"
                + "<div class=\"narrow\">Start Guten Tag السلام عليكم 123 End</div></body></html>";

        try (PDDocument doc = renderDocument(html, true)) {
            TextPositionCollector collector = collectPositions(doc);
            List<Float> yValues = collector.positions.stream()
                    .map(TextPosition::getYDirAdj)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            Assert.assertTrue("Mixed-direction text should wrap onto multiple lines", yValues.size() >= 3);
            Assert.assertTrue("Wrapped text should stay on the page", collector.positions.stream().allMatch(p -> p.getXDirAdj() >= 0f));
            Assert.assertTrue("Wrapped text should not overflow the page width",
                    collector.positions.stream().allMatch(p -> p.getXDirAdj() + p.getWidthDirAdj() <= 595f));
        }
    }

    @Test
    public void testFallbackFontIsUsedForArabicGlyphs() throws Exception {
        String html = "<html lang=\"en\" xmlns=\"" + HTML_NS + "\"><head><meta charset=\"utf-8\"/>"
                + "<style>@page { size: A4; margin: 2cm; } body { font-family: Arimo, 'Noto Sans Arabic', sans-serif; font-size: 20pt; }</style>"
                + "</head><body>Latin fallback: السلام عليكم</body></html>";

        try (PDDocument doc = renderDocument(html, true)) {
            TextPositionCollector collector = collectPositions(doc);
            boolean sawNotoArabic = collector.positions.stream()
                    .filter(p -> containsArabic(p.getUnicode()))
                    .map(p -> p.getFont().getName())
                    .anyMatch(name -> name.toLowerCase().contains("noto"));

            Assert.assertTrue("Arabic fallback glyphs should come from the Noto font", sawNotoArabic);
        }
    }

    @Test
    public void testSavedBidiPdfPreservesUnicodeText() throws Exception {
        String arabic = "نحن الآن في شهر رمضان 1447 هجري";
        String mixed = "Guten Tag السلام عليكم Good afternoon";
        String html = "<html lang=\"en\" xmlns=\"" + HTML_NS + "\"><head><meta charset=\"utf-8\"/></head><body>"
                + arabic + "<p/>" + mixed + "</body></html>";

        try (PDDocument doc = renderDocument(html, true)) {
            String extracted = new PDFTextStripper().getText(doc);
            Assert.assertTrue("Arabic text should round-trip", extracted.contains(arabic));
            Assert.assertTrue("Mixed Arabic/Latin sample should round-trip", extracted.contains("Guten Tag"));
            Assert.assertTrue("Arabic greeting should round-trip", extracted.contains("السلام عليكم"));
            Assert.assertTrue("Latin closing text should round-trip", extracted.contains("Good afternoon"));
        }
    }
}
