// Tests with Copilot
package com.openhtmltopdf.pdfbox.glyphlayout.awt;

import com.openhtmltopdf.bidi.SimpleBidiReorderer;
import org.apache.pdfbox.pdmodel.GlyphLayoutProcessorInterface;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.extend.FontContext;
import com.openhtmltopdf.extend.impl.FSNoOpCacheStore;
import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutProcessorAwt;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;



public class PdfBoxTextRendererTest {

    @Test
    public void legacySetupSignatureStillWorks() throws Exception {
        PdfBoxTextRenderer renderer = new PdfBoxTextRenderer();

        renderer.setup(null, new SimpleBidiReorderer());

        Assert.assertNull(getGlyphLayoutProcessor(renderer));
    }

    @Test
    public void setupStoresGlyphLayoutProcessor() throws Exception {
        PdfBoxTextRenderer renderer = new PdfBoxTextRenderer();
        GlyphLayoutProcessorInterface processor = (GlyphLayoutProcessorInterface) Proxy.newProxyInstance(
                GlyphLayoutProcessorInterface.class.getClassLoader(),
                new Class<?>[]{GlyphLayoutProcessorInterface.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "toString":
                            return "glyph-layout-processor";
                        case "hashCode":
                            return 0;
                        case "equals":
                            return proxy == args[0];
                        default:
                            return null;
                    }
                });

        renderer.setup(null, new SimpleBidiReorderer(), processor);

        Assert.assertSame(processor, getGlyphLayoutProcessor(renderer));
    }

    private static Object getGlyphLayoutProcessor(PdfBoxTextRenderer renderer) throws Exception {
        Field field = PdfBoxTextRenderer.class.getDeclaredField("_glyphLayoutProcessor");
        field.setAccessible(true);
        return field.get(renderer);
    }

    @Test
    public void testComplexTextRendersAcrossShadowPages() throws Exception {
        String html =
                "<html><body style='font-family: Noto Sans Arabic;'>" +
                        "<table style='width: 2000px; font-size: 24px;'>" +
                        "<tr><td>العربية العربية العربية ... very long content ...</td></tr>" +
                        "</table></body></html>";

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

        @Test
        public void getWidthDiffersWhenGlyphLayoutProcessorIsEnabled() throws Exception {
            String arabicLigature = "\u0644\u0627"; // "لا" (lam + alef ligature)

            try (PDDocument doc = new PDDocument()) {
                GlyphLayoutProcessorAwt glyphLayoutProcessor = new GlyphLayoutProcessorAwt();

                try (InputStream in = getClass().getResourceAsStream("/fonts/noto/NotoSansArabic-Regular.ttf")) {
                    Assert.assertNotNull("Noto Sans Arabic font resource missing", in);
                    PDType0Font font = glyphLayoutProcessor.loadFont(doc, in);

                    PdfBoxFSFont fsFont = new PdfBoxFSFont(
                            java.util.Collections.singletonList(
                                    new PdfBoxFontResolver.FontDescription(
                                            doc,
                                            () -> font,
                                            IdentValue.NORMAL,
                                            400,
                                            "Noto Sans Arabic",
                                            false,
                                            false,
                                            FSNoOpCacheStore.INSTANCE
                                    )
                            ),
                            24f
                    );

                    PdfBoxTextRenderer withoutProcessor = new PdfBoxTextRenderer();
                    withoutProcessor.setup(new FontContext() {}, null, null);

                    PdfBoxTextRenderer withProcessor = new PdfBoxTextRenderer();
                    withProcessor.setup(new FontContext() {}, null, glyphLayoutProcessor);

                    int widthWithout = withoutProcessor.getWidth(new FontContext() {}, fsFont, arabicLigature);
                    int widthWith = withProcessor.getWidth(new FontContext() {}, fsFont, arabicLigature);

                    Assert.assertNotEquals(
                            "Width should differ when glyph layout processing is enabled for shaped Arabic text",
                            widthWithout,
                            widthWith
                    );
                }
            }
        }
    }
}