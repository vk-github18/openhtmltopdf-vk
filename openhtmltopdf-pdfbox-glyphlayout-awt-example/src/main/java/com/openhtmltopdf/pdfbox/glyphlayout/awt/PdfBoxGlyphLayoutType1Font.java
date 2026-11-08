package com.openhtmltopdf.pdfbox.glyphlayout.awt;

import org.apache.pdfbox.pdmodel.GlyphLayoutProcessorInterface;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.util.Objects;

public class PdfBoxGlyphLayoutType1Font extends PDType0Font {
    private final GlyphLayoutProcessorInterface glyphLayoutProcessor;

    public PdfBoxGlyphLayoutType1Font(PDType0Font type0Font, GlyphLayoutProcessorInterface glyphLayoutProcessor) throws IOException {
        super(type0Font.getCOSObject());
        Objects.requireNonNull(glyphLayoutProcessor, "LayoutProcessor has to be not null");
        this.glyphLayoutProcessor = glyphLayoutProcessor;
    }

    @Override
    public float getStringWidth(String text) throws IOException {
        return glyphLayoutProcessor.getStringWidth((PDType0Font) this, 1.0f, text) / getFontMatrix().getScaleX();
    }
}