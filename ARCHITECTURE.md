# ARCHITECTURE

## Статус документа

Этот документ фиксирует целевую архитектуру проекта. Часть описанных компонентов пока не реализована и не должна восприниматься как текущее состояние кодовой базы.

## Текущая техническая основа

- платформа: Android;
- production-код: Java;
- UI: XML Views;
- Kotlin не используется для production-кода;
- Jetpack Compose не используется;
- Gradle-модуль: один `app`;
- `minSdk = 24`;
- `targetSdk = 36`;
- `compileSdk` в `app/build.gradle.kts` задан как `release(36) { minorApiLevel = 1 }`;
- `sourceCompatibility = JavaVersion.VERSION_11`;
- `targetCompatibility = JavaVersion.VERSION_11`;
- приложение должно работать локально;
- серверная часть в MVP отсутствует;
- интернет для основной функции не требуется.

## Целевая организация пакетов

Будущая структура production-кода:

```text
com.desperadoboi.imagetopdf
├── ui
│   ├── home
│   ├── editor
│   ├── gallery
│   ├── export
│   └── result
├── model
├── document
│   ├── image
│   ├── pdf
│   ├── spreadsheet
│   └── text
├── image
├── pdf
└── util
```

`MainActivity` сейчас является контейнером экранов. `HomeFragment` является dashboard, `ImagePickerFragment` показывает внутреннюю MediaStore-галерею и системные fallback-источники, `AllToolsFragment` показывает секционный каталог инструментов, `EditorFragment` отвечает за страницы и генерацию, а `PdfResultFragment` — за отдельный успешный сценарий.

`DocumentViewerActivity` является отдельной exported read-only точкой `ACTION_VIEW`. Она не
участвует в fragment navigation `MainActivity`, не получает общий editor ViewModel и не
импортирует внешние документы в текущую сессию.

## Целевая UI-архитектура

- одна `Activity` как контейнер;
- отдельные `Fragment` для домашнего dashboard, полного каталога и редактора страниц;
- `HomeFragment` показывает четыре колонки из восьми инструментов, не содержит фиктивной истории, поиска или настроек;
- `AllToolsFragment` использует один секционный `RecyclerView` и существующий fragment-контейнер без новой Activity и Navigation Component;
- immutable `ToolCatalog` является единственной точкой истины для `ToolId`, category, availability, title/icon resources и порядка Home/каталога;
- `PageEditFragment` открывается поверх редактора в существующем fragment-контейнере, использует stable ID страницы и общий `DocumentSessionViewModel`, содержит основной просмотр, горизонтальную ленту и обычный прямоугольный crop;
- обычный `PageEditFragment` не показывает perspective correction: rotate left/right расположены как overlay-кнопки в нижних углах preview, а нижняя панель содержит только crop и done;
- activity-scoped `DocumentSessionViewModel` для состояния пользовательской сессии;
- `Fragment` читает состояние из `DocumentSessionViewModel` и применяет точечные обновления XML UI;
- однонаправленный поток: `UI action -> ViewModel -> сервис/компонент -> новое состояние -> UI`;
- `ViewModel` не хранит `Activity`, `Fragment` или `View`;
- `Context` передаётся только компонентам, которым он действительно нужен;
- DI-фреймворк на старте не добавляется;
- зависимости передаются явно через конструкторы или простой `AppContainer`.

### Smart Scan

`SmartScanFragment` и `ScanReviewFragment` работают в существующей `MainActivity` через
обычный FragmentManager. Activity-scoped `ScanSessionViewModel` с `SavedStateHandle`
сохраняет ordered `ScanPage`, pending capture, ownership, rotation, perspective и grid state.
Чистые `ScanSessionReducer`, `LevelStateResolver`, `ScanCameraState` и navigation coordinator
не зависят от Android UI и покрываются JVM-тестами.

Текущий сценарий: `Home Smart Scan tile -> CameraX -> manual perspective review -> Editor
-> PDF`. Perspective-модели, overlay, geometry и bitmap transform переиспользуются общей
thumbnail/preview/PDF pipeline; отдельный исправленный JPEG не создаётся.

CameraX captures создаются в `filesDir/captured_images`. При отмене их удаляет scan session,
а после finish ownership передаётся `DocumentSessionViewModel`. Gallery Uri внешние и не
удаляются. Auto edge detection и auto-capture не реализованы.

## Основные технические решения

### Выбор изображений

- основной выбор выполняется во внутреннем `ImagePickerFragment` через `MediaStore`;
- `MediaGalleryRepository` выполняет query вне UI thread и возвращает только metadata и `content://` Uri;
- Android Photo Picker остаётся fallback для выбора отдельных изображений;
- Android 14 partial selected-photos access отображается отдельно от полного доступа;
- для обычного camera import использовать системное приложение камеры через `ActivityResultContracts.TakePicture`;
- для отдельного Smart Scan использовать CameraX Preview/ImageCapture с задней камерой;
- destination `Uri` для камеры создавать заранее через `CapturedImageStorage` и `FileProvider`;
- `FileProvider` ограничен каталогом `filesDir/captured_images`, не экспортируется и выдаёт только временные разрешения на `content://` Uri;
- работать с `Uri`;
- не пытаться получать абсолютные файловые пути.

### Чтение файлов

- использовать `ContentResolver`;
- открывать данные через `InputStream`;
- поддерживать изображения из галереи и `document providers`.
- снимки камеры хранить в app-specific storage как app-owned файлы и также читать через `ContentResolver` по `content://` Uri.

Для viewer `IncomingDocumentLoader` читает metadata и bytes через `ContentResolver`,
`DocumentTypeResolver` проверяет MIME/signature/extension, а `TemporaryDocumentStore`
создаёт bounded случайно именованную копию в app cache. Seekable PDF открывается только из
этой копии. Старые viewer files имеют age-based cleanup; Share выдаёт FileProvider URI после
явного действия пользователя. Подробный контракт — в [docs/DOCUMENT_VIEWER.md](docs/DOCUMENT_VIEWER.md).

### Обработка изображений

- не загружать все полноразмерные `Bitmap` одновременно;
- хранить в состоянии `Uri` и небольшие миниатюры;
- `PreviewImageLoader` декодирует изображение для полноэкранного просмотра на фоновой очереди через `ContentResolver`, bounds, `inSampleSize`, EXIF orientation и ручной поворот;
- `ZoomableImageView` реализует fit-center, pinch-to-zoom и ограниченный pan через стандартные Android `Matrix` и gesture APIs без сторонней библиотеки;
- `PageItem` хранит immutable `PageEditSpec`: `PerspectiveQuad` в координатах ориентированного изображения и `CropRect` в координатах rectified result;
- `RectCropOverlayView` и `DocumentPerspectiveOverlayView` отвечают только за отрисовку и временную touch-геометрию; применённые параметры записывает `PageEditFragment` через `DocumentSessionViewModel`;
- `PageBitmapProcessor` задаёт единый порядок EXIF, manual rotation, perspective и crop для `ThumbnailLoader`, `PreviewImageLoader` и `PdfGenerator`;
- perspective correction использует стандартный `Matrix.setPolyToPoly` с четырьмя парами точек и отрисовку через `Canvas`, без OpenCV и постоянных обработанных файлов;
- `SourceResolutionCalculator` учитывает долю perspective quad и crop при выборе sampled decode под DPI выбранного профиля без декодирования всех исходников одновременно;
- `PageItem` хранит `PageSource`, чтобы отличать внешние изображения галереи от app-owned снимков камеры;
- удалять app-owned camera-файлы при удалении соответствующей страницы и при создании новой сессии, не удаляя gallery `Uri`;
- во время создания PDF обрабатывать изображения последовательно;
- декодировать `Bitmap` с уменьшением под требуемый размер страницы;
- учитывать `EXIF orientation`;
- освобождать крупный `Bitmap` после обработки страницы.

### Создание PDF

- для первоначальной версии использовать нативный `android.graphics.pdf.PdfDocument`;
- `PdfExportSheet` использует progressive disclosure, формирует immutable `PdfExportRequest`, а незавершённый `PdfExportDraft` хранится в session ViewModel;
- профили качества 96/144/216 DPI передаются в `RasterTargetCalculator` из единого enum;
- ориентация A4 и image-sized страниц вычисляется в `PdfPageLayoutCalculator`;
- держать одновременно открытой только одну PDF-страницу;
- генерировать документ последовательно;
- не блокировать основной UI-поток.

### Фоновая работа

- на первоначальном этапе использовать `ExecutorService`;
- передавать прогресс через состояние `ViewModel`;
- `WorkManager` не добавлять без отдельного обоснованного сценария;
- в рамках одной пользовательской сессии разрешать только одну активную генерацию PDF одновременно.

### Сохранение

- использовать Storage Access Framework;
- пользователь сам выбирает место сохранения;
- «Конвертировать» при отсутствии output `Uri` запускает `CreateDocument` и автоматически продолжает генерацию после выбора; отдельной кнопки «Обзор» нет;
- не запрашивать широкий доступ ко всему файловому хранилищу без необходимости.

### Результат PDF

- успешная операция публикует `PdfResult` и отдельный consume-once `PdfSuccessEvent`, привязанный к `operationId`;
- operation-aware coordinator один раз переводит успешную активную генерацию из Editor в `PdfResultFragment`; cancel/error остаются в Editor;
- `PdfSuccessEvent` consume-ится только result screen, где показывает одноразовый `PdfSuccessBanner`; Editor не содержит карточку последнего PDF;
- `PdfResultMetadataReader` получает display name и size через `ContentResolver` на background executor, использует `ParcelFileDescriptor.statSize` и counted bytes fallback;
- `PdfLocationLabelResolver` преобразует provider/document metadata в понятное название без раскрытия authority, document ID, пути или content Uri;
- `PdfPreviewLoader` через `PdfRenderer` рендерит только первую страницу в bounded bitmap и закрывает page, renderer и descriptor;
- preview bitmap принадлежит Fragment, не хранится во ViewModel и освобождается при stale result/destroy;
- Open и Share используют системные intent с `application/pdf` и read grant;
- Back/Edit pages сохраняют session и output PDF, а New document удаляет только app-owned camera captures.

### Безопасность и приватность

- не добавлять `INTERNET` permission для основной офлайн-функции;
- не отправлять изображения и PDF на внешние серверы;
- не логировать содержимое документов;
- не хранить пользовательские документы дольше необходимого;
- не добавлять аналитику без отдельного решения.

## Зависимости

- новые production-зависимости добавляются только при необходимости;
- перед добавлением зависимости нужно указать, зачем она нужна;
- перед добавлением зависимости нужно указать, почему стандартного Android API недостаточно;
- перед добавлением зависимости нужно указать, какие есть риски;
- Gradle, Android Gradle Plugin, SDK и библиотеки не обновляются попутно с обычной функциональной задачей.

## Тестирование

- unit-тесты использовать для чистой логики;
- отдельно тестировать расчёт размеров и размещения страницы;
- отдельно тестировать порядок страниц и повороты;
- instrumentation-тесты добавлять только для сценариев, которым действительно нужен Android framework.

## Оптимизация растров для PDF

- Генерация PDF использует профили `COMPACT = 96`, `BALANCED = 144` и `HIGH = 216` DPI; `RasterTargetCalculator.TARGET_DPI = 144` остаётся совместимым значением сбалансированного профиля.
- `RasterTargetCalculator` является чистой Java-логикой и рассчитывает целевой размер bitmap по ориентированным размерам источника, фактической области содержимого PDF и режиму размещения FIT/FILL.
- `PdfGenerator` сохраняет двухэтапный `BitmapFactory` decode с чтением bounds и `inSampleSize`, затем применяет EXIF/ручной поворот и выполняет точное уменьшение через `ImageBitmapTransformer.scaleDownToFit` только если декодированный bitmap всё ещё больше рассчитанного target.
