package com.desperadoboi.imagetopdf.document.word;

import com.desperadoboi.imagetopdf.document.DocumentLimits;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

final class WordXml {
    static final String WORD_TRANSITIONAL =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    static final String WORD_STRICT =
            "http://purl.oclc.org/ooxml/wordprocessingml/main";
    private static final String MC =
            "http://schemas.openxmlformats.org/markup-compatibility/2006";
    private static final String PACKAGE_RELATIONSHIPS_TRANSITIONAL =
            "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String PACKAGE_RELATIONSHIPS_STRICT =
            "http://purl.oclc.org/ooxml/package/relationships";
    private static final String CONTENT_TYPES_TRANSITIONAL =
            "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String CONTENT_TYPES_STRICT =
            "http://purl.oclc.org/ooxml/package/content-types";
    private static final String DRAWING_MAIN_TRANSITIONAL =
            "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String DRAWING_MAIN_STRICT =
            "http://purl.oclc.org/ooxml/drawingml/main";
    private static final String DRAWING_WORD_TRANSITIONAL =
            "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
    private static final String DRAWING_WORD_STRICT =
            "http://purl.oclc.org/ooxml/drawingml/wordprocessingDrawing";
    private static final String DRAWING_PICTURE_TRANSITIONAL =
            "http://schemas.openxmlformats.org/drawingml/2006/picture";
    private static final String DRAWING_PICTURE_STRICT =
            "http://purl.oclc.org/ooxml/drawingml/picture";
    private static final String OFFICE_RELATIONSHIPS_TRANSITIONAL =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String OFFICE_RELATIONSHIPS_STRICT =
            "http://purl.oclc.org/ooxml/officeDocument/relationships";
    private static final String VML = "urn:schemas-microsoft-com:vml";
    private static final String PROCESS_DOCDECL =
            "http://xmlpull.org/v1/doc/features.html#process-docdecl";

    private WordXml() {
    }

    static XmlPullParser newParser(InputStream inputStream)
            throws XmlPullParserException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        } catch (XmlPullParserException ignored) {
            // The namespace-aware factory setting remains authoritative.
        }
        try {
            parser.setFeature(PROCESS_DOCDECL, false);
        } catch (XmlPullParserException ignored) {
            // DOCDECL is rejected while tokens are consumed as a second line of defense.
        }
        parser.setInput(inputStream, null);
        return parser;
    }

    static int next(XmlPullParser parser, Budget budget)
            throws IOException, XmlPullParserException, WordParseException {
        if (Thread.currentThread().isInterrupted()
                || (budget.cancelled != null && budget.cancelled.get())) {
            throw new WordParseException(
                    WordParseException.Reason.CANCELLED,
                    "DOCX parsing was cancelled"
            );
        }
        int event = parser.nextToken();
        budget.events++;
        if (budget.events > DocumentLimits.MAX_DOCX_XML_EVENTS) {
            throw tooLarge("XML event limit exceeded");
        }
        if (parser.getDepth() > DocumentLimits.MAX_XML_DEPTH) {
            throw tooLarge("XML nesting limit exceeded");
        }
        if (event == XmlPullParser.DOCDECL) {
            throw unsupported("Document type declarations are not allowed");
        }
        if (event == XmlPullParser.START_TAG) {
            budget.openElements++;
            if (parser.getAttributeCount() > 128) {
                throw tooLarge("XML attribute limit exceeded");
            }
        } else if (event == XmlPullParser.END_TAG) {
            budget.openElements--;
            if (budget.openElements < 0) {
                throw corrupted("XML element structure is invalid");
            }
        } else if (event == XmlPullParser.END_DOCUMENT && budget.openElements != 0) {
            throw corrupted("XML document is incomplete");
        }
        if (event == XmlPullParser.ENTITY_REF && !isPredefinedEntity(parser.getName())) {
            throw unsupported("Custom XML entities are not allowed");
        }
        return event;
    }

    static String attribute(XmlPullParser parser, String localName) {
        for (int index = 0; index < parser.getAttributeCount(); index++) {
            String namespace = parser.getAttributeNamespace(index);
            if (localName.equals(parser.getAttributeName(index))
                    && (namespace == null
                    || namespace.isEmpty()
                    || isWordNamespace(namespace)
                    || OFFICE_RELATIONSHIPS_TRANSITIONAL.equals(namespace)
                    || OFFICE_RELATIONSHIPS_STRICT.equals(namespace))) {
                return parser.getAttributeValue(index);
            }
        }
        return null;
    }

    static boolean isWordElement(XmlPullParser parser, String localName) {
        return localName.equals(parser.getName()) && isWordNamespace(parser.getNamespace());
    }

    static String wordElementName(XmlPullParser parser) {
        return isWordNamespace(parser.getNamespace()) ? parser.getName() : null;
    }

    static boolean isMarkupCompatibilityElement(
            XmlPullParser parser,
            String localName
    ) {
        return localName.equals(parser.getName()) && MC.equals(parser.getNamespace());
    }

    static boolean isPackageRelationshipElement(XmlPullParser parser) {
        String namespace = parser.getNamespace();
        return "Relationship".equals(parser.getName())
                && (PACKAGE_RELATIONSHIPS_TRANSITIONAL.equals(namespace)
                || PACKAGE_RELATIONSHIPS_STRICT.equals(namespace));
    }

    static boolean isContentTypeElement(XmlPullParser parser, String localName) {
        String namespace = parser.getNamespace();
        return localName.equals(parser.getName())
                && (CONTENT_TYPES_TRANSITIONAL.equals(namespace)
                || CONTENT_TYPES_STRICT.equals(namespace));
    }

    static boolean isImageMetadataElement(XmlPullParser parser, String localName) {
        if (!localName.equals(parser.getName())) return false;
        String namespace = parser.getNamespace();
        return DRAWING_MAIN_TRANSITIONAL.equals(namespace)
                || DRAWING_MAIN_STRICT.equals(namespace)
                || DRAWING_WORD_TRANSITIONAL.equals(namespace)
                || DRAWING_WORD_STRICT.equals(namespace)
                || DRAWING_PICTURE_TRANSITIONAL.equals(namespace)
                || DRAWING_PICTURE_STRICT.equals(namespace)
                || VML.equals(namespace);
    }

    static boolean supportsAlternateChoice(XmlPullParser parser) {
        String requires = attribute(parser, "Requires");
        if (requires == null || requires.trim().isEmpty()) return false;
        for (String prefix : requires.trim().split("\\s+")) {
            String namespace = parser.getNamespace(prefix);
            if (!isSupportedAlternateNamespace(namespace)) return false;
        }
        return true;
    }

    private static boolean isWordNamespace(String namespace) {
        return WORD_TRANSITIONAL.equals(namespace) || WORD_STRICT.equals(namespace);
    }

    private static boolean isSupportedAlternateNamespace(String namespace) {
        return isWordNamespace(namespace)
                || DRAWING_MAIN_TRANSITIONAL.equals(namespace)
                || DRAWING_MAIN_STRICT.equals(namespace)
                || DRAWING_WORD_TRANSITIONAL.equals(namespace)
                || DRAWING_WORD_STRICT.equals(namespace)
                || DRAWING_PICTURE_TRANSITIONAL.equals(namespace)
                || DRAWING_PICTURE_STRICT.equals(namespace)
                || VML.equals(namespace);
    }

    static void skipElement(XmlPullParser parser, Budget budget)
            throws IOException, XmlPullParserException, WordParseException {
        int startDepth = parser.getDepth();
        String name = parser.getName();
        int event;
        while ((event = next(parser, budget)) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == startDepth
                    && name.equals(parser.getName())) {
                return;
            }
        }
        throw corrupted("XML element is incomplete");
    }

    private static boolean isPredefinedEntity(String name) {
        return "amp".equals(name)
                || "lt".equals(name)
                || "gt".equals(name)
                || "apos".equals(name)
                || "quot".equals(name);
    }

    private static WordParseException tooLarge(String message) {
        return new WordParseException(WordParseException.Reason.TOO_LARGE, message);
    }

    private static WordParseException unsupported(String message) {
        return new WordParseException(WordParseException.Reason.UNSUPPORTED, message);
    }

    private static WordParseException corrupted(String message) {
        return new WordParseException(WordParseException.Reason.CORRUPTED, message);
    }

    static final class Budget {
        private final AtomicBoolean cancelled;
        private int events;
        private int openElements;

        Budget(AtomicBoolean cancelled) {
            this.cancelled = cancelled;
        }
    }
}
