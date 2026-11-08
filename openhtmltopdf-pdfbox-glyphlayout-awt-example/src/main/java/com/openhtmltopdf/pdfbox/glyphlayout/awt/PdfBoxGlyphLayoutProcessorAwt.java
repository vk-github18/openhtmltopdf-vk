package com.openhtmltopdf.pdfbox.glyphlayout.awt;

import org.apache.fontbox.ttf.TTFParser;
import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutFontLoaderAwt;
import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutProcessorAwt;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
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

    private final PdfBoxGlyphLayoutFontLoaderAwt glyphLayoutFontLoaderAwt = new PdfBoxGlyphLayoutFontLoaderAwt();


    public PdfBoxGlyphLayoutProcessorAwt(AbstractGlyphLayoutProcessor.GlyphLayoutProcessorOptions options) {
        super(options);
    }

    public PdfBoxGlyphLayoutProcessorAwt() {
    }

// No constructor where I could set PdfBoxGlyphLayoutFontLoaderAwt in GlyphLayoutProcessorAwt
// No useful public or protected constructor in PFType0Font
    ///////////////////////77
/// 1. Font nicht richtig erzeugt
/// 2. supportsFont und die anderen Methoden werden nicht funktionieren
///////////////////////77



    @Override
    public PDType0Font loadFont(PDDocument pdDocument, InputStream inputStream) throws IOException, FontFormatException {
        return glyphLayoutFontLoaderAwt.loadFont(pdDocument, inputStream);
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

}
