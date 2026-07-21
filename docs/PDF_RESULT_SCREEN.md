# PDF result screen

## Навигация и состояние

Успешное завершение активной операции записывает immutable `PdfResult` в
activity-scoped `DocumentSessionViewModel` и создаёт отдельный `PdfSuccessEvent` по
`operationId`. Operation-aware coordinator принимает только success активной операции и один
раз запускает прямой `EditorFragment → PdfResultFragment` переход. Cancel и error оставляют
пользователя в Editor, stale callback игнорируется, duplicate success не создаёт вторую
навигацию.

`EditorFragment` не consume-ит event и не показывает карточку последнего PDF. Result screen
сверяет pending event с текущим `PdfResult`, consume-ит его до анимации и показывает верхний
`PdfSuccessBanner` один раз. Banner появляется снизу status bar через
`translationY=-height/alpha=0 → 0/1` за 300 ms, остаётся около 2500 ms и скрывается за 240 ms,
после чего становится `GONE`. Recreation и повторный observer attach не повторяют banner;
новая операция получает новый event. Новая Activity, Navigation Component и отдельное
хранилище результата не используются.

Result добавляется поверх Editor в fragment back stack с коротким fade. Back, компактная
кнопка назад и «Вернуться к страницам» делают `popBackStack`, не удаляя созданный PDF.
Редактирование,
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

Result screen не содержит тяжёлую toolbar: сверху остаётся только компактная кнопка назад и
overlay-banner. Экран показывает крупный preview первой страницы, page badge, имя максимум в две строки, объединённый
summary «N страниц · SIZE» и human-readable location. `.pdf` защищено от отдельного переноса,
а полное имя остаётся в `contentDescription`.

Экран не показывает rename без реального переименования, WhatsApp shortcut, Draw, Add
Text, Signature, rating prompt и другие фиктивные функции.
