package com.desperadoboi.imagetopdf.ui.viewer;

final class DocxCssBuilder {
    private DocxCssBuilder() {
    }

    static String build() {
        return "html,body,main,section,div,p,h1,h2,h3,h4,h5,h6,ol,ul,li,"
                + "table,thead,tbody,tfoot,tr,th,td,figure,figcaption,span,a{"
                + "margin:0;padding:0;background:transparent;box-shadow:none;border:0;"
                + "box-sizing:border-box}"
                + "html{-webkit-text-size-adjust:100%;text-size-adjust:100%;"
                + "font-synthesis:none}"
                + "body{background:#e7eaee;color:#1d2633;overflow:auto;"
                + "font-family:sans-serif;font-size:11pt;line-height:1.15}"
                + ".docx-document{display:flex;flex-direction:column;align-items:center;"
                + "gap:12px;padding:12px;min-width:max-content}"
                + ".docx-page{position:relative;flex:none;background:#fff;"
                + "border:1px solid #cbd1d8;box-shadow:0 1px 4px rgba(19,31,45,.18);"
                + "box-sizing:border-box;overflow:hidden}"
                + ".docx-page-content{width:100%;height:100%;overflow:hidden}"
                + ".word-paragraph,.list-line{background:transparent;border:0;"
                + "box-shadow:none;max-width:100%;white-space:pre-wrap;overflow-wrap:break-word;"
                + "word-break:normal;tab-size:4;min-height:1em}"
                + ".word-heading{break-after:avoid-page}"
                + ".word-keep{break-inside:avoid-page}"
                + ".word-story-header,.word-story-footer{color:#4b5563}"
                + ".word-story-footnote,.word-story-endnote{font-size:.9em}"
                + ".word-list{list-style:none;max-width:100%}"
                + ".word-list>li{list-style:none;max-width:100%}"
                + ".list-line{display:grid;grid-template-columns:max-content minmax(0,1fr);"
                + "column-gap:.35em}"
                + ".list-marker{white-space:pre}"
                + ".list-content{min-width:0;white-space:pre-wrap;overflow-wrap:break-word}"
                + ".table-overflow{max-width:100%;overflow-x:auto;overflow-y:hidden;"
                + "-webkit-overflow-scrolling:touch}"
                + ".word-table{border-collapse:collapse;border-spacing:0;"
                + "table-layout:fixed;max-width:none;background:transparent}"
                + ".word-table td,.word-table th{box-sizing:border-box;"
                + "white-space:normal;overflow-wrap:break-word}"
                + ".word-table .word-paragraph{max-width:none}"
                + ".word-image{display:block;max-width:100%}"
                + ".word-image img{display:block;max-width:100%;height:auto;"
                + "object-fit:contain}"
                + ".image-placeholder{display:flex;align-items:center;justify-content:center;"
                + "min-height:36pt;max-width:100%;border:1px solid #aeb6c0;"
                + "color:#596574;background:#f4f5f7;font:9pt sans-serif;text-align:center;"
                + "padding:6pt}"
                + "a{color:#1565c0;text-decoration:underline}"
                + "@media(prefers-color-scheme:dark){body{background:#e7eaee;color:#1d2633}"
                + ".docx-page{background:#fff}}";
    }
}
