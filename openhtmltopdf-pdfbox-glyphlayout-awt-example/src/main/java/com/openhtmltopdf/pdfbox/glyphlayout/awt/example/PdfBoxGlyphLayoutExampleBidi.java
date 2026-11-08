package com.openhtmltopdf.pdfbox.glyphlayout.awt.example;

import com.openhtmltopdf.pdfbox.glyphlayout.awt.PdfBoxGlyphLayoutProcessorAwt;
import com.openhtmltopdf.pdfboxout.PDFontSupplier;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.AbstractGlyphLayoutProcessor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.awt.FontFormatException;
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
public class PdfBoxGlyphLayoutExampleBidi {
    public static final String OUTPUT_DIR_NAME = "openhtmltopdf-pdfbox-glyphlayout-awt-example/target/glyphlayout-example";

    public static void main(String[] args) throws Exception {
        String exampleHtmlPath = "/html/GlyphLayoutExampleBidi.html";
        new PdfBoxGlyphLayoutExampleBidi().run(exampleHtmlPath);
    }

    public void run(String exampleHtmlPath) throws IOException, FontFormatException {
        String baseName = exampleHtmlPath.replaceAll("^/[^/]*/([^.]*).*$", "$1");

        Path outputDir = Paths.get(OUTPUT_DIR_NAME);
        Files.createDirectories(outputDir);
        Path out = outputDir.resolve(String.format("%s.pdf", baseName));


        try (PDDocument doc = new PDDocument();
             OutputStream outputStream = Files.newOutputStream(out)) {
            AbstractGlyphLayoutProcessor.GlyphLayoutProcessorOptions options = new AbstractGlyphLayoutProcessor.GlyphLayoutProcessorOptions();
            options.useActualText();
            PdfBoxGlyphLayoutProcessorAwt glyphLayoutProcessor = new PdfBoxGlyphLayoutProcessorAwt(options);

            PDFont arabic = glyphLayoutProcessor.loadFont(doc, this.getClass().getResourceAsStream("/fonts/noto/NotoSansArabic-Regular.ttf"));
            PDFont arimo = glyphLayoutProcessor.loadFont(doc, this.getClass().getResourceAsStream("/fonts/arimo/Arimo-Regular.ttf"));

            // Load example HTML from resources
            InputStream is = PdfBoxGlyphLayoutExampleBidi.class.getResourceAsStream(exampleHtmlPath);
            Objects.requireNonNull(is, "Could not find Html resource");
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String html = br.lines().collect(Collectors.joining("\n"));

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useGlyphLayoutProcessor(glyphLayoutProcessor)
                    .toStream(outputStream)
                    .usePDDocument(doc)
                    .useFont(new PDFontSupplier(arimo), "Arimo")
                    .useFont(new PDFontSupplier(arabic), "Noto Sans Arabic")
                    .withHtmlContent(html, "")
                    .run();
        }
    }
}
