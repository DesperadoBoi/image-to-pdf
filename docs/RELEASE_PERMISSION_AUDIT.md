# Аудит разрешений release-варианта

Аудит относится к `applicationId = com.desperadoboi.imagetopdf`, `versionCode = 1`,
`versionName = 1.0.0`, `minSdk = 24` и `targetSdk = 36`. Источник истины — merged manifest
release-варианта, созданный задачами Android Gradle Plugin.

## Итоговые permissions

| Permission | Зачем нужен | Версии Android | Пользовательский сценарий | Что происходит при отказе |
|---|---|---|---|---|
| `android.permission.CAMERA` | Доступ CameraX к камере внутри приложения | Runtime-разрешение на Android 6.0+; приложение поддерживает API 24+ | «Сканировать документ»: preview и съёмка страницы | CameraX не запускается; остаётся системный выбор изображения, а пользователь может повторить запрос или открыть настройки |
| `android.permission.READ_MEDIA_IMAGES` | Чтение изображений через MediaStore для встроенной галереи | Android 13+ | Просмотр альбомов и множественный выбор во встроенной галерее | Встроенная галерея не читает MediaStore; доступны Photo Picker, Проводник и камера |
| `android.permission.READ_MEDIA_VISUAL_USER_SELECTED` | Поддержка частичного доступа к выбранным фотографиям | Android 14+ | Встроенная галерея показывает только разрешённые системой изображения | При отсутствии доступа используются Photo Picker, Проводник или камера; приложение не расширяет доступ самостоятельно |
| `android.permission.READ_EXTERNAL_STORAGE` с `maxSdkVersion=32` | Чтение изображений через MediaStore на старых версиях Android | Android 7.0–12L в диапазоне поддерживаемых API 24–32 | Просмотр альбомов и множественный выбор во встроенной галерее | Встроенная галерея недоступна; системные источники выбора и камера остаются доступны |
| `com.desperadoboi.imagetopdf.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Signature-защита внутренних non-exported dynamic receivers AndroidX Core | Техническое разрешение присутствует в manifest на всех поддерживаемых версиях; пользователь его не выдаёт | Внутренняя совместимость AndroidX, без отдельного пользовательского сценария | Отказа и runtime-диалога нет; разрешение доступно только приложениям с той же подписью |

`android.hardware.camera` объявлена как необязательная feature (`required=false`), поэтому
устройства без камеры не исключаются из установки и могут использовать импорт изображений.

## Транзитивное разрешение Media3

`androidx.camera:camera-view:1.6.1` транзитивно подключает `camera-video`, а тот —
`androidx.media3:media3-common:1.9.0`, manifest которого объявляет
`android.permission.ACCESS_NETWORK_STATE`. ImageToPDF не использует видео или сетевое
состояние, поэтому это разрешение точечно удаляется через `tools:node="remove"`. CameraX
Preview и ImageCapture при этом сохраняются.

## Подтверждённо отсутствуют

- `android.permission.INTERNET`;
- `android.permission.ACCESS_NETWORK_STATE`;
- `com.google.android.gms.permission.AD_ID`;
- `android.permission.WRITE_EXTERNAL_STORAGE`;
- `android.permission.MANAGE_EXTERNAL_STORAGE`;
- `android.permission.QUERY_ALL_PACKAGES`;
- `android.permission.READ_CONTACTS`;
- `android.permission.RECORD_AUDIO`;
- location permissions;
- notification permissions;
- advertising permissions.

Сохранение PDF выполняется через Storage Access Framework в выбранный пользователем `Uri`,
поэтому широкие storage permissions не требуются.

Локальный document viewer также не добавляет permissions. Внешний `ACTION_VIEW` получает
только временный read grant к переданному пользователем `content://`; внутренний вход
использует `ACTION_OPEN_DOCUMENT` и пытается сохранить read permission только для явно
выбранного файла. Временные seekable copies находятся в приватном app cache и выдаются при
Share через существующий non-exported FileProvider с точечным read grant.

Поддержка DOCX использует ту же временную копию и platform `java.util.zip`/`XmlPullParser`.
Новые permissions и production dependencies не добавлены. External relationships документа
не загружаются; переход по сохранённой `https`-ссылке возможен только после явного нажатия и
через внешнее приложение. Собственный `INTERNET` permission viewer не запрашивает.
