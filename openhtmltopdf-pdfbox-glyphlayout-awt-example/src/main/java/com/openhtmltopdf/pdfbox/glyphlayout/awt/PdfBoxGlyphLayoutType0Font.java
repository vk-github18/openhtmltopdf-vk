package com.openhtmltopdf.pdfbox.glyphlayout.awt;

import org.apache.fontbox.ttf.TTFParser;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.GlyphLayoutProcessorInterface;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.util.Objects;

public class PdfBoxGlyphLayoutType0Font extends PDType0Font {
    private final GlyphLayoutProcessorInterface glyphLayoutProcessor;

    public PdfBoxGlyphLayoutType0Font(GlyphLayoutProcessorInterface glyphLayoutProcessor, PDDocument doc, byte[] fontBytes, boolean embedSubset, boolean vertical) throws IOException {
        super(doc, (new TTFParser()).parse(new RandomAccessReadBuffer(fontBytes)), embedSubset, true, vertical);
        Objects.requireNonNull(glyphLayoutProcessor, "LayoutProcessor has to be not null");
        this.glyphLayoutProcessor = glyphLayoutProcessor;
    }

    @Override
    public float getStringWidth(String text) throws IOException {
        return glyphLayoutProcessor.getStringWidth((PDType0Font) this, 1.0f, text) / getFontMatrix().getScaleX();
    }
}