package com.openhtmltopdf.pdfboxout.glyphlayout.awt;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutProcessorAwt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Example usage of the optional PDFBox glyph-layout support.
 *
 * This class demonstrates how to enable the PDFBox glyph layout processor when creating a PDF.
 * The processor is optional and only needed when advanced glyph shaping / positioning is desired.
 */
public class PdfBoxGlyphLayoutExample {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: PdfBoxGlyphLayoutExample <output.pdf>");
        }

        File output = new File(args[0]);
        String html = "<html><body style='font-family: sans-serif; font-size: 24px;'>