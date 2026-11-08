package com.openhtmltopdf.pdfbox.glyphlayout.awt;

import org.apache.pdfbox.glyphlayout.awt.GlyphLayoutFontLoaderAwt;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class PdfBoxGlyphLayoutFontLoaderAwt extends GlyphLayoutFontLoaderAwt {
    /**
     * Loads the AWT font needed for layout
     *
     * @param pdDocument document
     * @param inputStream of the font
     * @param embedSubset True if the font will be subset before embedding. Set this to false when
     * creating a font for AcroForm.
     * @param fontOptions Options for font
     * @return pdType0Font PDFBox font
     * @throws IOException if font can not be loaded
     * @throws FontFormatException if the font is bad
     */
    public PDType0Font loadFont(PDDocument pdDocument, InputStream inputStream, boolean embedSubset, FontOptions fontOptions)
            throws IOException, FontFormatException
    {
        Objects.requireNonNull(inputStream, "InputStream must not be null");

        // Copy font stream into memory to read it twice for creation of PDType0Font and AWT Font
        byte[] fontBytes = IOUtils.toByteArray(inputStream);
        PDType0Font pdType0Font = PDType0Font.load(pdDocument,
                new RandomAccessReadBuffer(fontBytes), embedSubset, false);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(fontBytes))
        {
            loadAwtFont(pdType0Font, bais, fontOptions);
        }
        return pdType0Font;
    }
}
