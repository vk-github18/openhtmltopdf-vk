package com.openhtmltopdf.pdfbox.glyphlayout.awt;

import com.openhtmltopdf.pdfboxout.PDFontSupplier;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.AbstractGlyphLayoutProcessor;
import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutProcessorAwt;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.awt.FontFormatException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Example for PDFBox GlyphLayoutProcessor for correct glyph layout
 *
 * @author Volker Kunert
 */
public class PdfBoxGlyphLayoutExample {
    public static void main(String[] args) throws Exception {
        String[] exampleHtmlPathArray = new String[]{
                "/html/NoGlyphLayoutExampleDin91379.html",
                "/html/GlyphLayoutExampleBidi.html"};
        boolean[] useGlyphLayoutProcessorArray = new boolean[] {/*false,*/ true};
        boolean[] useActualTextArray = new boolean[] {false, true};
        boolean[] accessibleArray = new boolean[] {false, true};
        PdfRendererBuilder.PdfAConformance[] pdfAConformanceArray = new PdfRendererBuilder.PdfAConformance[]
                {PdfRendererBuilder.PdfAConformance.NONE, PdfRendererBuilder.PdfAConformance.PDFA_2_A, PdfRendererBuilder.PdfAConformance.PDFA_2_B, PdfRendererBuilder.PdfAConformance.PDFA_2_U,
                        PdfRendererBuilder.PdfAConformance.PDFA_3_B, PdfRendererBuilder.PdfAConformance.PDFA_3_U};

        for (String exampleHtmlPath: exampleHtmlPathArray) {
            for (boolean useGlyphLayoutProcessor: useGlyphLayoutProcessorArray) {
                for (boolean useActualText: useActualTextArray) {
                    for (boolean accessible: accessibleArray) {
                        for (PdfRendererBuilder.PdfAConformance pdfAConformance: pdfAConformanceArray) {
                            new PdfBoxGlyphLayoutExample().run(exampleHtmlPath, useGlyphLayoutProcessor, useActualText, accessible, pdfAConformance);
                            if (!accessible) {
                                break;
                            }
                        }
                    }

                }
            }
        }
    }

    public void run(String exampleHtmlPath, boolean useGlyphLayoutProcessor, boolean useActualText, boolean accessible, PdfRendererBuilder.PdfAConformance pdfAConformance) throws IOException, FontFormatException {
        String baseName = exampleHtmlPath.replaceAll("^/[^/]*/([^.]*).*$", "$1");

        File out = new File(String.format("%s_%s_%s._%s_%s.pdf", baseName, useGlyphLayoutProcessor?"GlyphLayout":"NoLayout",
                useActualText?"ActualText":"NoActualText", accessible?"Accessible":"NotAccessible", pdfAConformance.toString()));

        try (PDDocument doc = new PDDocument()) {
            AbstractGlyphLayoutProcessor.GlyphLayoutProcessorOptions options = new AbstractGlyphLayoutProcessor.GlyphLayoutProcessorOptions();
            if (useActualText) {
                options.useActualText();
            }
            GlyphLayoutProcessorAwt glyphLayoutProcessor = new GlyphLayoutProcessorAwt(options);

            PDFont arimo = glyphLayoutProcessor.loadFont(doc, this.getClass().getResourceAsStream("/fonts/arimo/Arimo-Regular.ttf"));
            PDFont arabic = glyphLayoutProcessor.loadFont(doc, this.getClass().getResourceAsStream("/fonts/noto/NotoSansArabic-Regular.ttf"));


            PdfRendererBuilder builder = new PdfRendererBuilder();
            if (useGlyphLayoutProcessor) {
                builder.useGlyphLayoutProcessor(glyphLayoutProcessor);
            }

            // Load example HTML from resources
            InputStream is = PdfBoxGlyphLayoutExample.class.getResourceAsStream(exampleHtmlPath);
            Objects.requireNonNull(is, "Could not find Html resource");

            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String html = br.lines().collect(Collectors.joining("\n"));

            builder.usePdfUaAccessibility(accessible)
                .usePdfAConformance(pdfAConformance)
                .withProducer("openhtmltopdf-pdfbox-glyphlayout-example")
                .toStream(Files.newOutputStream(out.toPath()))
                .usePDDocument(doc)
                .useFont(new PDFontSupplier(arimo), "Arimo")
                .useFont(new PDFontSupplier(arabic), "Noto Sans Arabic")
                .withHtmlContent(html, "")
                .run();
        }
    }
}
