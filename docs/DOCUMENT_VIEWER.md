# Локальный просмотр документов

## Область первой версии

`DocumentViewerActivity` — отдельная read-only Activity для локального открытия PDF, DOCX,
XLSX, текста, CSV/TSV и системно декодируемых JPEG/PNG/WebP/HEIC/HEIF. Она получает внешние
`ACTION_VIEW` intents и internal explicit intent после выбора файла через
`ACTION_OPEN_DOCUMENT`. Viewer не импортирует документ в editor и не меняет PDF-сессию в
`MainActivity`.

Manifest регистрирует только реально отображаемые MIME types. Для XLSX и DOCX используются
отдельные узкие filters
`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` и
`application/vnd.openxmlformats-officedocument.wordprocessingml.document`. Отсутствуют
`*/*`, `application/octet-stream`, `application/vnd.ms-excel`, `application/msword`,
XLSM/XLSB, DOCM/DOTX/DOTM, RTF, ODT, archive и другие document formats.
`CATEGORY_BROWSABLE` не используется.

## URI и временная копия

- исходный `content://` читается только через `ContentResolver`;
- `file://` допускается как ограниченный fallback, без преобразования `content://` в путь;
- display name очищается от separators, traversal, control characters и ограничивается по длине;
- bytes копируются вне main thread в `cacheDir/document_viewer` под случайным именем;
- общий предел входного файла — 250 MiB, PDF — 200 MiB, image — 100 MiB, text/table — 25 MiB,
  XLSX и DOCX — по 50 MiB;
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
DOCX также требует ZIP signature, `[Content_Types].xml`, `_rels/.rels`, единственный
безопасный internal `officeDocument` relationship, существующий main part и точный
WordprocessingML main content type. Main part может находиться не только в
`word/document.xml`: используется нормализованная цель relationship. MIME или `.docx` не
превращают обычный ZIP, DOCM/DOTX/DOTM или повреждённый package в DOCX.

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
- DOCX parser использует `java.util.zip`, streaming `XmlPullParser` и read-only block model.
  Читаются main document relationships, styles, numbering, settings validation, theme,
  inline raster media, headers, footers, footnotes и endnotes. Paragraph поддерживает
  несколько runs, preserved spaces, tab, line/page break, soft/no-break hyphen, Unicode,
  headings, alignment, indents и spacing. Run formatting включает safe font fallback,
  size, bold/italic/underline/strike, color/highlight, subscript/superscript; hidden text не
  показывается. Document defaults, paragraph/character styles, direct formatting и bounded
  `basedOn` inheritance применяются без циклов.
- Numbering поддерживает decimal, lower/upper letter, lower/upper Roman, bullets,
  многоуровневые placeholders, start values и indentation. Для неизвестного формата
  остаётся безопасный marker, текст paragraph не теряется.
- DOCX UI — локальный page-style renderer. `DocxHtmlRenderer` получает только безопасную
  immutable block-модель, экранирует весь текст/атрибуты и генерирует собственные HTML/CSS.
  `DocxPageStyle` переводит section twips в page size/margins, а Java-paginator выполняет
  стабильное приблизительное разбиение без заявления точного совпадения с Word. Headers
  отображаются перед body, footers после body, footnotes/endnotes отдельными secondary
  blocks; они не повторяются на вычисляемых страницах. Inserted track-change text
  показывается, deleted text пропускается, comments не отображаются. Сохранённый result
  fields показывается, field instructions, DDE и внешние команды не вычисляются и не
  выполняются.
- Word tables читают rows/cells, grid/widths, alignment, margins, borders, shading,
  `gridSpan`, vertical merge/alignment и paragraph content. HTML table использует
  `border-collapse`, `colspan`/`rowspan`, Word padding/borders/shading и локальный
  horizontal overflow внутри страницы. Большие таблицы paginator продолжает по строкам на
  следующей странице. Вложенная таблица остаётся частью контролируемой модели и HTML.
- `DocxLocalImageStore` читает только уже проверенные internal media entries, повторно
  проверяет безопасный package path, raster signature и decode bounds, после чего создаёт
  локальный `data:image/...;base64` URI в ограниченном бюджете. Word EMU задают размер с
  сохранением aspect ratio и `max-width` страницы. Floating/anchored image становится
  inline. SVG/EMF/WMF не передаются стороннему renderer и показываются placeholder.
- External hyperlink внутри DOCX не загружается. Только сохранённая безопасная `https` URI
  визуально показывается как ссылка и передаётся внешнему browser после явного нажатия;
  отсутствие browser обрабатывается без падения. Внутренний bookmark navigation пока не
  реализован.
- `DocxWebViewController` всегда держит JavaScript, file/content access, DOM/database
  storage, geolocation, mixed content, cookies и network loads выключенными. CSP допускает
  только inline CSS приложения и `data:` raster images; scripts, fonts, frames, objects и
  connections запрещены. WebView не содержит `JavascriptInterface` и сам не переходит по
  ссылкам. Только пользовательское нажатие по валидной HTTPS-ссылке передаётся внешнему
  browser.
- `WordViewerViewModel` удерживает выполняющийся parse, готовую модель и сформированный HTML
  при rotation. WebView восстанавливает scroll X/Y и zoom, использует fit-width overview,
  platform pinch/double-tap zoom и фиксированный `textZoom=100`, поэтому Android fontScale
  не меняет геометрию Word-страницы. Закрытие viewer, новый document или уничтожение
  ViewModel устанавливают cancellation token.
- images декодируются с bounds/inSampleSize, EXIF orientation, максимальной стороной 4096 px,
  fit-center, pinch/double-tap zoom и pan.

## DOCX security limits

- source не более 50 MiB; не более 1 024 ZIP entries, 150 MiB суммарного uncompressed
  content, 32 MiB на entry, ratio 100:1;
- не более 100 media entries, 16 MiB на media entry и 80 MiB media суммарно;
- ZIP обязан начинаться с local-header signature; central/local directory сверяются.
  Запрещены ZIP64, multi-disk, encrypted и неизвестные compression methods;
- запрещены absolute, backslash, traversal, control-character и duplicate/case-folded ZIP
  paths. Internal relationship target не может содержать `..`, scheme или выходить из
  package;
- XML: максимум 64 уровня, 3 миллиона events и 128 attributes на element; DOCDECL, DTD и
  custom entities запрещены;
- модель: 30 000 blocks, 20 000 paragraphs, 100 000 runs, 500 tables, 20 000 rows,
  50 000 cells, 100 image occurrences, 8 192 characters на run и 8 миллионов characters
  суммарно; `basedOn` depth 32, nested-table depth 4;
- запрещены macro-enabled content type, VBA, ActiveX, OLE/package/control relationships,
  external template/image/resource, embedded executable/script и unsafe external hyperlink;
- parsing выполняется вне main thread, проверяет cancellation, ловит `OutOfMemoryError` и
  переводит его в bounded too-large/memory state. Ни один bitmap всего документа и один
  giant document `Spannable` не создаются.

Внешние ресурсы DOCX не запрашиваются по сети, macros и embedded code не выполняются,
содержимое/metadata документа не логируются. `INTERNET` permission не добавлен.

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

## Старый бинарный DOC

`application/msword` не зарегистрирован. Официальный Apache POI HWPF 5.5.1 был проверен
отдельно: debug D8 при `minSdk 24` останавливается на
`org.apache.poi.poifs.nio.CleanerUtil` с `MethodHandle.invokeExact` (API 26+), а reachable
HWPF entry point в release R8 дополнительно требует отсутствующие Android `java.awt`
классы. Полный transitive graph добавлял бы около 9.39 MiB исходных JAR до shrink.

Собственный text-first DOC parser потребовал бы bounded CFB header/DIFAT/FAT/MiniFAT и
directory reader, `WordDocument` плюс выбранный `0Table`/`1Table`, FIB/CLX/Pcdt/PlcPcd,
compressed codepage и UTF-16 pieces, CP→FC validation и отдельный encrypted state. Без
реальных лицензируемых synthetic fixtures, fuzz/security hardening и physical smoke test
такой parser не считается стабильным. Подробности — в
[WORD_DOC_COMPATIBILITY_SPIKE.md](WORD_DOC_COMPATIBILITY_SPIKE.md).

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
XLSX, DOCX с RU/EN styles/lists/tables/images/links и rotation, повреждённый/зашифрованный/
слишком большой DOCX, macro/external-resource reject, horizontal/vertical table scroll,
lazy image release и возврат reading position. Проверить, что DOC не предлагается. Затем
quoted multiline CSV, UTF-8 BOM, Windows-1251 TXT, EXIF JPEG и HEIC/HEIF только на
устройствах с системным decoder. Отдельно пройти каталог и viewer на 320–412 dp, tablet,
landscape, font scale 1.0–1.5, light/dark и TalkBack.
