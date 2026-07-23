# Локальный просмотр документов

## Область первой версии

`DocumentViewerActivity` — отдельная read-only Activity для локального открытия PDF, XLSX,
текста, CSV/TSV и системно декодируемых JPEG/PNG/WebP/HEIC/HEIF. Она получает внешние
`ACTION_VIEW` intents и internal explicit intent после выбора файла через
`ACTION_OPEN_DOCUMENT`. Viewer не импортирует документ в editor и не меняет PDF-сессию в
`MainActivity`.

Manifest регистрирует только реально отображаемые MIME types. Для XLSX используется отдельный
узкий filter `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`. В нём
отсутствуют `*/*`, `application/octet-stream`, `application/vnd.ms-excel`, XLSM/XLSB, archive
и другие document formats. `CATEGORY_BROWSABLE` не используется.

## URI и временная копия

- исходный `content://` читается только через `ContentResolver`;
- `file://` допускается как ограниченный fallback, без преобразования `content://` в путь;
- display name очищается от separators, traversal, control characters и ограничивается по длине;
- bytes копируются вне main thread в `cacheDir/document_viewer` под случайным именем;
- общий предел входного файла — 250 MiB, PDF — 200 MiB, image — 100 MiB, text/table — 25 MiB,
  XLSX — 50 MiB;
- нерасшаренная копия удаляется после закрытия Activity; копия, выданная через FileProvider
  после явного Share, сохраняется не дольше 24 часов и удаляется последующей cleanup;
- старые viewer cache files очищаются при каждом старте viewer.

Для Share используется узкий FileProvider URI временной копии и read grant. Исходный файл
не переименовывается, не удаляется и не записывается в public storage.

## Определение типа

`DocumentTypeResolver` сопоставляет provider MIME, затем проверяет bounded signature и
использует безопасное расширение display name только как fallback. Сильная фактическая
signature имеет приоритет при конфликте. Проверяются `%PDF`, OLE Compound File, JPEG, PNG,
WebP и HEIF brands. XLSX распознаётся только по ZIP signature и согласованному OOXML package:
`[Content_Types].xml`, `_rels/.rels`, `xl/workbook.xml`, workbook relationships и реальный
worksheet part. MIME или расширение не превращают обычный ZIP в XLSX.

## Renderers

- PDF использует framework `PdfRenderer`: один page-by-page bitmap, две страницы в LRU,
  bounded render до 8 миллионов pixels, Previous/Next, horizontal swipe, page counter,
  pinch/double-tap zoom. Page, renderer и descriptor закрываются; background queue отменяется
  при закрытии.
- TXT читается line-by-line до 2 миллионов characters / 10 000 lines, поддерживает UTF-8 BOM,
  UTF-8 и безопасный Windows-1251 fallback. Строки selectable и виртуализированы RecyclerView.
- CSV/TSV parser поддерживает comma/semicolon/tab, quoted values, escaped quotes, multiline
  cells, BOM, empty и uneven rows. Preview ограничен 5 000 rows, 100 columns и 4 096 chars на
  cell. CSV и TSV используют тот же Canvas viewport, что и XLSX.
- XLSX parser использует только `java.util.zip`, `XmlPullParser` и app-cache файл. Он читает
  порядок и имена листов через workbook relationships, shared/inline/rich strings, integer,
  decimal, boolean, blank, error, cached formula result, dates по style и `date1904`, а также
  merged ranges. Формулы не вычисляются; при отсутствии cache показывается их текст.
  Dropdown листов и имя текущего листа остаются вне document canvas.
- XLSX visual fidelity includes direct/indexed/theme cell fills, basic font attributes, borders,
  alignment, wrapText, merged ranges, hidden rows/columns, and worksheet column/row dimensions.
  `SpreadsheetCanvasView` открывает новый лист в 100% у A1, поддерживает диагональный pan/fling,
  focal-point pinch 60–300%, double tap и команда overflow возвращают масштаб к 100%.
  Геометрия листа хранится независимо от экрана; prefix sums и binary search выбирают
  только видимую область с overscan. Sticky row/column headers и corner рисуются тем же Canvas.
  При масштабе ниже 60% renderer упрощает текст, а ниже 40% показывает структуру листа без
  cell text и без создания wrapped layouts. Ограниченный LRU хранит только видимые wrapped
  layouts; во время pinch они не создаются. Accessibility использует виртуальные nodes только
  для видимых непустых ячеек и заголовков, причём merged cell публикуется один раз.
- images декодируются с bounds/inSampleSize, EXIF orientation, максимальной стороной 4096 px,
  fit-center, pinch/double-tap zoom и pan.

## XLSX security limits

- не более 512 ZIP entries, 100 MiB суммарного uncompressed content, 20 MiB на entry и ratio
  100:1;
- shared strings — не более 16 MiB, 100 000 записей и 8 миллионов characters;
- не более 64 sheets, 5 000 rows и 100 columns на sheet, 4 096 characters на cell,
  10 000 merged ranges и 1 миллион разобранных cells на workbook;
- XML ограничен 64 уровнями и 2 миллионами events на part; DTD и custom entities запрещены;
- запрещены absolute/traversal/duplicate ZIP paths, encrypted ZIP, ZIP64, macros, ActiveX,
  external links, OLE embeddings и executable entries;
- relationship target нормализуется внутри package; external worksheet/style/string parts не
  читаются. Hyperlinks не открываются автоматически и код документа не выполняется.

При превышении row/column/cell-text limits показывается partial preview с предупреждением;
опасный archive-level или XML complexity limit переводит viewer в too-large state. Поддержка
custom locale number/date formats ограничена безопасным распознаванием date/time tokens.

## Отдельный будущий этап: старый XLS

Официальные `org.apache.poi:poi:5.5.1` и `poi-ooxml:5.5.1` были проверены изолированно.
Dependency resolution прошёл, но D8 при `minSdk 24` завершился ошибкой: POI `CleanerUtil`
использует `MethodHandle.invoke/invokeExact`, поддержанные Android runtime только начиная с
API 26. Повышать minSdk ради POI нельзя. Старая версия или неофициальный Android fork несут
неисправленные parser/security CVE, несовместимости форматов и неподдерживаемую transitive
dependency graph, поэтому такой обход не используется.

Собственный BIFF8 parser существенно сложнее OOXML: нужен безопасный OLE Compound File reader,
BIFF record state machine, SST/CONTINUE, codepages, styles/dates, cached formulas, merged cells,
encryption/macro/external-link filtering и fuzz corpus. Оценка первой read-only версии —
примерно 4–8 инженерных недель плюс security hardening и дальнейшее сопровождение. Это
отдельный будущий этап; `application/vnd.ms-excel` до него не регистрируется.

## Ручная проверка

На физическом API 24 и современном Android проверить внешнее Open with, internal SAF,
grant loss, rotation, повторный `onNewIntent`, encrypted/corrupted/empty PDF, очень большую
страницу, XLSX с несколькими листами и rotation, повреждённый/зашифрованный/слишком большой
XLSX, quoted multiline CSV, UTF-8 BOM, Windows-1251 TXT, EXIF JPEG и HEIC/HEIF только на
устройствах с системным decoder. Отдельно пройти каталог и viewer на 320–412 dp, tablet,
landscape, font scale 1.0–1.5, light/dark и TalkBack.
