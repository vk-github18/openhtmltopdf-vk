package com.openhtmltopdf.pdfbox.glyphlayout.awt;

import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutFontLoaderAwt;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;

/**
 * Font loader for PdfBoxGlyphLayoutProcessorAwt.
 * <p>
 * Returns instances of PdfBoxGlyphLayoutType0Font for correct
 * computation of getStringWidth().
 */
public class PdfBoxGlyphLayoutFontLoaderAwt extends GlyphLayoutFontLoaderAwt {

    public final PdfBoxGlyphLayoutProcessorAwt glyphLayoutProcessor;

    /**
     * Create a PdfBoxGlyphLayoutFontLoaderAwt
     *
     * @param glyphLayoutProcessor glyph layout processor to be used
     */
    public PdfBoxGlyphLayoutFontLoaderAwt(PdfBoxGlyphLayoutProcessorAwt glyphLayoutProcessor) {
        this.glyphLayoutProcessor = glyphLayoutProcessor;
    }

    /**
     * Load the PDType0Font
     * can be overridden by subclasses
     */
    @Override
    protected PDType0Font loadPDType0Font(PDDocument pdDocument, byte[] fontBytes,
                                          boolean embedSubset)
            throws IOException {

        return new PdfBoxGlyphLayoutType0Font(glyphLayoutProcessor, pdDocument, fontBytes,
                embedSubset, false);
    }

}
