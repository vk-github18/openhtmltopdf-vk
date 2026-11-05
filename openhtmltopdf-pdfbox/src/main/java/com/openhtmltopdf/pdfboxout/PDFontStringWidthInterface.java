package com.openhtmltopdf.pdfboxout;

import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.IOException;

public interface PDFontStringWidthInterface {
    /*
     * Compute string width for given font
     */
    float getFontStringWidth(PDFont font, String text) throws IOException;
}
