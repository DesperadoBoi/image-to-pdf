package com.desperadoboi.imagetopdf.document.word;

public final class WordImage extends WordBlock {
    private final String relationshipId;
    private final String packagePath;
    private final long widthEmu;
    private final long heightEmu;
    private final String altText;
    private final boolean vectorPlaceholder;

    public WordImage(
            String relationshipId,
            String packagePath,
            long widthEmu,
            long heightEmu,
            String altText,
            boolean vectorPlaceholder
    ) {
        super(Type.IMAGE);
        this.relationshipId = relationshipId == null ? "" : relationshipId;
        this.packagePath = packagePath == null ? "" : packagePath;
        this.widthEmu = Math.max(0L, widthEmu);
        this.heightEmu = Math.max(0L, heightEmu);
        this.altText = altText == null ? "" : altText;
        this.vectorPlaceholder = vectorPlaceholder;
    }

    public String getRelationshipId() { return relationshipId; }
    public String getPackagePath() { return packagePath; }
    public long getWidthEmu() { return widthEmu; }
    public long getHeightEmu() { return heightEmu; }
    public String getAltText() { return altText; }
    public boolean isVectorPlaceholder() { return vectorPlaceholder; }
}
