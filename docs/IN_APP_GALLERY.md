# In-app gallery

## Назначение

`ImagePickerFragment` — внутренний экран выбора изображений в существующем fragment-контейнере `MainActivity`. Он не создаёт новую Activity, не использует Navigation Component и передаёт выбранные `content://` `Uri` в общий `DocumentSessionViewModel` import pipeline.

## Модель доступа

- Android 13 и новее используют `READ_MEDIA_IMAGES` для полного доступа;
- Android 14 и новее дополнительно учитывают `READ_MEDIA_VISUAL_USER_SELECTED`: такой grant отображается как `PARTIAL`, а не как полная галерея;
- Android 7–12L используют `READ_EXTERNAL_STORAGE`, ограниченный в manifest через `maxSdkVersion="32"`;
- `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, `CAMERA` и `INTERNET` не используются.

`GalleryAccessState` различает `FULL`, `PARTIAL`, `DENIED` и `NOT_REQUESTED`. При частичном доступе экран показывает только разрешённые системой элементы и предлагает изменить доступ. При отказе остаются доступны Android Photo Picker и Storage Access Framework. После окончательного отказа также показывается переход в настройки приложения.

## MediaStore и альбомы

`MediaGalleryRepository` не зависит от Fragment или View. Он выполняет query на отдельном single-thread executor, закрывает `Cursor` через try-with-resources, читает только metadata и `content://` Uri, не использует `DATA` и абсолютные пути. Изображения сортируются от новых к старым.

`MediaAlbumAggregator` группирует уникальные media ID по bucket, считает изображения, выбирает самое новое cover и применяет локализованный fallback для отсутствующего bucket name. Camera, Screenshots и Download получают предсказуемый приоритет, если такие реальные buckets присутствуют.

`ImagePickerViewModel` хранит явный `GalleryUiState`: `LOADING`, `CONTENT`, `EMPTY`,
`PERMISSION_REQUIRED` и `ERROR`. Первоначальный и повторный MediaStore query начинаются с
`LOADING`; `EMPTY` публикуется только после успешного ответа с нулём изображений. При
фоновом обновлении последнее `CONTENT` остаётся видимым. Каждый query получает operation ID,
поэтому поздний результат старого запроса не может заменить более новое содержимое.
Уход с экрана, `onStop` и PDF export не очищают content и не переводят состояние в `EMPTY`.

## Миниатюры и выбор

`GalleryThumbnailLoader` использует ограниченный пул из трёх потоков, bounds decode, `inSampleSize`, EXIF transform и финальное уменьшение до размера View. Ключ запроса содержит `Uri` и размер. Adapter проверяет ключ recycled ViewHolder и освобождает stale bitmap; loader закрывается вместе с Fragment.

`ImageSelection` хранит уникальные `Uri` в порядке выбора. Снятие выбора не меняет порядок остальных, повторный выбор добавляет элемент в конец, а snapshot неизменяем. Fragment-scoped `ImagePickerViewModel` сохраняет selection, текущий album и list/grid mode при rotation и не хранит Bitmap.

## Camera, Files и импорт

Первая плитка Recent и сетки «Все изображения» запускает существующий `TakePicture`/`FileProvider` flow. Успешный app-owned снимок добавляется в selection; cancel ничего не меняет. Неимпортированные camera-файлы удаляются при выходе из picker.

«Проводник» запускает `OpenMultipleDocuments(image/*)`, сохраняет порядок provider и best-effort persistable read grant. Photo Picker остаётся fallback без широкого доступа к медиатеке.

Selection и source metadata хранятся отдельно: это позволяет импортировать смешанный порядок MediaStore, Photo Picker, Camera и Files. `NEW_DOCUMENT` заменяет session и удаляет только старые app-owned camera-файлы. `APPEND_TO_DOCUMENT` добавляет страницы в конец, сохраняет предыдущий PDF result и прокручивает Editor к первой новой странице.

После импорта picker атомарно заменяется Editor и не добавляется в export/result back stack.
Переход `Editor → PdfResultFragment` не проходит через `ImagePickerFragment`.
