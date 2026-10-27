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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;


public class PdfBoxGlyphLayoutTest {

    /**
     * Test rendering of a document longer than one page
     * <p>
     * The resulting PDF file has to be checked visually
     * @throws Exception
     */
    @Test
    public void testComplexTextRendersAcrossShadowPages() throws Exception {
        String exampleHtmlPath = "/html/GlyphLayoutExampleDin91379.html";

        InputStream is = PdfBoxGlyphLayoutExampleBidi.class.getResourceAsStream(exampleHtmlPath);
        Objects.requireNonNull(is, "Could not find Html resource " + exampleHtmlPath);

        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

        String lines = br.lines().filter(s -> !s.matches("</?html.*")).collect(Collectors.joining("\n"));
        String html = "<html lang=\"en\" xmlns=\"http://www.w3.org/1999/html\">\n" + lines + lines + "\n</html>\n";


        InputStream fontIs = getClass().getResourceAsStream("/fonts/arimo/Arimo-Regular.ttf");
        Assert.assertNotNull("Arimo font resource missing", fontIs);

        try (   PDDocument doc = new PDDocument();
                FileOutputStream out = new FileOutputStream("testComplexTextRendersAcrossShadowPages.pdf")) {

            GlyphLayoutProcessorAwt glyphLayoutProcessor = new GlyphLayoutProcessorAwt();
            PDType0Font arimo = glyphLayoutProcessor.loadFont(doc, fontIs); // fonts must be loaded by GlyphLayoutProcessor

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useGlyphLayoutProcessor(glyphLayoutProcessor).toStream(out).usePDDocument(doc).useFont(new PDFontSupplier(arimo), "Arimo").withHtmlContent(html, "").run();

            Assert.assertTrue(doc.getNumberOfPages() > 2);
        }
    }

    /**
     * Test, that the computed width differs for an arabic ligature
     * with and without GlyphLayoutProcessorAwt
     * <p>
     * Created with Copilot
     *
     * @throws Exception
     */
    @Test
    public void getWidthDiffersWhenGlyphLayoutProcessorIsEnabled() throws Exception {
        String arabicLigature = "\u0644\u0627"; // "لا" (lam + alef ligature)

        try (PDDocument doc = new PDDocument()) {
            GlyphLayoutProcessorAwt glyphLayoutProcessor = new GlyphLayoutProcessorAwt();

            try (InputStream in = getClass().getResourceAsStream("/fonts/noto/NotoSansArabic-Regular.ttf")) {
                Assert.assertNotNull("Noto Sans Arabic font resource missing", in);
                PDType0Font font = glyphLayoutProcessor.loadFont(doc, in);

                PdfBoxFSFont fsFont = new PdfBoxFSFont(
                        Collections.singletonList(new PdfBoxFontResolver.
                                FontDescription(doc, () -> font, IdentValue.NORMAL, 400,
                                "Noto Sans Arabic", false, false,
                                FSNoOpCacheStore.INSTANCE)), 24f);

                PdfBoxTextRenderer withoutProcessor = new PdfBoxTextRenderer();
                withoutProcessor.setup(new FontContext() {
                }, null);

                PdfBoxTextRenderer withProcessor = new PdfBoxTextRenderer();
                withProcessor.setup(new FontContext() {
                }, null);
                withoutProcessor.setGlyphLayoutProcessor(glyphLayoutProcessor);

                int widthWithout = withoutProcessor.getWidth(new FontContext() {
                }, fsFont, arabicLigature);
                int widthWith = withProcessor.getWidth(new FontContext() {
                }, fsFont, arabicLigature);

                Assert.assertNotEquals("Width should differ when glyph layout processing " +
                        "is enabled for shaped Arabic text", widthWithout, widthWith);
            }
        }
    }

    /**
     * Test, that the computed width is equal for the letter "A"
     * with and without GlyphLayoutProcessorAwt
     * <p>
     * Created with Copilot
     *
     * @throws Exception
     */
    @Test
    public void getWidthEqualsForLetterA() throws Exception {

        try (PDDocument doc = new PDDocument()) {
            GlyphLayoutProcessorAwt glyphLayoutProcessor = new GlyphLayoutProcessorAwt();

            try (InputStream in = getClass().getResourceAsStream(
                    "/fonts/arimo/Arimo-Regular.ttf")) {
                Assert.assertNotNull("Arimo font resource missing", in);
                PDType0Font font = glyphLayoutProcessor.loadFont(doc, in);

                PdfBoxFSFont fsFont = new PdfBoxFSFont(
                        Collections.singletonList(new PdfBoxFontResolver
                                .FontDescription(doc, () -> font, IdentValue.NORMAL, 400,
                                "Arimo", false, false,
                                FSNoOpCacheStore.INSTANCE)), 24f);

                PdfBoxTextRenderer withoutProcessor = new PdfBoxTextRenderer();
                withoutProcessor.setup(new FontContext() {
                }, null);

                PdfBoxTextRenderer withProcessor = new PdfBoxTextRenderer();
                withProcessor.setup(new FontContext() {
                }, null);
                withProcessor.setGlyphLayoutProcessor(glyphLayoutProcessor);


                String s = "A";
                int aWidthWithout = withoutProcessor.getWidth(
                        new FontContext() {}, fsFont, s);
                int aWidthWith = withProcessor.getWidth(
                        new FontContext() {}, fsFont, s);

                Assert.assertEquals("Width should be equal when glyph layout " +
                        "processing is enabled for the letter A", aWidthWithout, aWidthWith);
            }
        }
    }
}
