# Page editor specification

## User modes

`PageEditFragment` has two user-visible modes:

- `NORMAL` shows the final page, supports zoom and pan, page switching, rotation, reset and done;
- `RECT_CROP` shows the rectified page before crop and edits a temporary rectangular crop.

Only applied values are stored in `DocumentSessionViewModel`. Temporary handle positions remain in the overlay views. Cancelling a tool does not change `PageItem`.

## Transform order

All final outputs use this order:

1. sampled source decode;
2. EXIF correction;
3. manual page rotation;
4. perspective correction;
5. rectangular crop;
6. final scaling for thumbnail, preview or PDF.

`PageBitmapProcessor` owns steps 2 through 5. `ThumbnailLoader`, `PreviewImageLoader` and `PdfGenerator` use the same processor. No edited JPEG copy is written.

## Coordinate systems

`PerspectiveQuad` uses normalized coordinates relative to the image after EXIF correction and manual rotation, before perspective correction.

`CropRect` uses normalized coordinates relative to the rectangular result after perspective correction. When the perspective quad changes, the old crop is reset to `CropRect.FULL` because it described different output geometry.

Both models use values in `0..1`. `FULL` is explicit; absence of an edit is never represented by `null`.

## Handles

Rectangular crop has four corner handles and four side midpoint handles. Corners move two bounds, side midpoints move one bound, and dragging inside the crop moves the whole rectangle without changing its size.

`DocumentPerspectiveOverlayView` remains available for the future Smart Scan flow. It has four independent corner handles and four edge midpoint handles. An edge midpoint applies the same two-dimensional delta to the two adjacent corners. The validator rejects non-clockwise, concave, self-intersecting, zero-edge and too-small quadrilaterals. This overlay is not attached to the ordinary `PageEditFragment` layout.

## Reset behavior

- normal reset restores rotation to zero and both edit geometries to `FULL`;
- crop reset changes only the temporary crop to `FULL`;
- applying a changed perspective always resets the stored crop.

Perspective reset/apply behavior remains part of the model and future Smart Scan contract,
not the ordinary Image-to-PDF editor UI.

## Output integration

Thumbnail keys contain the stable page ID, manual rotation, perspective quad and crop rectangle. A page edit invalidates only that page's row and strip item.

Preview modes request only the geometry they display. `SourceResolutionCalculator` compensates for a small quad or crop when choosing `inSampleSize`, while retaining the source-resolution ceiling.

PDF generation snapshots immutable `PageItem` instances, processes one page at a time, preserves progress and cancellation checks, and releases each working bitmap before moving to the next page.

## Future Smart Scan flow

The ordinary Image-to-PDF editor does not show a Document action or perspective quad.
Perspective correction is reserved for a separate future flow:

`Home Smart Scan tile -> Camera -> Document perspective editor -> Result editor -> PDF`.

`SmartScanFlowCoordinator` declares the future capture/editor entry points without exposing
an unfinished screen.
