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
        new PdfBoxGlyphLayoutExample().run(false, "", false, PdfRendererBuilder.PdfAConformance.NONE);
        new PdfBoxGlyphLayoutExample().run(true, "_ActualText", false, PdfRendererBuilder.PdfAConformance.NONE);
        new PdfBoxGlyphLayoutExample().run(true, "_ActualText", true, PdfRendererBuilder.PdfAConformance.PDFA_2_A);
        new PdfBoxGlyphLayoutExample().run(true, "_ActualText", true, PdfRendererBuilder.PdfAConformance.PDFA_2_B);
        new PdfBoxGlyphLayoutExample().run(true, "_ActualText", true, PdfRendererBuilder.PdfAConformance.PDFA_2_U);
        new PdfBoxGlyphLayoutExample().run(true, "_ActualText", true, PdfRendererBuilder.PdfAConformance.PDFA_3_B);
        new PdfBoxGlyphLayoutExample().run(true, "_ActualText", true, PdfRendererBuilder.PdfAConformance.PDFA_3_U);
    }

    public void run(boolean useActualText, String sActualText, boolean accessible, PdfRendererBuilder.PdfAConformance pdfAConformance) throws IOException, FontFormatException {
        File out = new File(String.format("GlyphLayoutHtmlExample%s_%s.pdf", sActualText, pdfAConformance.toString()));

        try (PDDocument doc = new PDDocument()) {
            GlyphLayoutProcessorAwt glyphLayoutProcessor = new GlyphLayoutProcessorAwt();
            if (useActualText) {
                AbstractGlyphLayoutProcessor.GlyphLayoutProcessorOptions options = new AbstractGlyphLayoutProcessor.GlyphLayoutProcessorOptions();
                options.useActualText();
                glyphLayoutProcessor = new GlyphLayoutProcessorAwt(options);
            }
            PDFont arimo = glyphLayoutProcessor.loadFont(doc, this.getClass().getResourceAsStream("/fonts/arimo/Arimo-Regular.ttf"));

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useGlyphLayoutProcessor(glyphLayoutProcessor);

            // Load example HTML from resources
            InputStream is = PdfBoxGlyphLayoutExample.class.getResourceAsStream("/glyphlayout/GlyphLayoutExample.html");
            Objects.requireNonNull(is, "Could not find GlyphLayoutExample.html resource");


            String html = new String(IOUtils.toByteArray(is), StandardCharsets.UTF_8);

            builder.usePdfUaAccessibility(accessible);
            builder.usePdfAConformance(pdfAConformance);
            // Remember to add one or more f
            builder.withProducer("openhtmltopdf-pdfbox-glyphlayout-example");
            builder.toStream(new FileOutputStream(out));
            builder.usePDDocument(doc);
            builder.useFont(new PDFontSupplier(arimo), "Arimo");
            builder.withHtmlContent(html, "");
            builder.run();
        }
    }
}