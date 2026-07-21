# PDF result screen

## Навигация и состояние

Успешное завершение активной операции записывает immutable `PdfResult` в
activity-scoped `DocumentSessionViewModel` и создаёт одноразовое событие навигации по
`operationId`. `EditorFragment` потребляет событие только один раз и открывает
`PdfResultFragment` в существующем контейнере `MainActivity`. Новая Activity, Navigation
Component и отдельное хранилище результата не используются.

Back и кнопка закрытия возвращают к редактору, не удаляя созданный PDF. Редактирование,
reorder и APPEND сохраняют последний результат; он заменяется следующей успешной
генерацией или очищается при NEW_DOCUMENT. «Создать новый документ» удаляет только
app-owned camera captures текущей session и не удаляет сохранённый output `Uri`.

## Preview и metadata

`PdfPreviewLoader` открывает `ParcelFileDescriptor` через `ContentResolver`, использует
нативный `PdfRenderer` и рендерит только первую страницу на ограниченном single-thread
executor. Размер bitmap ограничен реальным preview. `PdfRenderer.Page`, `PdfRenderer` и
descriptor закрываются; устаревший или отменённый bitmap освобождается. Bitmap хранится
только во Fragment и освобождается вместе с view. Если provider нельзя отрендерить,
остаётся PDF placeholder, а Open и Share продолжают работать.

`PdfResultMetadataReader` выполняет best-effort query `OpenableColumns.DISPLAY_NAME` и
`SIZE` вне UI thread. `PdfResult` хранит только `Uri`, display name, неотрицательный size,
признак доступности size, page count, timestamp и provider/location label. Абсолютные пути,
`Bitmap`, `ContentResolver`, Fragment и Activity в модели не хранятся.

## Действия

- «Поделиться» использует `ACTION_SEND`, `application/pdf`, read grant и системный chooser;
- «Открыть» использует `ACTION_VIEW`, `application/pdf` и read grant, отсутствие viewer
  обрабатывается сообщением;
- «Редактировать страницы» возвращает текущую session в `EditorFragment`;
- «Создать новый документ» очищает session и возвращает Home, сохраняя созданный PDF.

Экран не показывает rename без реального переименования, WhatsApp shortcut, Draw, Add
Text, Signature, rating prompt и другие фиктивные функции.
