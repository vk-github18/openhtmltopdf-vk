package com.openhtmltopdf.pdfbox.glyphlayout.awt;

import com.openhtmltopdf.pdfboxout.PDFontSupplier;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutProcessorAwt;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Tests rendered line breaking with kerning and glyph layout enabled independently. */
public class PdfBoxGlyphLayoutOutputTest {
    private static final String ARABIC_FONT = "/fonts/noto/NotoSansArabic-Regular.ttf";
    private static final String LATIN_FONT = "/fonts/arimo/Arimo-Regular.ttf";
    private static final String HTML_NS = "http://www.w3.org/1999/html";
    private static final String WORD = "AVAVAVAVAVAV";

    private static final class RenderedLines {
        final List<String> lines = new ArrayList<String>();
        String extractedText;
    }

    private byte[] render(String html, boolean kerning, boolean glyphLayout) throws Exception {
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
            if (glyphLayout) {
                builder.useGlyphLayoutProcessor(processor);
            }
            builder.toStream(output)
                    .usePDDocument(document)
                    .useFont(new PDFontSupplier(arabicFont), "Noto Sans Arabic")
                    .useFont(new PDFontSupplier(latinFont), "Arimo")
                    .withHtmlContent(html, "")
                    .run();

            // PDFRendererBuilder closes the supplied document after run; the bytes are
            // reopened by collectLines() below, so this method returns an independent PDF.
            return output.toByteArray();
        }
    }

    private String wrappingHtml(int widthPx, boolean kerning) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0) {
                text.append(' ');
            }
            text.append(WORD);
        }

        return "<html lang=\"en\" xmlns=\"" + HTML_NS + "\"><head><meta charset=\"utf-8\"/>"
                + "<style>@page { size: A4; margin: 2cm; } body { margin: 0; }"
                + ".sample { width: " + widthPx + "px; font-family: Arimo, sans-serif; font-size: 24px; line-height: 1.3;"
                + " font-kerning: " + (kerning ? "normal" : "none") + "; }</style></head><body>"
                + "<div class=\"sample\">" + text + "</div></body></html>";
    }

    private RenderedLines collectLines(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            Assert.assertEquals("The wrapping fixture must fit on one page", 1, document.getNumberOfPages());
            final RenderedLines result = new RenderedLines();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<org.apache.pdfbox.text.TextPosition> positions) {
                    result.lines.add(text.trim());
                }
            };
            stripper.setSortByPosition(true);
            result.extractedText = stripper.getText(document);
            return result;
        }
    }

    private String withoutWhitespace(String text) {
        return text.replaceAll("\\s+", "");
    }

    private String expectedText() {
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            expected.append(WORD);
        }
        return expected.toString();
    }

    private void assertNoLostOrDuplicatedText(RenderedLines rendered) {
        Assert.assertEquals("Line wrapping must preserve every input character", expectedText(),
                withoutWhitespace(rendered.extractedText));
    }

    /**
     * Tests all four combinations. In the unshaped path, kerning should affect
     * at least one wrap boundary. With glyph layout, shaping owns the glyph
     * advances, so the separate font-kerning switch must not change the breaks.
     * A small width sweep finds a boundary without hard-coding a font-specific
     * exact line count.
     */
    @Test
    public void testLineBreaksWithAndWithoutKerningAndGlyphLayout() throws Exception {
        int widthWhereKerningChangesBreaks = -1;
        RenderedLines unkernedWithoutGlyphLayout = null;
        RenderedLines kernedWithoutGlyphLayout = null;

        for (int width = 300; width <= 380; width += 2) {
            RenderedLines unkerned = collectLines(render(wrappingHtml(width, false), false, false));
            RenderedLines kerned = collectLines(render(wrappingHtml(width, true), false, false));
            assertNoLostOrDuplicatedText(unkerned);
            assertNoLostOrDuplicatedText(kerned);

            if (!unkerned.lines.equals(kerned.lines)) {
                widthWhereKerningChangesBreaks = width;
                unkernedWithoutGlyphLayout = unkerned;
                kernedWithoutGlyphLayout = kerned;
                break;
            }
        }

        Assert.assertTrue("Kerning on/off should produce different line breaks at a tested width",
                widthWhereKerningChangesBreaks >= 0);
        Assert.assertNotEquals("Unkerned and kerned runs should have different line assignments",
                unkernedWithoutGlyphLayout.lines, kernedWithoutGlyphLayout.lines);

        RenderedLines unkernedWithGlyphLayout = collectLines(
                render(wrappingHtml(widthWhereKerningChangesBreaks, false), false, true));
        RenderedLines kernedWithGlyphLayout = collectLines(
                render(wrappingHtml(widthWhereKerningChangesBreaks, true), false, true));
        assertNoLostOrDuplicatedText(unkernedWithGlyphLayout);
        assertNoLostOrDuplicatedText(kernedWithGlyphLayout);

        Assert.assertEquals("With glyph layout enabled, the processor's shaping should determine line breaks; "
                        + "the independent kerning switch must not apply a second kerning adjustment",
                unkernedWithGlyphLayout.lines, kernedWithGlyphLayout.lines);

        // The extracted text is checked independently in each of the four cases:
        // (kerning off/on) x (glyph layout off/on).
        Assert.assertFalse(unkernedWithoutGlyphLayout.lines.isEmpty());
        Assert.assertFalse(kernedWithoutGlyphLayout.lines.isEmpty());
        Assert.assertFalse(unkernedWithGlyphLayout.lines.isEmpty());
        Assert.assertFalse(kernedWithGlyphLayout.lines.isEmpty());
    }
}
