# Data Safety draft

Статус: пошаговый технический черновик для `applicationId =
com.desperadoboi.imagetopdf`, `versionCode = 1`, `versionName = 1.0.0`. Он не отправлен в
Google Play и не заменяет ручную проверку точного подписанного AAB.

## Граница между доступом и сбором

В терминах Google Play «сбор» означает передачу пользовательских данных за пределы
устройства. Доступ к данным и их обработка только на устройстве не декларируются как сбор,
если данные не передаются наружу. Поэтому наличие CAMERA/media permission необходимо
объяснить, но оно само по себе не означает `Data collected`.

ImageToPDF локально читает выбранные изображения, CameraX captures и создаваемые документы.
По проверенному коду приложение не передаёт эти данные разработчику или SDK. `Share` и
`Open` запускаются только явным действием пользователя через Android intents. Такой transfer
нужно отличать от автоматической передачи; текущая Google guidance допускает исключение для
конкретного user-initiated action, при котором пользователь ожидает отправку.

Официальный источник терминов:
[Provide information for Google Play's Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469).

## Предлагаемый итог

| Раздел | Предлагаемый выбор |
|---|---|
| Data collection | `No`: данные не передаются за пределы устройства приложением или его SDK |
| Data sharing | `No`: автоматической передачи третьим лицам нет; Share/Open — ожидаемое действие пользователя |
| Accounts | `No`: регистрация, вход и backend отсутствуют |
| Advertising | `No`: рекламы и рекламных SDK нет |
| Analytics | `No`: analytics/crash reporting SDK отсутствуют |
| Photos and videos | Не выбирать как collected/shared; фотографии доступны и обрабатываются локально |
| Files and documents | Не выбирать как collected/shared; файлы читаются/создаются локально по выбранным Uri |
| Camera | Permission используется для локальной съёмки; не является отдельным доказательством collection |

Если финальный AAB, SDK graph или поведение изменятся, этот итог пересматривается до
заполнения формы.

## Пошаговое заполнение

### 1. Data collection

- **Предлагаемый выбор:** `No`, приложение не собирает и не передаёт требуемые типы данных
  за пределы устройства.
- **Техническое основание:** генерация, preview, crop и perspective выполняются локально;
  backend и network client отсутствуют. В проверенном merged release manifest отсутствуют
  `INTERNET` и `ACCESS_NETWORK_STATE`.
- **Подтверждение:** `app/src/main/AndroidManifest.xml` удаляет транзитивный
  `ACCESS_NETWORK_STATE`; [`docs/RELEASE_PERMISSION_AUDIT.md`](../../docs/RELEASE_PERMISSION_AUDIT.md)
  фиксирует merged permissions; `app/build.gradle.kts` не содержит SDK аналитики, рекламы,
  cloud storage или network client.
- **Проверить вручную:** извлечь manifest из точного подписанного AAB, повторить
  `releaseRuntimeClasspath`, сверить все SDK disclosures и убедиться, что ни одна active
  release version в Google Play не имеет иного поведения. При наличии передачи выбрать
  `Yes` и заполнить каждый data type, purpose, optional/required и retention.

### 2. Data sharing

- **Предлагаемый выбор:** `No`.
- **Техническое основание:** приложение не передаёт данные третьим лицам автоматически.
  `PdfResultFragment` открывает системные Share/Open только после нажатия пользователя, а
  получателя выбирает сам пользователь.
- **Подтверждение:** `app/src/main/java/com/desperadoboi/imagetopdf/ui/result/PdfIntentFactory.java`
  создаёт `Intent.ACTION_SEND` и `Intent.ACTION_VIEW`; manifest не содержит Internet
  permission; result Uri передаётся только с временным read grant. FileProvider для
  app-owned camera captures не экспортирован.
- **Проверить вручную:** подтвердить в текущей версии формы применимость user-initiated
  sharing exception, проверить отсутствие auto-upload/background export и убедиться, что
  chooser явно показывает пользователю выбор получателя. Если появится автоматическая
  отправка другому приложению или сервису, ответ пересмотреть.

### 3. Security practices

- **Предлагаемый выбор:** не заявлять `Data encrypted in transit` как преимущество: у
  приложения нет декларируемой сетевой передачи, поэтому вопрос не применяется и может не
  показываться после ответа `No` на collection/sharing.
- **Независимая security review:** `No`; подтверждённой независимой проверки нет.
- **Техническое основание:** app-owned captures хранятся в приватном каталоге, Camera
  FileProvider имеет `exported=false`, доступ выдаётся временно; captures исключены из cloud
  backup и device transfer.
- **Подтверждение:** `app/src/main/AndroidManifest.xml`,
  `app/src/main/res/xml/captured_image_paths.xml`, `app/src/main/res/xml/backup_rules.xml`,
  `app/src/main/res/xml/data_extraction_rules.xml`,
  `app/src/main/java/com/desperadoboi/imagetopdf/image/CapturedImageStorage.java`.
- **Проверить вручную:** формулировки актуальной формы, целевой возраст/Families declaration,
  точные backup rules в AAB и отсутствие новой сетевой библиотеки. Не выбирать encryption
  или independent review без фактического основания.

### 4. Account creation

- **Предлагаемый выбор:** `No, app does not allow users to create an account`.
- **Техническое основание:** нет экранов регистрации/входа, identity SDK, backend или
  account model; основная функция доступна сразу после запуска.
- **Подтверждение:** [`PRODUCT.md`](../../PRODUCT.md) исключает accounts и registration;
  `app/build.gradle.kts` не содержит auth SDK; `ToolCatalog` ведёт прямо в локальные flows.
- **Проверить вручную:** запустить чистую установку точного release build и убедиться, что ни
  один сценарий не требует входа через системный provider или внешний сервис.

### 5. Data deletion

- **Предлагаемый выбор:** account deletion — `Not applicable`, поскольку аккаунтов нет.
  Отдельный remote data deletion request не заявлять: разработчик не хранит пользовательские
  данные на сервере. Если поле не показывается после `No collection`, ничего дополнительно
  не декларировать.
- **Техническое основание:** app-owned camera captures удаляются при отмене, пересъёмке,
  удалении страницы и очистке сессии; локальные app data можно удалить через Android settings
  или uninstall. PDF в выбранном внешнем месте контролирует и удаляет пользователь.
- **Подтверждение:** [`docs/SMART_SCAN.md`](../../docs/SMART_SCAN.md),
  `CapturedImageStorage.java`, `CapturedPageCleanup.java`, `ScanSessionViewModel.java` и
  обе privacy policies.
- **Проверить вручную:** пройти cancel/retake/delete/new document/uninstall scenarios и
  проверить отсутствие orphan captures. Убедиться, что wording формы не требует отдельного
  URL для приложений без account creation.

### 6. Photos and videos

- **Предлагаемый выбор:** не отмечать `Photos` или `Videos` как collected/shared. Videos
  приложением не используются; photos только доступны и обрабатываются локально.
- **Техническое основание:** встроенная галерея читает metadata/content через MediaStore;
  Photo Picker и системные источники возвращают выбранные `content://` Uri. Доступ не
  сопровождается передачей разработчику.
- **Подтверждение:** permissions `READ_MEDIA_IMAGES`,
  `READ_MEDIA_VISUAL_USER_SELECTED`, `READ_EXTERNAL_STORAGE` с `maxSdkVersion=32` в
  `app/src/main/AndroidManifest.xml`; `MediaGalleryRepository.java`,
  `ImagePickerFragment.java`, `ImagePickerLauncher.java`.
- **Проверить вручную:** точный merged manifest на API 24–36, Android 14 partial access,
  отказ в permission и Photo Picker fallback. Повторно проверить, что ни одна библиотека не
  получает image bytes или EXIF metadata за пределами устройства.

### 7. Files and documents

- **Предлагаемый выбор:** не отмечать `Files and docs` как collected/shared.
- **Техническое основание:** Files/Photo Picker дают приложению выбранный Uri; PDF создаётся
  последовательно и пишется в Uri, выбранный пользователем через Storage Access Framework.
  Локальный preview читает результат через `PdfRenderer`.
- **Подтверждение:** `EditorFragment.java` запускает `CreateDocument`;
  `PdfGenerator.java`, `PdfPreviewLoader.java`, `PdfResultMetadataReader.java`; broad storage
  permissions в merged manifest отсутствуют.
- **Проверить вручную:** сохранение в локальный и сторонний document provider. Если выбранный
  пользователем cloud provider сам загружает файл, зафиксировать, что это явный системный
  выбор пользователя и обработка регулируется provider; приложение не должно выполнять
  собственный upload.

### 8. Camera

- **Предлагаемый выбор:** указать использование camera permission в permission/privacy
  disclosures, но не считать его Data Safety collection. Если интерфейс формы отдельно
  спрашивает доступ к camera, ответить, что доступ нужен для app functionality и optional:
  импорт изображений остаётся доступен без камеры.
- **Техническое основание:** CameraX Preview/ImageCapture используется только в Smart Scan;
  обычная камера вызывается системным `TakePicture`. Captures остаются в app-specific
  `filesDir/captured_images` и не добавляются в MediaStore.
- **Подтверждение:** permission `android.permission.CAMERA` и optional feature
  `android.hardware.camera required=false` в manifest; `SmartScanFragment.java`,
  `CapturedImageStorage.java`, [`docs/SMART_SCAN.md`](../../docs/SMART_SCAN.md).
- **Проверить вручную:** granted/denied/permanently denied flows, отсутствие video/audio
  capture, cleanup временных снимков и отсутствие network transmission.

### 9. Advertising

- **Предлагаемый выбор:** `No, the app does not contain ads`.
- **Техническое основание:** рекламный UI и ads SDK отсутствуют; `AD_ID` permission не
  присутствует; приложение не создаёт advertising identifiers.
- **Подтверждение:** `app/build.gradle.kts`, merged permission audit в
  [`docs/RELEASE_PERMISSION_AUDIT.md`](../../docs/RELEASE_PERMISSION_AUDIT.md), отсутствие
  рекламных dependencies и permissions.
- **Проверить вручную:** exact AAB dependency graph, merged manifest и все dynamic-feature
  modules, если они появятся. Ответ изменить при любой будущей монетизации рекламой.

### 10. Analytics

- **Предлагаемый выбор:** данные для analytics не собираются; analytics/crash reporting —
  `No`.
- **Техническое основание:** Firebase, Google Analytics, Crashlytics и иные telemetry SDK
  отсутствуют; app interaction, crash logs, diagnostics и device IDs не отправляются.
- **Подтверждение:** `app/build.gradle.kts`, release dependency audit ниже и отсутствие
  `INTERNET` permission.
- **Проверить вручную:** точный dependency graph, startup initializers и SDK disclosures.
  Если появится telemetry, выбрать `Yes` для соответствующих data types и purposes, даже
  если SDK отправляет данные напрямую третьей стороне.

## Карта локального доступа

| Сценарий | Данные на устройстве | Передача приложением |
|---|---|---|
| Встроенная галерея | MediaStore metadata, thumbnails и выбранные image bytes | Нет |
| Photo Picker / Files | Только выбранные пользователем `content://` Uri | Нет |
| Обычная камера / Smart Scan | App-owned JPEG captures | Нет |
| Редактор и PDF | Rotation, crop, perspective metadata, bitmap и создаваемый PDF | Нет |
| Save | Запись PDF в выбранный пользователем document provider | Только явное действие пользователя; собственного upload нет |
| Open / Share | Временный read grant выбранному приложению | Только явное действие пользователя |
| Document viewer | Выбранный или переданный системой Uri и bounded временная копия в app cache | Нет; Share только по явному действию пользователя |

Read-only viewer принимает user-provided `content://` через системный Open with или
`ACTION_OPEN_DOCUMENT`. Тип проверяется локально, parser/render выполняется на устройстве,
а seekable temporary copy остаётся в приватном cache и удаляется lifecycle/age cleanup.
Приложение не отправляет просмотренный файл разработчику; FileProvider read grant выдаётся
другому приложению только после нажатия Share.

## Release dependency audit

Проверенный `releaseRuntimeClasspath` содержит AndroidX AppCompat/Activity/Core/Lifecycle,
Material Components, ConstraintLayout, RecyclerView, ExifInterface, CameraX и их runtime
transitives. Ads, analytics, crash reporting, auth, cloud storage и network client SDK не
обнаружены. Test-only JUnit, Espresso и AndroidX Test в release runtime не входят.

CameraX `camera-view` транзитивно подключает `camera-video` и Media3. ImageToPDF не использует
video/network flow; объявляемый Media3 `ACCESS_NETWORK_STATE` точечно удалён manifest merger.
Это необходимо повторно проверять после каждого обновления CameraX или Media3.

## Финальный ручной gate

1. Зафиксировать hash точного подписанного AAB и извлечь его merged manifest.
2. Проверить permissions и отсутствие `INTERNET`, `ACCESS_NETWORK_STATE`, `AD_ID`.
3. Повторить release dependency graph и изучить data disclosures всех SDK.
4. Пройти gallery, camera, scan, export, Open и Share на чистом устройстве.
5. Сверить ответы с RU/EN privacy policy и фактическими active artifacts.
6. Заполнить форму вручную и сохранить preview ответов для review владельцем.
7. Не отправлять декларацию, пока privacy URL и developer contact не заполнены реальными
   значениями.
