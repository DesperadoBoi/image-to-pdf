# PDF result screen

## Навигация и состояние

Успешное завершение активной операции записывает immutable `PdfResult` в
activity-scoped `DocumentSessionViewModel` и создаёт отдельный `PdfSuccessEvent` по
`operationId`. `EditorFragment` потребляет event только один раз и показывает верхний
`PdfSuccessBanner`; автоматического перехода на result screen больше нет. Consumed event
остаётся consumed при recreation, повторный callback того же operation не публикует event,
а новая операция получает новый event.

Banner появляется через translation/alpha, показывает «PDF готов», число страниц, размер и
действие «Открыть», затем скрывается примерно через три секунды. В Editor также остаётся
компактная карточка последнего PDF с именем, summary, Share и открытием result screen.
`PdfResultFragment` доступен только по явному действию пользователя. Новая Activity,
Navigation Component и отдельное хранилище результата не используются.

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
`SIZE` вне UI thread. Для размера после query используется `ParcelFileDescriptor.statSize`,
а точный счётчик байтов собственной записи остаётся fallback. `PdfResult.UNKNOWN_SIZE = -1`
отличает неизвестный размер от реального zero-byte файла; UI показывает «Размер неизвестен»
вместо `0 B` для unknown.

`PdfLocationLabelResolver` переводит Downloads/external Downloads/Documents в «Загрузки»
или «Документы», использует безопасное название стороннего provider (например Google Drive),
если оно доступно, и иначе возвращает «Выбранная папка». Raw authority, document ID,
абсолютный путь и content Uri пользователю не показываются. `PdfResult` не хранит `Bitmap`,
`ContentResolver`, Fragment или Activity.

## Действия

- «Поделиться» использует `ACTION_SEND`, `application/pdf`, read grant и системный chooser;
- «Открыть» использует `ACTION_VIEW`, `application/pdf` и read grant, отсутствие viewer
  обрабатывается сообщением;
- «Поделиться» — основная полноширинная кнопка;
- «Открыть» — вторичная outlined-кнопка;
- «Вернуться к страницам» — компактное text action/обычное Back;
- «Создать новый документ» — компактное text action, которое очищает session и возвращает Home, сохраняя созданный PDF.

Result screen показывает bounded preview, page badge, имя максимум в две строки, объединённый
summary «N страниц · SIZE» и human-readable location. `.pdf` защищено от отдельного переноса,
а полное имя остаётся в `contentDescription`.

Экран не показывает rename без реального переименования, WhatsApp shortcut, Draw, Add
Text, Signature, rating prompt и другие фиктивные функции.
