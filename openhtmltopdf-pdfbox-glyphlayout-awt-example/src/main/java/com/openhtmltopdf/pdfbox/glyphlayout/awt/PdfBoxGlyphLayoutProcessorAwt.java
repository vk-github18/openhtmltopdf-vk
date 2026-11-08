package com.openhtmltopdf.pdfbox.glyphlayout.awt;

import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutFontLoaderAwt;
import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutProcessorAwt;
import org.apache.pdfbox.pdmodel.AbstractGlyphLayoutProcessor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;

/**
 * Glyph layout processor
 * <p>
 * loadFont returns instances of PdfBoxGlyphLayoutType1Font for correct
 * computation of getStringWidth
 */
public class PdfBoxGlyphLayoutProcessorAwt extends GlyphLayoutProcessorAwt {

    public PdfBoxGlyphLayoutProcessorAwt(AbstractGlyphLayoutProcessor.GlyphLayoutProcessorOptions options) {
        super(options);
    }

    public PdfBoxGlyphLayoutProcessorAwt() {
    }


    @Override
    public PDType0Font loadFont(PDDocument pdDocument, InputStream inputStream) throws IOException, FontFormatException {
        return wrapFont(super.loadFont(pdDocument, inputStream));
    }

    @Override
    public PDType0Font loadFont(PDDocument pdDocument, InputStream inputStream, boolean embedSubset) throws IOException, FontFormatException {
        return wrapFont(super.loadFont(pdDocument, inputStream, embedSubset));
    }

    @Override
    public PDType0Font loadFont(PDDocument pdDocument, InputStream inputStream, GlyphLayoutFontLoaderAwt.FontOptions fontOptions) throws IOException, FontFormatException {
        return wrapFont(super.loadFont(pdDocument, inputStream, fontOptions));
    }

    @Override
    public PDType0Font loadFont(PDDocument pdDocument, InputStream inputStream, boolean embedSubset, GlyphLayoutFontLoaderAwt.FontOptions fontOptions) throws IOException, FontFormatException {
        return wrapFont(super.loadFont(pdDocument, inputStream, embedSubset, fontOptions));
    }

    protected PdfBoxGlyphLayoutType1Font wrapFont(PDType0Font type0Font) throws IOException {
        return new PdfBoxGlyphLayoutType1Font(type0Font, this);
    }
}
