package com.openhtmltopdf.pdfbox.glyphlayout.awt;

import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.extend.FontContext;
import com.openhtmltopdf.extend.impl.FSNoOpCacheStore;
import com.openhtmltopdf.pdfbox.glyphlayout.awt.example.PdfBoxGlyphLayoutExampleBidi;
import com.openhtmltopdf.pdfboxout.PDFontSupplier;
import com.openhtmltopdf.pdfboxout.PdfBoxFSFont;
import com.openhtmltopdf.pdfboxout.PdfBoxFontResolver;
import com.openhtmltopdf.pdfboxout.PdfBoxTextRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.Assert;
import org.junit.Test;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Tests created with support from GitHub Copilot
 */
public class PdfBoxGlyphLayoutTest {
    public static final String OUTPUT_DIR_NAME = "target/glyphlayout-test";

    /**
     * Smoke test: rendering a long DIN 91379 document should create several pages and complete successfully.
     */
    @Test
    public void testComplexTextRendersAcrossShadowPages() throws Exception {
        String exampleHtmlPath = "/html/GlyphLayoutExampleDin91379.html";

        Path outputDir = Paths.get(OUTPUT_DIR_NAME);
        Files.createDirectories(outputDir);
        Path output = outputDir.resolve("testComplexTextRendersAcrossShadowPages.pdf");

        try (InputStream is = PdfBoxGlyphLayoutExampleBidi.class.getResourceAsStream(exampleHtmlPath)) {
            Objects.requireNonNull(is, "Could not find Html resource " + exampleHtmlPath);

            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String lines = br.lines().filter(s -> !s.matches("</?html.*")).collect(Collectors.joining("\n"));
                String html = "<html lang=\"en\" xmlns=\"http://www.w3.org/1999/html\">\n" + lines + lines + "\n</html>\n";

                try (InputStream fontIs = getClass().getResourceAsStream("/fonts/arimo/Arimo-Regular.ttf")) {
                    Assert.assertNotNull("Arimo font resource missing", fontIs);

                    try (PDDocument doc = new PDDocument();
                         java.io.OutputStream outputStream = Files.newOutputStream(output)) {
                        PdfBoxGlyphLayoutProcessorAwt glyphLayoutProcessor = new PdfBoxGlyphLayoutProcessorAwt();
                        PDType0Font arimo = glyphLayoutProcessor.loadFont(doc, fontIs);

                        PdfRendererBuilder builder = new PdfRendererBuilder();
                        builder.useGlyphLayoutProcessor(glyphLayoutProcessor)
                                .toStream(outputStream)
                                .usePDDocument(doc)
                                .useFont(new PDFontSupplier(arimo), "Arimo")
                                .withHtmlContent(html, "")
                                .run();

                        Assert.assertTrue("Glyph layout rendering should create more than one page for long content", doc.getNumberOfPages() > 2);
                    }
                }
            }
        }
    }

    protected void testWidth(String text, String fontResource, String fontName, boolean shouldEqual, String assertionMessage) throws IOException, FontFormatException {

        try (PDDocument doc = new PDDocument()) {
            PdfBoxGlyphLayoutProcessorAwt glyphLayoutProcessor = new PdfBoxGlyphLayoutProcessorAwt();

            try (InputStream in1 = getClass().getResourceAsStream(fontResource);
                 InputStream in2 = getClass().getResourceAsStream(fontResource)) {
                Assert.assertNotNull(fontResource + " missing (1)", in1);
                Assert.assertNotNull(fontResource + " missing (2)", in2);

                PDType0Font fontNoProcessor =   PDType0Font.load(doc, in1);
                PDType0Font fontWithProcessor = glyphLayoutProcessor.loadFont(doc, in2);

                PdfBoxFSFont fsFontNoProcessor = new PdfBoxFSFont(
                        Collections.singletonList(new PdfBoxFontResolver.FontDescription(
                                doc, () -> fontNoProcessor, IdentValue.NORMAL, 400,
                                fontName, false, false,
                                FSNoOpCacheStore.INSTANCE)), 24f);

                PdfBoxFSFont fsFontWithProcessor = new PdfBoxFSFont(
                        Collections.singletonList(new PdfBoxFontResolver.FontDescription(
                                doc, () -> fontWithProcessor, IdentValue.NORMAL, 400,
                                fontName, false, false,
                                FSNoOpCacheStore.INSTANCE)), 24f);

                PdfBoxTextRenderer renderer = new PdfBoxTextRenderer();
                renderer.setup(new FontContext() {}, null);

                int widthNoProcessor = renderer.getWidth(new FontContext() {}, fsFontNoProcessor, text);
                int widthWithProcessor = renderer.getWidth(new FontContext() {}, fsFontWithProcessor, text);

                if( shouldEqual) {
                    Assert.assertEquals(assertionMessage, widthNoProcessor, widthWithProcessor);
                } else {
                    Assert.assertNotEquals(assertionMessage, widthNoProcessor, widthWithProcessor);
                }
            }
        }
    }

    @Test
    public void getWidthDiffersWhenGlyphLayoutProcessorIsEnabled() throws Exception {
        String arabicLigature = "\u0644\u0627"; // "لا" (lam + alef ligature)

        testWidth(arabicLigature, "/fonts/noto/NotoSansArabic-Regular.ttf", "Noto Sans Arabic",
            false, "Width should differ when glyph layout processing is enabled for shaped Arabic text");

    }

    @Test
    public void getWidthDiffersForArabicTextWhenGlyphLayoutProcessorIsEnabled() throws Exception {
        String arabicText =  "نحن الآن في شهر رمضان 1447 هجري";

        testWidth(arabicText, "/fonts/noto/NotoSansArabic-Regular.ttf", "Noto Sans Arabic",
                false, "Arabic word width should differ when glyph shaping is active");
    }

    @Test
    public void getWidthEqualsForLetterA() throws Exception {
        testWidth("A", "/fonts/arimo/Arimo-Regular.ttf", "Arimo",
                true, "Width should be equal when glyph layout processing is enabled for the letter A");
    }

    @Test
    public void getWidthDiffersForMixedArabicLatinTextWhenGlyphLayoutProcessorIsEnabled() throws Exception {
        String text1 = "Guten Tag ";
        String text2 = "السلام عليكم";
        String text3 = " Good afternoon";
        String mixedText = text1 + text2 + text3;

        testWidth(mixedText, "/fonts/noto/NotoSansArabic-Regular.ttf", "Noto Sans Arabic",
                false, "Mixed Arabic/Latin width should differ when glyph shaping is active");
    }

    @Test
    public void getWidthStaysStableForCombiningMarks() throws Exception {
        testWidth("A\u030B", "/fonts/arimo/Arimo-Regular.ttf", "Arimo",
                true, "Combining diacritical marks should not change measurement when shaping is active");
    }
}
