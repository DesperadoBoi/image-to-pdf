# Локальный просмотр документов

## Область первой версии

`DocumentViewerActivity` — отдельная read-only Activity для локального открытия PDF, текста,
CSV/TSV и системно декодируемых JPEG/PNG/WebP/HEIC/HEIF. Она получает внешние
`ACTION_VIEW` intents и internal explicit intent после выбора файла через
`ACTION_OPEN_DOCUMENT`. Viewer не импортирует документ в editor и не меняет PDF-сессию в
`MainActivity`.

Manifest регистрирует только реально отображаемые MIME types. В нём отсутствуют `*/*`,
`application/octet-stream`, Office, archive и document formats, которые эта версия не
открывает. `CATEGORY_BROWSABLE` не используется.

## URI и временная копия

- исходный `content://` читается только через `ContentResolver`;
- `file://` допускается как ограниченный fallback, без преобразования `content://` в путь;
- display name очищается от separators, traversal, control characters и ограничивается по длине;
- bytes копируются вне main thread в `cacheDir/document_viewer` под случайным именем;
- общий предел входного файла — 250 MiB, PDF — 200 MiB, image — 100 MiB, text/table — 25 MiB;
- нерасшаренная копия удаляется после закрытия Activity; копия, выданная через FileProvider
  после явного Share, сохраняется не дольше 24 часов и удаляется последующей cleanup;
- старые viewer cache files очищаются при каждом старте viewer.

Для Share используется узкий FileProvider URI временной копии и read grant. Исходный файл
не переименовывается, не удаляется и не записывается в public storage.

## Определение типа

`DocumentTypeResolver` сопоставляет provider MIME, затем проверяет bounded signature и
использует безопасное расширение display name только как fallback. Сильная фактическая
signature имеет приоритет при конфликте. Проверяются `%PDF`, OLE Compound File, OOXML ZIP с
`[Content_Types].xml` и `xl/workbook.xml`, JPEG, PNG, WebP и HEIF brands. ZIP metadata имеет
лимиты числа entries, суммарного uncompressed size и compression ratio.

## Renderers

- PDF использует framework `PdfRenderer`: один page-by-page bitmap, две страницы в LRU,
  bounded render до 8 миллионов pixels, Previous/Next, horizontal swipe, page counter,
  pinch/double-tap zoom. Page, renderer и descriptor закрываются; background queue отменяется
  при закрытии.
- TXT читается line-by-line до 2 миллионов characters / 10 000 lines, поддерживает UTF-8 BOM,
  UTF-8 и безопасный Windows-1251 fallback. Строки selectable и виртуализированы RecyclerView.
- CSV/TSV parser поддерживает comma/semicolon/tab, quoted values, escaped quotes, multiline
  cells, BOM, empty и uneven rows. Preview ограничен 5 000 rows, 100 columns и 4 096 chars на
  cell; вертикально виртуализирован.
- images декодируются с bounds/inSampleSize, EXIF orientation, максимальной стороной 4096 px,
  fit-center, pinch/double-tap zoom и pan.

## Excel compatibility spike

Официальные `org.apache.poi:poi:5.5.1` и `poi-ooxml:5.5.1` были проверены изолированно.
Dependency resolution прошёл, но D8 при `minSdk 24` завершился ошибкой: POI `CleanerUtil`
использует `MethodHandle.invoke/invokeExact`, поддержанные Android runtime только начиная с
API 26. Поэтому зависимости удалены, XLS/XLSX MIME types не зарегистрированы и Excel viewer
не заявлен готовым.

Следующий этап должен сохранить `SpreadsheetParser` boundary и отдельно проверить небольшой
Android-compatible adapter на API 24, D8, R8, license, ZIP-bomb limits и fixture runtime.
Понижать minSdk или подключать заброшенный неофициальный Office stack ради обхода нельзя.

## Ручная проверка

На физическом API 24 и современном Android проверить внешнее Open with, internal SAF,
grant loss, rotation, повторный `onNewIntent`, encrypted/corrupted/empty PDF, очень большую
страницу, quoted multiline CSV, UTF-8 BOM, Windows-1251 TXT, EXIF JPEG и HEIC/HEIF только на
устройствах с системным decoder. Отдельно пройти 320–412 dp, tablet, landscape, font scale
1.0–1.5, light/dark и TalkBack.
