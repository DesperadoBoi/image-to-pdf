# ImageToPDF Privacy Policy

Effective date: **[POLICY EFFECTIVE DATE]**

Developer: **[DEVELOPER NAME]**

Contact email: **[CONTACT EMAIL]**

Public policy URL: **[PUBLIC POLICY URL]**

## Scope

This policy describes data handling in the ImageToPDF Android application. The current
version creates PDF documents from images and scans document pages locally.

## How data is handled

- Images and documents are processed locally on the device.
- The app does not send images or generated PDF files to the developer's server.
- The app does not require registration, create user accounts, display advertising, or use
  analytics.
- The app does not sell data or share it with advertisers.
- The app manifest does not include the `INTERNET` permission.

## Camera and image access

`CAMERA` is used only to capture pages in the document scanning flow. The app accesses
images only after the user selects them through a system interface or grants the relevant
permission. When MediaStore access is granted, the app reads image information and content
locally to display the in-app gallery and process selected pages. If access is denied,
available system file selection methods can still be used; the app does not obtain broader
access without permission.

## File storage and deletion

Generated PDF files are saved to a location selected by the user through the Android system
dialog. After saving, the user controls the file through the selected storage provider or a
file manager.

Temporary captures created by the app are kept in app-owned storage for the working session.
They are deleted during supported cancellation, retake, page removal, or session cleanup
flows. The app does not delete source images selected from the gallery or a third-party
document provider.

Users can remove local app data through Android settings or by uninstalling the app. PDF
files saved to a user-selected external location must be deleted by the user.

## Opening and sharing PDF files

PDF files are opened or shared only after a user action through Android system mechanisms.
When sharing, the user selects the recipient application. Any subsequent processing is
governed by that recipient's privacy practices.

## Policy changes and contact

This policy should be reviewed whenever permissions, dependencies, or data handling change.
Privacy questions can be sent to the contact email listed above.
