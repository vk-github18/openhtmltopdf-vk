// Tests built with Copilot assistance
package com.openhtmltopdf.pdfbox.glyphlayout.awt;

import com.openhtmltopdf.pdfboxout.PdfBoxFSFont;
import com.openhtmltopdf.pdfboxout.PdfBoxFontResolver;
import com.openhtmltopdf.pdfboxout.PdfBoxTextRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.extend.FontContext;
import com.openhtmltopdf.extend.impl.FSNoOpCacheStore;
import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutProcessorAwt;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;


public class PdfBoxGlyphLayoutTest {

    @Test
    public void testComplexTextRendersAcrossShadowPages() throws Exception {
        String exampleHtmlPath = "/html/NoGlyphLayoutExampleDin91379.html";

        InputStream is = PdfBoxGlyphLayoutExample.class.getResourceAsStream(exampleHtmlPath);
        Objects.requireNonNull(is, "Could not find Html resource "+ exampleHtmlPath);

        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        String lines = br.lines().filter(s->!s.matches("</?html")).collect(Collectors.joining("\n"));
        String html = "<html lang=\"en\" xmlns=\"http://www.w3.org/1999/html\">\n"
                + lines
                + lines
                + "</html>\n";

        try (PDDocument doc = new PDDocument()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            PdfRendererBuilder builder = new PdfRendererBuilder()
                    .usePDDocument(doc)
                    .useGlyphLayoutProcessor(new GlyphLayoutProcessorAwt())
                    .withHtmlContent(html, null)
                    .toStream(out);

            builder.run();

            Assert.assertTrue(doc.getNumberOfPages() > 0);
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
                        java.util.Collections.singletonList(
                                new PdfBoxFontResolver.FontDescription(
                                        doc,
                                        () -> font,
                                        IdentValue.NORMAL,
                                        400,
                                        "Arimo",
                                        false,
                                        false,
                                        FSNoOpCacheStore.INSTANCE
                                )
                        ),
                        24f
                );

                PdfBoxTextRenderer withoutProcessor = new PdfBoxTextRenderer();
                withoutProcessor.setup(new FontContext() {
                }, null);

                PdfBoxTextRenderer withProcessor = new PdfBoxTextRenderer();
                withProcessor.setup(new FontContext() {
                }, null);
                withProcessor.setGlyphLayoutProcessor(glyphLayoutProcessor);


                String s = "A";
                int aWidthWithout = withoutProcessor.getWidth(new FontContext() {
                }, fsFont, s);
                int aWidthWith = withProcessor.getWidth(new FontContext() {
                }, fsFont, s);

                Assert.assertEquals(
                        "Width should be equal when glyph layout processing is enabled for the letter A",
                        aWidthWithout,
                        aWidthWith
                );
            }
        }
    }
}
