/*
 * {{{ header & license
 * Copyright (c) 2006 Wisconsin Court System
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
package com.openhtmltopdf.pdfboxout;

import com.openhtmltopdf.bidi.BidiReorderer;
import com.openhtmltopdf.bidi.BidiSplitter;
import com.openhtmltopdf.bidi.BidiSplitterFactory;
import com.openhtmltopdf.bidi.SimpleBidiReorderer;
import com.openhtmltopdf.context.StyleReference;
import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.css.style.CalculatedStyle;
import com.openhtmltopdf.extend.*;
import com.openhtmltopdf.layout.BoxBuilder;
import com.openhtmltopdf.layout.Layer;
import com.openhtmltopdf.layout.LayoutContext;
import com.openhtmltopdf.layout.SharedContext;
import com.openhtmltopdf.outputdevice.helper.BaseDocument;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceType;
import com.openhtmltopdf.outputdevice.helper.PageDimensions;
import com.openhtmltopdf.outputdevice.helper.UnicodeImplementation;
import com.openhtmltopdf.pdfboxout.PdfBoxUtil.Metadata;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder.CacheStore;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder.PdfAConformance;
import com.openhtmltopdf.render.BlockBox;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.PageBox;
import com.openhtmltopdf.render.RenderingContext;
import com.openhtmltopdf.render.ViewportBox;
import com.openhtmltopdf.render.displaylist.DisplayListCollector;
import com.openhtmltopdf.render.displaylist.DisplayListContainer;
import com.openhtmltopdf.render.displaylist.DisplayListPainter;
import com.openhtmltopdf.render.displaylist.PagedBoxCollector;
import com.openhtmltopdf.render.displaylist.DisplayListContainer.DisplayListPageContainer;
import com.openhtmltopdf.resource.XMLResource;
import com.openhtmltopdf.simple.extend.XhtmlNamespaceHandler;
import com.openhtmltopdf.util.LogMessageId;
import com.openhtmltopdf.util.OpenUtil;
import com.openhtmltopdf.util.ThreadCtx;
import com.openhtmltopdf.util.XRLog;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdfwriter.compress.COSWriterCompressionPool;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.apache.pdfbox.pdmodel.encryption.PDEncryption;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.*;
import org.apache.xmpbox.type.BadFieldValueException;
import org.apache.xmpbox.xml.XmpSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.transform.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PdfBoxRenderer implements Closeable, PageSupplier {
    // See discussion of units at top of PdfBoxOutputDevice.
    private static final float DEFAULT_DOTS_PER_POINT = 20f * 4f / 3f;
    private static final int DEFAULT_DOTS_PER_PIXEL = 20;
    private static final int DEFAULT_PDF_POINTS_PER_INCH = 72;

    private final SharedContext _sharedContext;
    private final PdfBoxOutputDevice _outputDevice;
    private final List<FSDOMMutator> _domMutators;
    private final NamespaceHandler _namespaceHandler;
    private final Object _glyphLayoutProcessor;

    private Document _doc;
    private BlockBox _root;

    private final float _dotsPerPoint;

    private PDDocument _pdfDoc;
    private PDEncryption _pdfEncryption;
    private String _producer;
    private float _pdfVersion;
    private PdfAConformance _pdfAConformance;
    private boolean _pdfUaConformance;
    private byte[] _colorProfile;
    private boolean _testMode;
    private PDFCreationListener _listener;
    private OutputStream _os;
    private SVGDrawer _svgImpl;
    private SVGDrawer _mathmlImpl;
    private BidiSplitterFactory _splitterFactory;
    private byte _defaultTextDirection = BidiSplitter.LTR;
    private BidiReorderer _reorderer;
    private PageSupplier _pageSupplier;
    private final Closeable _diagnosticConsumer;
    private Hyphenator _hyphenator;
    private final int _initialPageNumber;

    PdfBoxRenderer(
            BaseDocument doc,
            UnicodeImplementation unicode,
            PageDimensions pageSize,
            PdfRendererBuilderState state,
            Closeable diagnosticConsumer) {

        PdfBoxFontResolver fontResolver = null;
        _pdfDoc = state.pddocument != null ? state.pddocument : new PDDocument();

        try {
            _diagnosticConsumer = diagnosticConsumer;

            _pdfDoc.setVersion(state._pdfVersion);
            _pdfVersion = state._pdfVersion;

            _producer = state._producer;

            _pageSupplier = state._pageSupplier != null ? state._pageSupplier : this;
            _namespaceHandler = state._namespaceHandler != null ? state._namespaceHandler : new XhtmlNamespaceHandler();

            _svgImpl = state._svgImpl;
            _mathmlImpl = state._mathmlImpl;

            _pdfAConformance = state._pdfAConformance;
            _pdfUaConformance = state._pdfUaConform;
            _colorProfile = state._colorProfile;

            _hyphenator = state._hyphenator;
            _glyphLayoutProcessor = state._glyphLayoutProcessor;

            _dotsPerPoint = DEFAULT_DOTS_PER_POINT;
            _testMode = state._testMode;
            _outputDevice =
                    new PdfBoxFastOutputDevice(DEFAULT_DOTS_PER_POINT, _testMode,
                            state._pdfUaConform || state._pdfAConformance.getConformanceValue().equals("A"),
                            state._pdfAConformance);
            _outputDevice.setWriter(_pdfDoc);
            _outputDevice.setStartPageNo(_pdfDoc.getNumberOfPages());

            PdfBoxUserAgent userAgent = new PdfBoxUserAgent(_outputDevice);

            if (_svgImpl != null) {
                _svgImpl.withUserAgent(userAgent);
                // So that SVGs used as CSS images, such as a background-image, can be drawn too.
                userAgent.setSVGDrawer(_svgImpl);
            }

            userAgent.setProtocolsStreamFactory(state._streamFactoryMap);

            if (state._resolver != null) {
                userAgent.setUriResolver(state._resolver);
            }

            userAgent.setAccessController(ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI, state._beforeAccessController);
            userAgent.setAccessController(ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI, state._afterAccessController);

            _sharedContext = new SharedContext();
            _sharedContext.registerWithThread();

            _sharedContext._preferredTransformerFactoryImplementationClass = state._preferredTransformerFactoryImplementationClass;
            _sharedContext._preferredDocumentBuilderFactoryImplementationClass = state._preferredDocumentBuilderFactoryImplementationClass;

            _sharedContext.setUserAgentCallback(userAgent);
            _sharedContext.setCss(new StyleReference(userAgent));
            userAgent.setSharedContext(_sharedContext);
            _outputDevice.setSharedContext(_sharedContext);

            fontResolver = new PdfBoxFontResolver(_sharedContext, _pdfDoc, state._caches.get(CacheStore.PDF_FONT_METRICS), state._pdfAConformance, state._pdfUaConform);
            _sharedContext.setFontResolver(fontResolver);

            PdfBoxReplacedElementFactory replacedElementFactory = new PdfBoxReplacedElementFactory(_outputDevice, state._svgImpl, state._objectDrawerFactory, state._mathmlImpl);
            _sharedContext.setReplacedElementFactory(replacedElementFactory);

            _sharedContext.setTextRenderer(new PdfBoxTextRenderer());
            _sharedContext.setDPI(DEFAULT_PDF_POINTS_PER_INCH * _dotsPerPoint);
            _sharedContext.setDotsPerPixel(DEFAULT_DOTS_PER_PIXEL);
            _sharedContext.setPrint(true);
            _sharedContext.setInteractive(false);

            this.getSharedContext().setDefaultPageSize(pageSize.w, pageSize.h, pageSize.isSizeInches);

            if (state._replacementText != null) {
                this.getSharedContext().setReplacementText(state._replacementText);
            }

            if (unicode.splitterFactory != null) {
                this._splitterFactory = unicode.splitterFactory;
            }

            if (unicode.reorderer != null) {
                this._reorderer = unicode.reorderer;
                this._outputDevice.setBidiReorderer(_reorderer);
            }

            if (unicode.lineBreaker != null) {
                _sharedContext.setLineBreaker(unicode.lineBreaker);
            }

            if (unicode.charBreaker != null) {
                _sharedContext.setCharacterBreaker(unicode.charBreaker);
            }

            if (unicode.toLowerTransformer != null) {
                _sharedContext.setUnicodeToLowerTransformer(unicode.toLowerTransformer);
            }

            if (unicode.toUpperTransformer != null) {
                _sharedContext.setUnicodeToUpperTransformer(unicode.toUpperTransformer);
            }

            if (unicode.toTitleTransformer != null) {
                _sharedContext.setUnicodeToTitleTransformer(unicode.toTitleTransformer);
            }

            if(_hyphenator != null) {
                _sharedContext.setHyphenator(_hyphenator);
            }

            this._defaultTextDirection = unicode.textDirection ? BidiSplitter.RTL : BidiSplitter.LTR;

            this._domMutators = state._domMutators;

            if (doc.html != null) {
                this.setDocumentFromStringP(doc.html, doc.baseUri);
            }
            else if (doc.document != null) {
                this.setDocumentP(doc.document, doc.baseUri);
            }
            else if (doc.uri != null) {
                this.setDocumentP(doc.uri);
            }
            else if (doc.file != null) {
                try {
                    this.setDocumentP(doc.file);
                } catch (IOException e) {
                    XRLog.log(Level.WARNING, LogMessageId.LogMessageId0Param.EXCEPTION_PROBLEM_TRYING_TO_READ_INPUT_XHTML_FILE, e);
                    throw new RuntimeException("File IO problem", e);
                }
            }

            this._os = state._os;
            this._initialPageNumber = state._initialPageNumber;
        } catch (Throwable e) {
            if (state.pddocument == null) {
                OpenUtil.closeQuietly(_pdfDoc);
            }

            OpenUtil.closeQuietly(fontResolver);

            throw e;
        }
    }

    private PDPageContentStream initPage(
            PDDocument doc, float w, float h, int mainPageIndex, int shadowPageIndex) throws IOException {

        PDPage page = _pageSupplier.requestPage(doc, w, h, mainPageIndex, shadowPageIndex);

        PDPageContentStream cs = new PDPageContentStream(doc, page, AppendMode.APPEND, !_testMode);
        ReflectionGlyphLayoutSupport.setGlyphLayoutProcessorIfAvailable(cs, _glyphLayoutProcessor);

        _outputDevice.initializePage(cs, page, h);

        return cs;
    }

    private void paintShadowPages(
            RenderingContext c,
            PDDocument doc,
            int mainPageIndex,
            PageBox currentPage,
            List<DisplayListPageContainer> shadows) throws IOException {

        int count = shadows.size();

        currentPage.setShadowPageCount(count);

        int pageContentWidth = currentPage.getContentWidth(c);
        int translateIncrement = pageContentWidth * (currentPage.getCutOffPageDirection() == IdentValue.LTR ? 1 : -1);
        int translateX = translateIncrement;

        for (int i = 0; i < count; i++) {
            DisplayListPageContainer shadow = shadows.get(i);

            float shadowWidth = currentPage.getWidth(c) / _dotsPerPoint;
            float shadowHeight = currentPage.getHeight(c) / _dotsPerPoint;

            PDPage shadowPdPage = 
                _pageSupplier.requestPage(doc, shadowWidth, shadowHeight, mainPageIndex, i);

            try (PDPageContentStream shadowCs = new PDPageContentStream(doc, shadowPdPage, AppendMode.APPEND, !_testMode)) {
                ReflectionGlyphLayoutSupport.setGlyphLayoutProcessorIfAvailable(shadowCs, _glyphLayoutProcessor);
                _outputDevice.initializePage(shadowCs, shadowPdPage, shadowHeight);
                c.setShadowPageNumber(i);

                paintPageFast(c, currentPage, shadow, -translateX);

                _outputDevice.finishPage();
            }

            translateX += translateIncrement;
        }
    }

    // rest of class unchanged from original project
}
