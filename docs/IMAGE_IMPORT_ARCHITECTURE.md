# Image import architecture

## Назначение

Импорт изображений использует системные Android-компоненты и передаёт в приложение
только `content://` `Uri`. Приложение не запрашивает широкие разрешения на хранилище,
не перечисляет медиатеку через `MediaStore` и не преобразует `Uri` в абсолютные пути.

## Модель запроса

`ImageImportRequest` объединяет два обязательных значения:

- `ImageImportSource` — `GALLERY`, `CAMERA` или `FILES`;
- `ImageImportMode` — `NEW_DOCUMENT` или `APPEND_TO_DOCUMENT`.

`NEW_DOCUMENT` заменяет страницы текущей сессии, очищает относящееся к документу
временное состояние и результат предыдущей генерации, но не удаляет сохранённый PDF.
`APPEND_TO_DOCUMENT` сохраняет текущие страницы, добавляет новые в конец и сбрасывает
только завершённый operation status. Пустой результат системного выбора является no-op.

`ImageSourceSheet` отвечает только за выбор источника. `ImagePickerLauncher` и
`ImageImportCoordinator` являются точкой запуска источников, а создание `PageItem`
сосредоточено в `DocumentSessionViewModel.importImages()` и
`importCameraImage()`. Поэтому любой источник возвращает данные в один и тот же pipeline.

## Текущие источники

### Gallery

`GALLERY` запускает системный Android Photo Picker через
`ActivityResultContracts.PickMultipleVisualMedia` с `ImageOnly`. Он поддерживает выбор
нескольких изображений и не требует storage permissions. Полученные страницы имеют
`PageSource.GALLERY`.

### Camera

`CAMERA` сохраняет существующий flow `ActivityResultContracts.TakePicture`.
`CapturedImageStorage` заранее создаёт app-owned файл, а `FileProvider` выдаёт камере
временный `content://` `Uri`. `CAMERA` permission приложению не требуется. Полученная
страница имеет `PageSource.CAMERA` и хранит имя принадлежащего приложению файла.

### Files

`FILES` запускает системный Storage Access Framework через
`ActivityResultContracts.OpenMultipleDocuments` с MIME type `image/*`. Доступные поиск,
папки и providers определяются системным файловым UI. Выбор возвращает один или несколько
`Uri`; изображения не копируются без необходимости. Полученные страницы имеют
`PageSource.FILES`.

Для каждого Files `Uri` приложение best effort вызывает
`ContentResolver.takePersistableUriPermission()` с read flag. `SecurityException`
игнорируется: provider может не поддерживать persistable grant, но текущий grant остаётся
доступным в активной сессии.

## Владение и cleanup

- `GALLERY` и `FILES` принадлежат внешним providers и никогда не удаляются приложением;
- только `CAMERA` указывает на app-owned файл в `filesDir/captured_images`;
- удаление страницы, замена документа и создание новой сессии очищают только camera-файлы;
- `ThumbnailLoader` и `PdfGenerator` читают все источники одинаково через
  `ContentResolver` и не зависят от `PageSource`;
- stable ID, rotation, crop, perspective и reorder сохраняют исходный `PageSource`.

Отмена Photo Picker, камеры или Files не меняет страницы, `PdfResult` или status и не
показывает Toast/Snackbar.

## Будущая внутренняя галерея

Будущий `IN_APP_GALLERY` добавляется как новый `ImageImportSource` и launcher в
`ImageImportCoordinator`. Экран галереи должен вернуть выбранные `Uri` в существующий
`importImages()` pipeline; `EditorFragment`, создание `PageItem`, thumbnail и PDF logic
не должны получать отдельную копию импорта.

Полноценная внутренняя галерея отложена в отдельный этап, потому что ей потребуются
`MediaStore`, версия-зависимая политика разрешений, paging, thumbnail cache и обработка
больших библиотек. В текущем этапе нет `MediaStore` query, album browser или новых media
permissions.
