package com.desperadoboi.imagetopdf.document.word;

import com.desperadoboi.imagetopdf.document.DocumentLimits;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

final class WordXml {
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
            if (localName.equals(parser.getAttributeName(index))) {
                return parser.getAttributeValue(index);
            }
        }
        return null;
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
