package com.openhtmltopdf.pdfbox.glyphlayout.awt;

import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutFontLoaderAwt;
import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutProcessorAwt;

/**
 * Glyph layout processor
 * <p>
 * uses PdfBoxGlyphLayoutFontLoaderAwt providing {@link PdfBoxGlyphLayoutType0Font} for correct
 * computation of getStringWidth
 */
public class PdfBoxGlyphLayoutProcessorAwt extends GlyphLayoutProcessorAwt {


    public PdfBoxGlyphLayoutProcessorAwt(PdfBoxGlyphLayoutProcessorAwt.GlyphLayoutProcessorOptions options) {
        super(options);
    }

    public PdfBoxGlyphLayoutProcessorAwt() {
    }

    @Override
    protected GlyphLayoutFontLoaderAwt createGlyphLayoutFontLoaderAwt() {
        return new PdfBoxGlyphLayoutFontLoaderAwt(this);
    }
}
