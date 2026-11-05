package com.openhtmltopdf.pdfbox.glyphlayout.awt;

import com.openhtmltopdf.pdfboxout.PDFontSupplier;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutProcessorAwt;
import org.apache.pdfbox.pdmodel.AbstractGlyphLayoutProcessor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Example for PDFBox GlyphLayoutProcessor for correct glyph layout
 *
 * @author Volker Kunert
 */
public class PdfBoxGlyphLayoutExampleDin91379MissingGlyph {
    public static final String OUTPUT_DIR_NAME = "openhtmltopdf-pdfbox-glyphlayout-awt-example/target/glyphlayout-example";

    public static void main(String[] args) throws Exception {
        new PdfBoxGlyphLayoutExampleDin91379MissingGlyph().run("/html/GlyphLayoutExampleDin91379.html", true);
        new PdfBoxGlyphLayoutExampleDin91379MissingGlyph().run("/html/NoGlyphLayoutExampleDin91379.html", false);
    }

    public void run(String exampleHtmlPath, boolean useGlyphLayoutProcessor) throws IOException, FontFormatException {
        String baseName = exampleHtmlPath.replaceAll("^/[^/]*/([^.]*).*$", "$1");
        Path outputDir = Paths.get(OUTPUT_DIR_NAME);
        Files.createDirectories(outputDir);
        Path out = outputDir.resolve(String.format("%s_%s_MissingGlyph.pdf", baseName,
                useGlyphLayoutProcessor ? "GlyphLayout" : "No_GlyphLayout"));

        try (PDDocument doc = new PDDocument();
             OutputStream outputStream = Files.newOutputStream(out)) {
            AbstractGlyphLayoutProcessor.GlyphLayoutProcessorOptions options = new AbstractGlyphLayoutProcessor.GlyphLayoutProcessorOptions();
            options.useActualText();
            GlyphLayoutProcessorAwt glyphLayoutProcessor = new GlyphLayoutProcessorAwt(options);

            PDFont arimo = glyphLayoutProcessor.loadFont(doc, this.getClass().getResourceAsStream("/fonts/noto/NotoSans-Regular.ttf"));

            // Load example HTML from resources
            InputStream is = PdfBoxGlyphLayoutExampleDin91379MissingGlyph.class.getResourceAsStream(exampleHtmlPath);
            Objects.requireNonNull(is, "Could not find Html resource");
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String html = br.lines().collect(Collectors.joining("\n"));

            PdfRendererBuilder builder = new PdfRendererBuilder();
            if (useGlyphLayoutProcessor) {
                builder.useGlyphLayoutProcessor(glyphLayoutProcessor);
            }
            builder.toStream(outputStream)
                    .usePDDocument(doc)
                    .useFont(new PDFontSupplier(arimo), "Arimo")
                    .withHtmlContent(html, "")
                    .run();
        }
    }
}
