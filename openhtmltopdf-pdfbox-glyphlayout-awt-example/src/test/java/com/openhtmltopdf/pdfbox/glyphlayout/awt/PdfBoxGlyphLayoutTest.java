package com.openhtmltopdf.pdfbox.glyphlayout.awt;

import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.extend.FontContext;
import com.openhtmltopdf.extend.impl.FSNoOpCacheStore;
import com.openhtmltopdf.pdfboxout.PDFontSupplier;
import com.openhtmltopdf.pdfboxout.PdfBoxFSFont;
import com.openhtmltopdf.pdfboxout.PdfBoxFontResolver;
import com.openhtmltopdf.pdfboxout.PdfBoxTextRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutProcessorAwt;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
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
                         java.io.OutputStream out = Files.newOutputStream(output)) {
                        GlyphLayoutProcessorAwt glyphLayoutProcessor = new GlyphLayoutProcessorAwt();
                        PDType0Font arimo = glyphLayoutProcessor.loadFont(doc, fontIs);

                        PdfRendererBuilder builder = new PdfRendererBuilder();
                        builder.useGlyphLayoutProcessor(glyphLayoutProcessor)
                                .toStream(out)
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

    @Test
    public void getWidthDiffersWhenGlyphLayoutProcessorIsEnabled() throws Exception {
        String arabicLigature = "\u0644\u0627"; // "لا" (lam + alef ligature)

        try (PDDocument doc = new PDDocument()) {
            GlyphLayoutProcessorAwt glyphLayoutProcessor = new GlyphLayoutProcessorAwt();

            try (InputStream in = getClass().getResourceAsStream("/fonts/noto/NotoSansArabic-Regular.ttf")) {
                Assert.assertNotNull("Noto Sans Arabic font resource missing", in);
                PDType0Font font = glyphLayoutProcessor.loadFont(doc, in);

                PdfBoxFSFont fsFont = new PdfBoxFSFont(
                        Collections.singletonList(new PdfBoxFontResolver.FontDescription(
                                doc, () -> font, IdentValue.NORMAL, 400,
                                "Noto Sans Arabic", false, false,
                                FSNoOpCacheStore.INSTANCE)), 24f);

                PdfBoxTextRenderer rendererWithoutProcessor = new PdfBoxTextRenderer();
                rendererWithoutProcessor.setup(new FontContext() {}, null);

                PdfBoxTextRenderer rendererWithProcessor = new PdfBoxTextRenderer();
                rendererWithProcessor.setup(new FontContext() {}, null);
                rendererWithProcessor.setGlyphLayoutProcessor(glyphLayoutProcessor);

                int widthWithout = rendererWithoutProcessor.getWidth(new FontContext() {}, fsFont, arabicLigature);
                int widthWith = rendererWithProcessor.getWidth(new FontContext() {}, fsFont, arabicLigature);

                Assert.assertNotEquals("Width should differ when glyph layout processing is enabled for shaped Arabic text",
                        widthWithout, widthWith);
            }
        }
    }

    @Test
    public void getWidthDiffersForArabicTextWhenGlyphLayoutProcessorIsEnabled() throws Exception {
        String arabicText =  "نحن الآن في شهر رمضان 1447 هجري";

        try (PDDocument doc = new PDDocument()) {
            GlyphLayoutProcessorAwt glyphLayoutProcessor = new GlyphLayoutProcessorAwt();

            try (InputStream in = getClass().getResourceAsStream("/fonts/noto/NotoSansArabic-Regular.ttf")) {
                Assert.assertNotNull("Noto Sans Arabic font resource missing", in);
                PDType0Font font = glyphLayoutProcessor.loadFont(doc, in);

                PdfBoxFSFont fsFont = new PdfBoxFSFont(
                        Collections.singletonList(new PdfBoxFontResolver.FontDescription(
                                doc, () -> font, IdentValue.NORMAL, 400,
                                "Noto Sans Arabic", false, false,
                                FSNoOpCacheStore.INSTANCE)), 24f);

                PdfBoxTextRenderer withoutProcessor = new PdfBoxTextRenderer();
                withoutProcessor.setup(new FontContext() {}, null);

                PdfBoxTextRenderer withProcessor = new PdfBoxTextRenderer();
                withProcessor.setup(new FontContext() {}, null);
                withProcessor.setGlyphLayoutProcessor(glyphLayoutProcessor);

                int widthWithout = withoutProcessor.getWidth(new FontContext() {}, fsFont, arabicText);
                int widthWith = withProcessor.getWidth(new FontContext() {}, fsFont, arabicText);

                Assert.assertNotEquals("Arabic word width should differ when glyph shaping is active", widthWithout, widthWith);
            }
        }
    }

    @Test
    public void getWidthEqualsForLetterA() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            GlyphLayoutProcessorAwt glyphLayoutProcessor = new GlyphLayoutProcessorAwt();

            try (InputStream in = getClass().getResourceAsStream("/fonts/arimo/Arimo-Regular.ttf")) {
                Assert.assertNotNull("Arimo font resource missing", in);
                PDType0Font font = glyphLayoutProcessor.loadFont(doc, in);

                PdfBoxFSFont fsFont = new PdfBoxFSFont(
                        Collections.singletonList(new PdfBoxFontResolver.FontDescription(
                                doc, () -> font, IdentValue.NORMAL, 400,
                                "Arimo", false, false,
                                FSNoOpCacheStore.INSTANCE)), 24f);

                PdfBoxTextRenderer withoutProcessor = new PdfBoxTextRenderer();
                withoutProcessor.setup(new FontContext() {}, null);

                PdfBoxTextRenderer withProcessor = new PdfBoxTextRenderer();
                withProcessor.setup(new FontContext() {}, null);
                withProcessor.setGlyphLayoutProcessor(glyphLayoutProcessor);

                String s = "A";
                int aWidthWithout = withoutProcessor.getWidth(new FontContext() {}, fsFont, s);
                int aWidthWith = withProcessor.getWidth(new FontContext() {}, fsFont, s);

                Assert.assertEquals("Width should be equal when glyph layout processing is enabled for the letter A",
                        aWidthWithout, aWidthWith);
            }
        }
    }

    @Test
    public void getWidthDiffersForMixedArabicLatinTextWhenGlyphLayoutProcessorIsEnabled() throws Exception {
        String text1 = "Guten Tag ";
        String text2 = "السلام عليكم";
        String text3 = " Good afternoon";
        String mixedText = text1 + text2 + text3;

        try (PDDocument doc = new PDDocument()) {
            GlyphLayoutProcessorAwt glyphLayoutProcessor = new GlyphLayoutProcessorAwt();

            try (InputStream in = getClass().getResourceAsStream("/fonts/noto/NotoSansArabic-Regular.ttf")) {
                Assert.assertNotNull("Noto Sans Arabic font resource missing", in);
                PDType0Font font = glyphLayoutProcessor.loadFont(doc, in);

                PdfBoxFSFont fsFont = new PdfBoxFSFont(
                        Collections.singletonList(new PdfBoxFontResolver.FontDescription(
                                doc, () -> font, IdentValue.NORMAL, 400,
                                "Noto Sans Arabic", false, false,
                                FSNoOpCacheStore.INSTANCE)), 24f);

                PdfBoxTextRenderer withoutProcessor = new PdfBoxTextRenderer();
                withoutProcessor.setup(new FontContext() {}, null);

                PdfBoxTextRenderer withProcessor = new PdfBoxTextRenderer();
                withProcessor.setup(new FontContext() {}, null);
                withProcessor.setGlyphLayoutProcessor(glyphLayoutProcessor);

                int widthWithout = withoutProcessor.getWidth(new FontContext() {}, fsFont, mixedText);
                int widthWith = withProcessor.getWidth(new FontContext() {}, fsFont, mixedText);

                Assert.assertNotEquals("Mixed Arabic/Latin width should differ when glyph shaping is active", widthWithout, widthWith);
            }
        }
    }

    @Test
    public void getWidthStaysStableForCombiningMarks() throws Exception {
        String combined = "A\u030B";

        try (PDDocument doc = new PDDocument()) {
            GlyphLayoutProcessorAwt glyphLayoutProcessor = new GlyphLayoutProcessorAwt();

            try (InputStream in = getClass().getResourceAsStream("/fonts/arimo/Arimo-Regular.ttf")) {
                Assert.assertNotNull("Arimo font resource missing", in);
                PDType0Font font = glyphLayoutProcessor.loadFont(doc, in);

                PdfBoxFSFont fsFont = new PdfBoxFSFont(
                        Collections.singletonList(new PdfBoxFontResolver.FontDescription(
                                doc, () -> font, IdentValue.NORMAL, 400,
                                "Arimo", false, false,
                                FSNoOpCacheStore.INSTANCE)), 24f);

                PdfBoxTextRenderer withoutProcessor = new PdfBoxTextRenderer();
                withoutProcessor.setup(new FontContext() {}, null);

                PdfBoxTextRenderer withProcessor = new PdfBoxTextRenderer();
                withProcessor.setup(new FontContext() {}, null);
                withProcessor.setGlyphLayoutProcessor(glyphLayoutProcessor);

                int widthWithout = withoutProcessor.getWidth(new FontContext() {}, fsFont, combined);
                int widthWith = withProcessor.getWidth(new FontContext() {}, fsFont, combined);

                Assert.assertEquals("Combining diacritical marks should not change measurement when shaping is active", widthWithout, widthWith);
            }
        }
    }
}
