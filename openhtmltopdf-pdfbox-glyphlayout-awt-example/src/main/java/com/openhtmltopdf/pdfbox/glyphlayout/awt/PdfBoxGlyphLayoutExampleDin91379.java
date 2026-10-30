package com.openhtmltopdf.pdfbox.glyphlayout.awt;

import com.openhtmltopdf.pdfboxout.PDFontSupplier;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutProcessorAwt;
import org.apache.pdfbox.pdmodel.AbstractGlyphLayoutProcessor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.awt.*;
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
public class PdfBoxGlyphLayoutExampleDin91379 {
    public static void main(String[] args) throws Exception {
        new PdfBoxGlyphLayoutExampleDin91379().run("/html/GlyphLayoutExampleDin91379.html", true);
        new PdfBoxGlyphLayoutExampleDin91379().run("/html/NoGlyphLayoutExampleDin91379.html", false);
    }

    public void run(String exampleHtmlPath, boolean useGlyphLayoutProcessor) throws IOException, FontFormatException {
        String baseName = exampleHtmlPath.replaceAll("^/[^/]*/([^.]*).*$", "$1");

        File out = new File(String.format("%s_%s.pdf", baseName,
                useGlyphLayoutProcessor ? "GlyphLayout" : "No_GlyphLayout"));

        try (PDDocument doc = new PDDocument()) {
            AbstractGlyphLayoutProcessor.GlyphLayoutProcessorOptions options = new AbstractGlyphLayoutProcessor.GlyphLayoutProcessorOptions();
            options.useActualText();
            GlyphLayoutProcessorAwt glyphLayoutProcessor = new GlyphLayoutProcessorAwt(options);

            PDFont arimo = glyphLayoutProcessor.loadFont(doc, this.getClass().getResourceAsStream("/fonts/arimo/Arimo-Regular.ttf"));

            // Load example HTML from resources
            InputStream is = PdfBoxGlyphLayoutExampleDin91379.class.getResourceAsStream(exampleHtmlPath);
            Objects.requireNonNull(is, "Could not find Html resource");
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String html = br.lines().collect(Collectors.joining("\n"));

            PdfRendererBuilder builder = new PdfRendererBuilder();
            if (useGlyphLayoutProcessor) {
                builder.useGlyphLayoutProcessor(glyphLayoutProcessor);
            }
            builder.toStream(Files.newOutputStream(out.toPath()))
                    .usePDDocument(doc)
                    .useFont(new PDFontSupplier(arimo), "Arimo")
                    .withHtmlContent(html, "")
                    .run();
        }
    }
}

