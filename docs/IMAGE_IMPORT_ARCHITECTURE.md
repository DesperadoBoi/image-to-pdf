# Image import architecture

## Назначение

Импорт изображений передаёт в приложение только `content://` `Uri`. Основной сценарий
использует внутренний `ImagePickerFragment` и `MediaStore`, а системные Android Photo
Picker и Storage Access Framework остаются доступными fallback. Абсолютные пути и
`MediaStore.DATA` не используются.

## Модель запроса

`ImageImportRequest` объединяет два обязательных значения:

- `ImageImportSource` — `IN_APP_GALLERY`, `GALLERY`, `CAMERA` или `FILES`;
- `ImageImportMode` — `NEW_DOCUMENT` или `APPEND_TO_DOCUMENT`.

`NEW_DOCUMENT` заменяет страницы текущей сессии, очищает относящееся к документу
временное состояние и результат предыдущей генерации, но не удаляет сохранённый PDF.
`APPEND_TO_DOCUMENT` сохраняет текущие страницы, добавляет новые в конец и сбрасывает
только завершённый operation status. Пустой результат системного выбора является no-op.

`ImagePickerFragment` хранит ordered selection отдельно от source metadata, а создание
`PageItem` сосредоточено в `DocumentSessionViewModel.importImages()`. Поэтому смешанный
выбор MediaStore, Photo Picker, Camera и Files проходит через один pipeline без потери
порядка.

## Текущие источники

### In-app Gallery

`IN_APP_GALLERY` читает разрешённые системе изображения через отдельный
`MediaGalleryRepository`. Версионная permission model, partial access, альбомы,
миниатюры и selection описаны в [IN_APP_GALLERY.md](IN_APP_GALLERY.md).

### Android Photo Picker

`GALLERY` используется fallback-действием и запускает системный Android Photo Picker через
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

## Permissions

Manifest содержит только `READ_MEDIA_IMAGES`, `READ_MEDIA_VISUAL_USER_SELECTED` и
`READ_EXTERNAL_STORAGE` с `maxSdkVersion="32"`. Partial grant Android 14+ не выдаётся за
full access. При denied состоянии Camera, Photo Picker и Files продолжают работать.
