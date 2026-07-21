# Smart Scan

## Назначение

«Сканировать документ» — отдельный локальный сценарий создания PDF из фотографий страниц:

```text
Home / All Tools
→ CameraX camera
→ ручная коррекция перспективы
→ добавление страницы
→ существующий EditorFragment
→ создание PDF
```

Первая версия является честным ручным сканером. Она не определяет границы документа,
не делает снимок автоматически и не сообщает, что документ найден. Все изображения
обрабатываются на устройстве и не отправляются в сеть.

## CameraX architecture

`SmartScanFragment` работает в существующей `MainActivity`. Новая Activity и Navigation
Component не используются. CameraX 1.6.1 подключён только официальными артефактами:

- `camera-core` — `Preview`, `ImageCapture`, `CameraSelector` и camera control API;
- `camera-camera2` — реализация `ProcessCameraProvider` поверх Camera2;
- `camera-lifecycle` — lifecycle binding use cases;
- `camera-view` — XML `PreviewView` с сохранением aspect ratio и rotation transform.

Задняя камера выбирается через `CameraSelector.DEFAULT_BACK_CAMERA`. `Preview` и
`ImageCapture` связываются с `viewLifecycleOwner`; `ImageCapture` использует
`CAPTURE_MODE_MINIMIZE_LATENCY`. Перед каждым снимком обновляется target rotation.
Снимок пишется асинхронно в app-owned файл, двойной shutter блокируется состоянием
`captureInProgress`, а use cases освобождаются в `onDestroyView`.

## Permission handling

Поскольку CameraX обращается к камере внутри приложения, manifest содержит только новое
разрешение `android.permission.CAMERA`; storage и `INTERNET` permissions не добавляются.
Камера объявлена как необязательное hardware feature, поэтому устройства без камеры могут
использовать импорт из галереи.

`ActivityResultContracts.RequestPermission` различает:

- запрос ещё не выполнялся — диалог запускается один раз;
- rationale — показывается локальное объяснение и кнопка повторного запроса;
- denied/permanently denied — остаётся системный выбор изображения;
- permanently denied — дополнительно доступен переход в настройки;
- granted — запускается CameraX preview.

Флаг выполненного запроса хранится в `SavedStateHandle`, поэтому permission dialog не
зацикливается после recreation.

## Camera UI

Preview занимает центральную область между toolbar и нижней панелью и не пересекает
system insets. `DocumentFrameOverlayView` показывает только предполагаемую рамку документа:
полупрозрачное затемнение снаружи, тонкий контур и четыре крупных угла. Это не результат
edge detection.

Фонарик управляется через `CameraControl.enableTorch()` и доступен только при
`CameraInfo.hasFlashUnit()`. При pause он всегда выключается. Сетка третей переключается
отдельно и сохраняется в `SavedStateHandle`.

Overflow содержит подсказку о локальной обработке и, когда требуется, переход к настройкам
приложения. Sound toggle отсутствует.

## Индикатор уровня

`LevelStateResolver` — чистая Java-логика с состояниями `UNAVAILABLE`, `TILTED`,
`ALMOST_LEVEL`, `LEVEL`. Она вычисляет угол по accelerometer, использует отдельные пороги
входа/выхода для hysteresis и не зависит от Fragment.

`LevelIndicatorView` показывает компактную линию и движущуюся точку над shutter. В состоянии
`LEVEL` точка становится semantic green. Короткий haptic выполняется только при первом
входе в `LEVEL`; повтор возможен после выхода из него. При отсутствии accelerometer
индикатор скрывается, камера продолжает работать. Listener снимается в `onPause` и
`onDestroyView`.

## Scan session

Activity-scoped `ScanSessionViewModel` хранит immutable `ScanSessionState`:

- упорядоченный список `ScanPage`;
- pending capture/current review page;
- `captureInProgress`;
- session/page IDs;
- rotation, perspective quad, original mode, ownership и timestamps.

Отдельный immutable `ScanCameraState` хранит grid и transient torch capability/state; факт
permission request также принадлежит `ScanSessionViewModel`.

Изменения проходят через чистый `ScanSessionReducer`. Capture result привязан к уникальному
page ID, поэтому повторный callback не добавляет страницу дважды.

Базовое состояние сохраняется в `SavedStateHandle`: Uri — строками, quad — нормализованными
координатами, остальные поля — сериализуемыми значениями. FragmentManager восстанавливает
camera/review back stack. Прерванный capture не запускается повторно. При восстановлении
отсутствующие app-owned файлы удаляются из session; недоступный внешний Uri показывает
ошибку review без падения.

## Scan review и perspective

`ScanReviewFragment` загружает только один bounded preview на background executor. Он
переиспользует `PreviewImageLoader`, `DocumentPerspectiveOverlayView`, `PerspectiveQuad` и
геометрию существующего редактора. В review отображаются четыре draggable corner handles;
side handles скрыты.

- «Переснять» и delete удаляют pending app-owned файл и возвращают к камере;
- «Повернуть» меняет только metadata на 90°, не перекодируя JPEG;
- «Авто» в первой версии восстанавливает базовую прямоугольную рамку. Это reset/default
  crop, а не компьютерное определение границ;
- «Оригинал» сохраняет весь снимок и отключает perspective correction;
- «Добавить страницу» сохраняет rotation и `PerspectiveQuad` в `ScanPage` и возвращает к
  камере.

Bitmap не обрабатывается в UI thread. Отдельная исправленная JPEG-копия не создаётся:
после передачи в document session существующий `PageBitmapProcessor` применяет единый
порядок EXIF → manual rotation → perspective → crop для thumbnail, preview и PDF.

## Ownership и cleanup

CameraX captures создаются через `CapturedImageStorage` в
`filesDir/captured_images` и публикуются только как `content://` Uri через неэкспортируемый
`FileProvider`. Они не добавляются в MediaStore и системную Gallery.
Каталог `captured_images` исключён из cloud backup и device transfer rules.

- cancel/retake/capture error удаляют только app-owned файлы;
- gallery Uri никогда не удаляются приложением;
- после finish ownership camera-файлов передаётся `DocumentSessionViewModel`;
- Editor удаляет их при удалении страницы или очистке/замене document session;
- файл не удаляется, пока соответствующий `PageItem` используется Editor/PDF pipeline.

## Навигация

`Home → SmartScanFragment` добавляет scan entry в обычный FragmentManager back stack.
`ScanReviewFragment` добавляется поверх камеры и снимается после retake/add. Finish
атомарно импортирует подготовленные страницы в `DocumentSessionViewModel`, очищает scan
back stack и открывает `EditorFragment`. Back без страниц отменяет scan сразу; Back после
добавления страниц показывает подтверждение и удаляет только app-owned scan captures.

## Ограничения первой версии и следующие этапы

Пока не реализованы:

- автоматическое определение границ;
- auto-capture;
- OCR;
- document filters;
- ID/QR modes;
- автоматическая оценка качества/резкости.

Следующие этапы могут добавить edge detection и осознанный auto-capture поверх текущего
manual flow, не меняя ownership, session и PDF pipeline.
