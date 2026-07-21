# UI foundation

## Назначение

UI foundation превращает стартовый экран ImageToPDF в расширяемый каталог локальных
PDF-инструментов. Каталог не означает, что все показанные инструменты реализованы:
доступность каждого элемента явно хранится в `ToolCatalog`.

Приложение остаётся offline-first и privacy-first. Документы обрабатываются локально,
серверная часть, аналитика и реклама не используются.

## Home

`HomeFragment` — dashboard без истории файлов, поиска, настроек и нижней навигации.
Экран содержит заголовок, подзаголовок и четыре колонки инструментов:

1. Изображение в PDF;
2. Умное сканирование;
3. Импорт PDF;
4. Сжать PDF;
5. PDF в JPG;
6. Слияние PDF;
7. Камера;
8. Ещё.

`IMAGE_TO_PDF` открывает `ImagePickerFragment` в режиме `NEW_DOCUMENT`. Внутренний экран
показывает Recent, реальные MediaStore albums, трёхколоночную сетку, Camera и Files;
Photo Picker остаётся fallback при partial/denied доступе. Отдельный инструмент `CAMERA`
сохраняет быстрый прямой запуск камеры, `MORE` открывает `AllToolsFragment`.

## All Tools

`AllToolsFragment` работает в существующей `MainActivity` и не использует Navigation
Component. Один `RecyclerView` объединяет полноширинные заголовки секций и трёхколоночные
ячейки:

- «Создать и преобразовать»;
- «Популярные»;
- «Изменить PDF»;
- «Безопасность».

`CAMERA` остаётся отдельным home-входом к созданию первой страницы, а `MORE` является
навигационной плиткой, поэтому они не дублируются внутри каталога. Доступное
«Изображение в PDF» возвращается к тому же launcher в `HomeFragment`; логика импорта не
дублируется.

## Выбор источника изображений

Home и Editor открывают один `ImagePickerFragment` соответственно в `NEW_DOCUMENT` и
`APPEND_TO_DOCUMENT`. `ImageImportRequest` и ordered import entries направляют результат
любого источника в единый session pipeline.

`OpenMultipleDocuments` ограничен `image/*`, поддерживает несколько `Uri`, папки и поиск,
когда их предоставляет системный provider. Для Files grant сохраняется best effort. Широкие
storage permissions не используются. Полный контракт описан в
[IMAGE_IMPORT_ARCHITECTURE.md](IMAGE_IMPORT_ARCHITECTURE.md) и
[IN_APP_GALLERY.md](IN_APP_GALLERY.md).

## ToolCatalog

Пакет `ui.tools` содержит:

- `ToolId` — стабильную идентичность инструмента;
- `ToolAvailability` — `AVAILABLE` или `COMING_SOON`;
- `ToolCategory` — категорию каталога;
- immutable `ToolDefinition` — ссылки на string/drawable resources, категорию,
  доступность и порядок;
- `ToolCatalog` — единственный immutable список определений.

Адаптеры получают готовые определения и передают наружу только
`onToolSelected(ToolId)`. Они не открывают `Fragment` самостоятельно.

Доступны сейчас:

- `IMAGE_TO_PDF`;
- `CAMERA`;
- `MORE`.

Помечены «Скоро» и не запускают фиктивный экран:

- `SMART_SCAN`, `IMPORT_PDF`, `COMPRESS_PDF`, `PDF_TO_JPG`, `MERGE_PDF`;
- `DOCX_TO_PDF`, `PPT_TO_PDF`, `PDF_TO_WORD`, `PDF_TO_PPT`;
- `ID_SCAN`, `PRINT_PDF`, `DRAW_ON_PDF`, `ADD_TEXT`, `SIGN_PDF`;
- `LOCK_PDF`, `UNLOCK_PDF`.

## Обычный редактор страницы

`PageEditFragment` сохраняет stable page ID, activity-scoped `DocumentSessionViewModel`,
фоновые preview loads, zoom/pan и горизонтальную ленту.

Editor и PageEdit используют единый 24×24 VectorDrawable-пакет для back, add, reorder,
rotate, crop, done, delete, PDF и reset. Иконки tint-ятся semantic palette, действия имеют
48dp touch target, а destructive delete отделён собственным цветом. Page cards используют
фиксированную область номера, увеличенную миниатюру и точечные payload-обновления без
`notifyDataSetChanged`. Основная кнопка создания PDF закреплена снизу без большого внешнего
контейнера; progress, cancel, status и result появляются только в соответствующем состоянии.

Reorder использует симметричный grip 2×3 без постоянного круглого контейнера. Drag
запускается сразу по `ACTION_DOWN` на grip или долгим удержанием карточки/миниатюры;
rotate/delete не участвуют в long-press fallback. Во время drag карточка получает elevation,
небольшие alpha/scale и activated state, которые полностью сбрасываются в `clearView`.

«Создать PDF» открывает progressive `PdfExportSheet`: основной вид содержит имя, три
компактных quality choices, summary дополнительных параметров и одну яркую кнопку
«Конвертировать». Размер страницы, ориентация и поля доступны через раскрываемые setting
rows и single-choice dialogs. Отдельной кнопки «Обзор» нет: при отсутствии output Uri
«Конвертировать» запускает системный `CreateDocument` и продолжает генерацию после выбора.
В sheet нет фиктивных password/signature/encryption опций.

Success оставляет пользователя в Editor, один раз показывает `PdfSuccessBanner` и обновляет
компактную карточку последнего PDF с Share/Open. `PdfResultFragment` открывается только по
явному действию, показывает bounded preview, page badge, имя максимум в две строки,
объединённые page count/size и human-readable location. Share является primary, Open —
secondary, возврат к страницам и новый документ — компактные text actions. Контракт
зафиксирован в [PDF_RESULT_SCREEN.md](PDF_RESULT_SCREEN.md).

Обычный режим содержит:

- toolbar с back, «Страница N» и компактным reset;
- крупный fit-center preview на нейтральном тёмном фоне;
- круглые rotate left/right у нижних левого и правого углов preview;
- счётчик `N / M` и ленту миниатюр с акцентной рамкой текущей страницы;
- только «Обрезать» и основную кнопку «Готово» снизу.

Прямоугольная обрезка использует существующий `RectCropOverlayView`, сетку и восемь
handles. В режиме crop лента и rotate скрыты, toolbar показывает «Обрезка» и reset, а
закреплённая нижняя панель — «Отмена» и «Применить». Touch radius handles остаётся больше
их визуального радиуса.

## Perspective и Smart Scan

Perspective correction скрыта из обычного Image-to-PDF editor. В нём нет кнопки
«Документ», quad overlay или режима выравнивания документа.

При этом сохранены `PerspectiveQuad`, `DocumentPerspectiveOverlayView`, quad validation,
midpoint geometry, perspective bitmap transform и применение edit spec в thumbnail,
preview и PDF pipelines. Контракт `SmartScanFlowCoordinator` резервирует будущие входы
`openSmartScanCapture()` и `openSmartScanEditor(pageId)`, но сейчас не подключён к UI.

Будущий отдельный flow:

```text
Home Smart Scan tile
→ Camera
→ Document perspective editor
→ Result editor
→ PDF
```

Автоматическое определение документа и настоящий Smart Scan в текущую задачу не входят.

## PNG assets

Каталог использует готовые 512×512 PNG из `res/drawable-nodpi`:

- `ic_tool_image_to_pdf.png`, `ic_tool_smart_scan.png`, `ic_tool_camera.png`;
- `ic_tool_import_pdf.png`, `ic_tool_compress_pdf.png`, `ic_tool_pdf_to_jpg.png`;
- `ic_tool_merge_pdf.png`, `ic_tool_more.png`, `ic_tool_docx_to_pdf.png`;
- `ic_tool_ppt_to_pdf.png`, `ic_tool_pdf_to_word.png`, `ic_tool_pdf_to_ppt.png`;
- `ic_tool_id_scan.png`, `ic_tool_print_pdf.png`, `ic_tool_draw_pdf.png`;
- `ic_tool_add_text.png`, `ic_tool_signature.png`, `ic_tool_lock_pdf.png`;
- `ic_tool_unlock_pdf.png`.

PNG уже содержат внутреннюю тёмную плитку и собственные цвета. В layout обязательно
используется `app:tint="@null"`, `scaleType="fitCenter"`; дополнительный непрозрачный круг
под иконкой не добавляется. Растровые файлы не следует пересохранять при UI-изменениях.

## Добавление инструмента

1. Добавить стабильный `ToolId`.
2. Добавить title в `strings.xml` и drawable без пользовательского текста в Java.
3. Добавить ровно один `ToolDefinition` в `ToolCatalog` с категорией, доступностью и
   уникальным порядком.
4. Для home-инструмента задать `showOnHome = true` и уникальный `homeOrder`.
5. До реализации использовать `COMING_SOON`; не связывать плитку с пустым экраном.
6. Добавить или обновить JVM-тесты каталога.
7. Подключать действие во `Fragment` через callback по `ToolId`, не из адаптера.
