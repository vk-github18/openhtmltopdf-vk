package com.openhtmltopdf.pdfboxout;

import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.IOException;

public class PDFontDefaultStringWidth implements PDFontStringWidthInterface {
    /*
     * Compute string width for given font with default algorithm
     */
    public float getFontStringWidth(PDFont font, String text) throws IOException {
        return font.getStringWidth(text);
    }
}
