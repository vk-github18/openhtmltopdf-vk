package com.openhtmltopdf.pdfboxout.glyphlayout.awt;

import com.openhtmltopdf.pdfboxout.PDFontSupplier;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.AbstractGlyphLayoutProcessor;
import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutProcessorAwt;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Example for PDFBox GlyphLayoutProcessor for correct glyph layout
 *
 * @author Volker Kunert
 */
public class PdfBoxGlyphLayoutExample {
    public static void main(String[] args) throws Exception {
        new PdfBoxGlyphLayoutExample().run("/glyphlayout/NoGlyphLayoutExampleDin91379.html", false, false, false, PdfRendererBuilder.PdfAConformance.NONE);
        new PdfBoxGlyphLayoutExample().run("/glyphlayout/GlyphLayoutExampleDin91379.html", true, false, false, PdfRendererBuilder.PdfAConformance.NONE);
        new PdfBoxGlyphLayoutExample().run("/glyphlayout/GlyphLayoutExampleDin91379.html", true, true, false, PdfRendererBuilder.PdfAConformance.NONE);
        new PdfBoxGlyphLayoutExample().run("/glyphlayout/GlyphLayoutExampleDin91379.html", true, true, true, PdfRendererBuilder.PdfAConformance.PDFA_2_A);
        new PdfBoxGlyphLayoutExample().run("/glyphlayout/GlyphLayoutExampleDin91379.html",  true, true, true, PdfRendererBuilder.PdfAConformance.PDFA_2_B);
        new PdfBoxGlyphLayoutExample().run( "/glyphlayout/GlyphLayoutExampleDin91379.html", true, true, true, PdfRendererBuilder.PdfAConformance.PDFA_2_U);
        new PdfBoxGlyphLayoutExample().run("/glyphlayout/GlyphLayoutExampleDin91379.html",  true, true, true, PdfRendererBuilder.PdfAConformance.PDFA_3_B);
        new PdfBoxGlyphLayoutExample().run("/glyphlayout/GlyphLayoutExampleDin91379.html",  true, true, true, PdfRendererBuilder.PdfAConformance.PDFA_3_U);
        new PdfBoxGlyphLayoutExample().run("/glyphlayout/GlyphLayoutExampleBidi.html",  true, true, true, PdfRendererBuilder.PdfAConformance.PDFA_3_U);
    }

    public void run(String exampleHtmlPath, boolean useGlyphLayoutProcessor, boolean useActualText, boolean accessible, PdfRendererBuilder.PdfAConformance pdfAConformance) throws IOException, FontFormatException {
        String baseName = exampleHtmlPath.replaceAll("^/[^/]*/([^.]*).*$", "$1");

        File out = new File(String.format("%s_%s_%s._%s_%s.pdf", baseName, useGlyphLayoutProcessor?"GlyphLayout":"NoLayout",
                useActualText?"ActualText":"NoActualText", accessible?"Accessible":"NotAccessible", pdfAConformance.toString()));

        try (PDDocument doc = new PDDocument()) {
            GlyphLayoutProcessorAwt glyphLayoutProcessor = new GlyphLayoutProcessorAwt();
            if (useActualText) {
                AbstractGlyphLayoutProcessor.GlyphLayoutProcessorOptions options = new AbstractGlyphLayoutProcessor.GlyphLayoutProcessorOptions();
                options.useActualText();
                glyphLayoutProcessor = new GlyphLayoutProcessorAwt(options);
            }
            PDFont arimo = glyphLayoutProcessor.loadFont(doc, this.getClass().getResourceAsStream("/fonts/arimo/Arimo-Regular.ttf"));
            PDFont arabic = glyphLayoutProcessor.loadFont(doc, this.getClass().getResourceAsStream("/fonts/noto/NotoSansArabic-Regular.ttf"));


            PdfRendererBuilder builder = new PdfRendererBuilder();
            if (useGlyphLayoutProcessor) {
                builder.useGlyphLayoutProcessor(glyphLayoutProcessor);
            }

            // Load example HTML from resources
            InputStream is = PdfBoxGlyphLayoutExample.class.getResourceAsStream(exampleHtmlPath);
            Objects.requireNonNull(is, "Could not find Html resource");

            String html = new String(IOUtils.toByteArray(is), StandardCharsets.UTF_8);

            builder.usePdfUaAccessibility(accessible);
            builder.usePdfAConformance(pdfAConformance);
            builder.withProducer("openhtmltopdf-pdfbox-glyphlayout-example");
            builder.toStream(new FileOutputStream(out));
            builder.usePDDocument(doc);
            builder.useFont(new PDFontSupplier(arimo), "Arimo");
            builder.useFont(new PDFontSupplier(arabic), "Noto Sans Arabic");
            builder.withHtmlContent(html, "");
            builder.run();
        }
    }
}
