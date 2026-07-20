package com.desperadoboi.imagetopdf.model;

public final class PerspectiveQuadValidator {
    public static final float MIN_AREA = 0.0001f;

    private static final float MIN_EDGE_LENGTH_SQUARED = 0.00000001f;

    private PerspectiveQuadValidator() {
    }

    public static boolean isValid(
            NormalizedPoint topLeft,
            NormalizedPoint topRight,
            NormalizedPoint bottomRight,
            NormalizedPoint bottomLeft
    ) {
        NormalizedPoint[] points = {topLeft, topRight, bottomRight, bottomLeft};
        for (int index = 0; index < points.length; index++) {
            NormalizedPoint current = points[index];
            NormalizedPoint next = points[(index + 1) % points.length];
            if (distanceSquared(current, next) < MIN_EDGE_LENGTH_SQUARED) {
                return false;
            }
        }

        float firstCross = cross(points[0], points[1], points[2]);
        if (firstCross <= 0f) {
            return false;
        }
        for (int index = 1; index < points.length; index++) {
            float nextCross = cross(
                    points[index],
                    points[(index + 1) % points.length],
                    points[(index + 2) % points.length]
            );
            if (nextCross <= 0f) {
                return false;
            }
        }
        return signedArea(points) >= MIN_AREA;
    }

    public static float signedArea(PerspectiveQuad quad) {
        return signedArea(new NormalizedPoint[]{
                quad.getTopLeft(),
                quad.getTopRight(),
                quad.getBottomRight(),
                quad.getBottomLeft()
        });
    }

    private static float signedArea(NormalizedPoint[] points) {
        float twiceArea = 0f;
        for (int index = 0; index < points.length; index++) {
            NormalizedPoint current = points[index];
            NormalizedPoint next = points[(index + 1) % points.length];
            twiceArea += (current.getX() * next.getY()) - (current.getY() * next.getX());
        }
        return twiceArea / 2f;
    }

    private static float cross(
            NormalizedPoint first,
            NormalizedPoint second,
            NormalizedPoint third
    ) {
        return ((second.getX() - first.getX()) * (third.getY() - second.getY()))
                - ((second.getY() - first.getY()) * (third.getX() - second.getX()));
    }

    private static float distanceSquared(NormalizedPoint first, NormalizedPoint second) {
        float dx = second.getX() - first.getX();
        float dy = second.getY() - first.getY();
        return (dx * dx) + (dy * dy);
    }
}
