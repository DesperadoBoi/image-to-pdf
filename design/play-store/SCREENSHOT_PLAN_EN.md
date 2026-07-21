# Google Play screenshot plan — EN

## Shared production rules

- Deliver 7 portrait phone assets on `1080×1920`, `9:16` canvases as JPEG or 24-bit PNG
  files with no transparency.
- Every composition must use a real screenshot from the current release build on the same
  device. Do not redraw controls, replace in-app labels, or assemble states the app cannot
  produce.
- Keep the localized headline within the top 20% of the asset. The real UI must remain the
  dominant element and must not be covered by marketing copy.
- Use one headline style, one screenshot scale, and consistent margins throughout the set.
  Do not add a phone frame, store badges, or install calls to action.
- Capture on a clean test device. Use System UI demo mode or prepare a neutral status bar:
  no notifications or carrier name and full network and battery indicators. Hide debug
  overlays, touch markers, permission dialogs, toasts, keyboard, and system chooser screens.
- Use four self-created sample pages titled `Project notes 1–4`, containing neutral text and
  simple geometric diagrams. Never show real names, faces, signatures, addresses, email,
  phone numbers, identity numbers, QR/barcodes, account details, notifications, or third-party
  logos.
- The current app contains Russian in-app resources only. English marketing headlines are
  allowed, but the Russian UI in the real captures must not be translated in image editing.
  Reassess the mixed-language set before publication if an English app localization becomes
  available later.
- Before export, check legibility at thumbnail size, correct aspect ratio, current UI state,
  and removal of EXIF/location metadata from the finished assets.

## 1. Home screen

- **Screen:** `HomeFragment` after a clean launch, with no sheet open.
- **Test data:** none; start with a new empty session.
- **Show:** ImageToPDF branding, subtitle, and the upper dashboard area containing the real
  Image to PDF and Smart Scan tiles. Do not make future tools the focus; preserve their real
  “Coming soon” state if they appear in the crop.
- **Headline:** “Photos to PDF in just a few steps”.
- **Free space:** top 18% for the headline and at least 6% background on both sides.
- **Personal data:** none appears on this screen.
- **System UI:** retain clean status and gesture-navigation bars; hide notifications, carrier
  name, and debug indicators.
- **Background:** `#E8EEFA` fading subtly to `#DCE7F8`.
- **Theme:** light.

## 2. Built-in gallery

- **Screen:** `ImagePickerFragment` in the `All images` or `Recent` grid after MediaStore
  access has been granted.
- **Test data:** 8–12 neutral images; select sample pages 1, 2, and 3 in an obvious order.
- **Show:** real thumbnails, numbered selection badges, album control, and the active
  `IMPORT (3)` action. Keep Camera and Files entries visible when the real layout allows it,
  but do not open a system picker.
- **Headline:** “Pick the photos you need”.
- **Free space:** top 18–20%; do not cover the image grid.
- **Personal data:** no personal camera roll, faces, geotagged content, real album names, or
  organization names.
- **System UI:** retain clean status/navigation bars; hide permission prompts and system
  notifications.
- **Background:** `#EDF3FC`.
- **Theme:** light.

## 3. Document scanning

- **Screen:** `SmartScanFragment` with CAMERA permission granted and the CameraX preview
  running.
- **Test data:** sample page 1 printed on a plain desk; add two sample pages beforehand so
  the counter demonstrates a multi-page session.
- **Show:** the real camera preview, guidance frame, shutter, page count, gallery, done, grid,
  and torch controls. Place the paper inside the frame without implying that the app detected
  its edges.
- **Headline:** “Scan documents with your camera”.
- **Free space:** top 16–18% on navy; do not cover the toolbar or camera preview.
- **Personal data:** only the sample sheet and a neutral surface; no hands, faces, mail,
  screens, addresses, or serial-numbered objects.
- **System UI:** retain clean status/navigation bars; hide permission dialogs, screen-recording
  indicators, notifications, and developer overlays.
- **Background:** `#081A3B`.
- **Theme:** dark, preserving the real camera surface.

## 4. Page editor

- **Screen:** `EditorFragment` after importing four sample pages.
- **Test data:** pages 1–4; move page 3 before page 2 and rotate one page before capture.
- **Show:** the Pages title, four thumbnails, page numbers, drag handles, rotate and delete
  actions, reorder guidance, Add, and Create PDF.
- **Headline:** “Reorder and edit every page”.
- **Free space:** top 18%; keep the bottom primary action fully visible.
- **Personal data:** synthetic pages only, with no names or handwritten signatures.
- **System UI:** retain clean status/navigation bars; do not capture an active drag,
  accessibility menu, or toast.
- **Background:** `#E8EEFA`.
- **Theme:** light.

## 5. Crop and perspective

- **Screen:** `ScanReviewFragment` displaying a sample page photographed at a slight angle.
- **Test data:** sample page 2 with clear outer boundaries and no confidential content.
- **Show:** the real image, all four handles of the manual perspective frame, and the actual
  Retake, Rotate, Auto, Original, and Add page actions. Position the handles manually; do not
  present them as detected edges. A separate variant may use `PageEditFragment` in manual
  crop mode, but do not combine two screens into one asset.
- **Headline:** “Adjust document corners by hand”.
- **Free space:** top 18%; keep all handles and the bottom tool area unobstructed.
- **Personal data:** synthetic content only; no signatures, numbers, or QR/barcodes.
- **System UI:** retain clean status/navigation bars; hide toasts and touch markers.
- **Background:** `#DCE7F8`.
- **Theme:** light.

## 6. Export settings

- **Screen:** `PdfExportSheet` over `EditorFragment`, with Additional settings expanded.
- **Test data:** file name `Project notes`, Balanced quality, A4, Auto orientation, and
  standard margins; do not select a destination yet.
- **Show:** file name, all three real quality choices, expanded page size, orientation, and
  margin values, plus the Convert action.
- **Headline:** “Choose quality and page setup”.
- **Free space:** top 17–18%; show the full bottom sheet and primary action, retaining the
  editor behind it as genuine context.
- **Personal data:** no real document name, path, or storage account.
- **System UI:** retain clean status/navigation bars; hide the keyboard and CreateDocument
  system screen.
- **Background:** `#152E63`.
- **Theme:** light app theme on a navy marketing background.

## 7. Result screen

- **Screen:** `PdfResultFragment` immediately after a real successful export.
- **Test data:** `Project notes.pdf`, four pages, saved to a local destination with a neutral
  `Downloads` label.
- **Show:** first-page preview, file name, page count, actual size, neutral location label,
  one-time success banner, and Share, Open, and Return to pages actions. Do not open the share
  chooser or a PDF viewer.
- **Headline:** “Open or share your finished PDF”.
- **Free space:** top 18%; keep the preview and both primary result actions visible.
- **Personal data:** use only the sample file and content; no cloud-provider email or account
  name.
- **System UI:** retain clean status/navigation bars; hide save notifications, chooser, and
  third-party apps.
- **Background:** `#E8EEFA`.
- **Theme:** light.
