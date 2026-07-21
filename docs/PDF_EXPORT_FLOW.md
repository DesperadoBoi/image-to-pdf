# PDF export flow

## Экран параметров

`EditorFragment` открывает `PdfExportSheet` до системного выбора файла. Sheet использует уже подключённый Material `BottomSheetDialogFragment`, XML Views и activity-scoped `DocumentSessionViewModel`; новая dependency не добавлена.

`PdfExportDraft` хранит незавершённый пользовательский ввод и переживает recreation. `PdfExportRequest` создаётся только после валидации и является immutable. Имя очищается от недопустимых для документа символов, пустое имя отклоняется, а одна `.pdf` добавляется автоматически без двойного расширения.

## Реальные параметры

- качество: `COMPACT` 96 DPI, `BALANCED` 144 DPI и `HIGH` 216 DPI;
- размер страницы: A4 или по изображению;
- ориентация: автоматически по странице, книжная или альбомная;
- поля: без полей, маленькие или стандартные;
- placement остаётся реальным существующим `FIT`, потому что отдельного выбора placement в текущем sheet нет.

`PdfQualityProfile` является единственной точкой значений DPI. `RasterTargetCalculator` принимает DPI профиля, сохраняет исходный aspect ratio и никогда не увеличивает маленькое изображение. `PdfPageLayoutCalculator` применяет orientation к A4 и image-sized страницам. `PdfGenerator` по-прежнему обрабатывает одну страницу за раз и не изменяет исходные файлы.

## Output Uri

«Обзор» запускает `CreateDocument(application/pdf)` с нормализованным именем, записывает возвращённый `Uri` и best-effort display name в draft, но не начинает генерацию. «Конвертировать» использует уже выбранный `Uri`; если его нет, тот же `CreateDocument` автоматически продолжает генерацию после успешного выбора.

Cancel системного диалога оставляет sheet и draft без status/toast. После начала генерации sheet закрывается, Editor показывает существующие progress и cancel. `operationId`, stale-callback filtering, `CancellationToken` и удаление partial output сохранены. После cancel/error параметры остаются для повтора, а удалённый partial `Uri` очищается из draft.

В sheet нет пароля, шифрования, подписи и других неработающих параметров.
