package com.desperadoboi.imagetopdf.document.spreadsheet;

import com.desperadoboi.imagetopdf.document.DocumentLimits;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;

final class XlsxXml {
    private static final String PROCESS_DOCDECL =
            "http://xmlpull.org/v1/doc/features.html#process-docdecl";

    private XlsxXml() {
    }

    static XmlPullParser newParser(InputStream inputStream)
            throws XmlPullParserException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        } catch (XmlPullParserException ignored) {
            // Namespace-aware factory configuration above remains authoritative.
        }
        try {
            parser.setFeature(PROCESS_DOCDECL, false);
        } catch (XmlPullParserException ignored) {
            // DOCDECL is also rejected while tokens are consumed.
        }
        parser.setInput(inputStream, null);
        return parser;
    }

    static int next(XmlPullParser parser, Budget budget)
            throws IOException, XmlPullParserException, XlsxParseException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("XLSX parsing was interrupted");
        }
        int event = parser.nextToken();
        budget.events++;
        if (budget.events > DocumentLimits.MAX_XML_EVENTS) {
            throw new XlsxParseException(
                    XlsxParseException.Reason.TOO_LARGE,
                    "XML event limit exceeded"
            );
        }
        if (parser.getDepth() > DocumentLimits.MAX_XML_DEPTH) {
            throw new XlsxParseException(
                    XlsxParseException.Reason.TOO_LARGE,
                    "XML nesting limit exceeded"
            );
        }
        if (event == XmlPullParser.DOCDECL) {
            throw new XlsxParseException(
                    XlsxParseException.Reason.UNSUPPORTED,
                    "Document type declarations are not allowed"
            );
        }
        if (event == XmlPullParser.START_TAG) {
            budget.openElements++;
        } else if (event == XmlPullParser.END_TAG) {
            budget.openElements--;
            if (budget.openElements < 0) {
                throw new XlsxParseException(
                        XlsxParseException.Reason.CORRUPTED,
                        "XML element structure is invalid"
                );
            }
        } else if (event == XmlPullParser.END_DOCUMENT && budget.openElements != 0) {
            throw new XlsxParseException(
                    XlsxParseException.Reason.CORRUPTED,
                    "XML document is incomplete"
            );
        }
        if (event == XmlPullParser.ENTITY_REF && !isPredefinedEntity(parser.getName())) {
            throw new XlsxParseException(
                    XlsxParseException.Reason.UNSUPPORTED,
                    "Custom XML entities are not allowed"
            );
        }
        if (event == XmlPullParser.START_TAG && parser.getAttributeCount() > 128) {
            throw new XlsxParseException(
                    XlsxParseException.Reason.TOO_LARGE,
                    "XML attribute limit exceeded"
            );
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

    private static boolean isPredefinedEntity(String name) {
        return "amp".equals(name)
                || "lt".equals(name)
                || "gt".equals(name)
                || "apos".equals(name)
                || "quot".equals(name);
    }

    static final class Budget {
        private int events;
        private int openElements;
    }
}
