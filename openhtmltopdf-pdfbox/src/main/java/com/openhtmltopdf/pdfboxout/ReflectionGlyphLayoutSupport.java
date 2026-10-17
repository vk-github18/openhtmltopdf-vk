package com.openhtmltopdf.pdfboxout;

import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.lang.reflect.Method;

/**
 * Reflection helper to set a PDFBox GlyphLayoutProcessor on a PDPageContentStream
 * when the PDFBox version provides the API. This avoids a compile-time dependency
 * on the glyph-layout API so the library can build against older PDFBox versions.
 */
public final class ReflectionGlyphLayoutSupport {

    private ReflectionGlyphLayoutSupport() {}

    public static void setGlyphLayoutProcessorIfAvailable(PDPageContentStream cs, Object glyphLayoutProcessor) {
        if (cs == null || glyphLayoutProcessor == null) {
            return;
        }

        try {
            Method candidate = null;
            for (Method m : cs.getClass().getMethods()) {
                if (m.getName().equals("setGlyphLayoutProcessor") && m.getParameterCount() == 1) {
                    candidate = m;
                    break;
                }
            }

            if (candidate == null) {
                // Method not available on this PDFBox version
                return;
            }

            candidate.invoke(cs, glyphLayoutProcessor);
        } catch (Throwable t) {
            // Wrap to give a clearer error to callers
            throw new RuntimeException("Failed to set GlyphLayoutProcessor via reflection", t);
        }
    }
}
