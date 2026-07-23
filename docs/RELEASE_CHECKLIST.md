# Release checklist

Чек-лист относится к первой технической release candidate `1 / 1.0.0` с
`applicationId = com.desperadoboi.imagetopdf`. Он не разрешает публикацию и не заменяет
ручное тестирование на реальных устройствах.

## Signing setup

Upload keystore и пароли хранятся только вне репозитория. Gradle читает следующие значения
из user-level Gradle properties (`%USERPROFILE%\.gradle\gradle.properties`) или одноимённых
environment variables:

- `IMAGETOPDF_STORE_FILE` — путь к keystore вне репозитория;
- `IMAGETOPDF_STORE_PASSWORD`;
- `IMAGETOPDF_KEY_ALIAS`;
- `IMAGETOPDF_KEY_PASSWORD`.

Значения нельзя добавлять в project `gradle.properties`, `local.properties`, CI-файлы или
документацию. `checkReleaseSigningConfiguration` проверяет полноту, существование файла и
его расположение вне репозитория, не печатая секреты. Без credentials debug/test/lint и
unsigned `assembleRelease` остаются доступны; production `bundleRelease` останавливается с
понятной ошибкой.

После настройки credentials `verifyReleaseBundle` создаёт и проверяет непустой release AAB,
`applicationId`, `versionCode`, `versionName` и `debuggable=false`. Ожидаемый артефакт:
`app/build/outputs/bundle/release/app-release.aab`.

## Build

- [x] `./gradlew clean --no-daemon --no-configuration-cache`
- [x] `./gradlew assembleDebug --no-daemon --no-configuration-cache`
- [x] `./gradlew assembleRelease --no-daemon --no-configuration-cache`
- [ ] `./gradlew bundleRelease --no-daemon --no-configuration-cache` с внешним upload key
- [x] `./gradlew test --no-daemon --no-configuration-cache`
- [x] `./gradlew lint --no-daemon --no-configuration-cache`
- [x] Проверен merged release manifest и provenance каждого permission
- [x] Безопасный отказ `checkReleaseSigningConfiguration` без credentials подтверждён; перед
  AAB задача должна завершиться успешно
- [ ] `./gradlew verifyReleaseBundle`
- [ ] Подпись AAB проверена официальным `jarsigner -verify -verbose -certs`
- [ ] AAB существует, имеет ненулевой размер и не добавлен в Git
- [x] Release variant: правильные applicationId/version, `debuggable=false`, R8 и resource shrinking включены
- [x] Проверены R8 mapping/configuration и отсутствие blanket keep rules
- [x] `git diff --check`, conflict markers, secrets и keystore audit пройдены

Автоматические проверки выше повторены 22 июля 2026 года. Повторить их после любого
изменения release-конфигурации или production-кода.

## Известные технические замечания

- Android build tools упаковывают транзитивные CameraX libraries
  `libimage_processing_util_jni.so` и `libsurface_util_jni.so` без дополнительного stripping;
  это предупреждение не остановило optimized release-сборку.
- Компилятор сообщает об использовании deprecated Android API в `PdfSuccessBanner`; lint
  проходит, но предупреждение следует проверить перед будущим обновлением AndroidX.
- Основные и stress-сценарии ниже требуют ручного тестирования на реальных phone/tablet
  устройствах; автоматическая сборка не подтверждает визуальное и lifecycle-поведение.
- Английская локализация в этой ветке охватывает только новые About/Privacy экраны; прежний
  интерфейс намеренно использует русские fallback-строки.

## Основные сценарии

- [ ] Запуск приложения
- [ ] Android 12+ splash
- [ ] Adaptive icon
- [ ] Android 13+ themed icon в светлой и тёмной теме launcher
- [ ] Image to PDF
- [ ] Встроенная галерея
- [ ] Photo Picker fallback
- [ ] Проводник / Storage Access Framework
- [ ] Системная камера
- [ ] CameraX Smart Scan и отказ в CAMERA permission
- [ ] Ручная перспектива и crop
- [ ] Изменение порядка страниц
- [ ] Поворот и удаление страниц
- [ ] Отмена выбора места и отмена export
- [ ] Успешный export
- [ ] Result preview и metadata
- [ ] Share
- [ ] Open и отсутствие PDF viewer
- [ ] Внешнее Open with для PDF, XLSX, TXT, CSV/TSV и поддерживаемых изображений
- [ ] Внутренний «Просмотр документов» через системный ACTION_OPEN_DOCUMENT
- [ ] Viewer: permission loss, rotation, onNewIntent, Share и cache cleanup
- [ ] Viewer: empty/corrupted/encrypted/large PDF и page zoom/swipe
- [ ] Viewer: multiline quoted CSV, UTF-8 BOM, legacy TXT и EXIF image
- [ ] Viewer: XLSX sheets, dates, cached formulas, merged cells и partial preview limits
- [ ] Viewer не предлагается для XLS, XLSM/XLSB, DOC/DOCX, archives, octet-stream и wildcard
- [ ] Последовательное создание нескольких PDF
- [ ] Process recreation на gallery/editor/scan/result flow
- [ ] Светлая и тёмная тема
- [ ] Edge-to-edge, status/navigation bars и display cutout
- [ ] Системный Back и predictive back на поддерживаемом устройстве
- [ ] Home → «О приложении» → локальная политика, включая Back на каждом уровне
- [ ] Версия на экране «О приложении» совпадает с `versionName` собранного APK/AAB
- [ ] Email разработчику открывается через `mailto:`; понятен fallback без email-приложения
- [ ] Публичная политика открывается в браузере; локальная остаётся доступной без браузера/сети
- [ ] Новые экраны проверены на русском и английском языке
- [ ] About/Privacy проверены при fontScale `1.0`, `1.3`, `1.5` на ширинах `320dp` и `360dp`

## Stress

- [ ] 1 изображение
- [ ] 25 изображений
- [ ] 50 изображений
- [ ] Большие фотографии с EXIF rotation
- [ ] Недостаточно памяти
- [ ] Недостаточно места в выбранном provider
- [ ] Отмена во время генерации
- [ ] Поворот экрана во время выбора, редактирования и генерации
- [ ] Возвращение из background
- [ ] Уничтожение процесса и восстановление сессии

## Google Play handoff

- [ ] Play Store icon 512×512 без прозрачности и встроенного внешнего скругления
- [ ] Feature graphic 1024×500
- [ ] Phone/tablet screenshots основных сценариев
- [ ] Short description
- [ ] Full description
- [ ] Публичный privacy policy URL
- [ ] Data safety перепроверена по точному подписанному AAB
- [ ] Ads declaration: рекламы нет
- [ ] Target audience
- [ ] Content rating
- [ ] App access: аккаунт не требуется
- [ ] Internal testing track и список тестировщиков подготовлены вручную
- [ ] Upload key зарегистрирован вручную и AAB загружается только после финального approval

Публикация, Play Console и создание реального signing key не входят в автоматическую
подготовку этой release candidate.

## Privacy publication handoff

- [x] Подготовлены `docs/index.html`, RU `docs/privacy/index.html`, EN
  `docs/privacy/en/index.html` и `docs/.nojekyll`
- [x] Локальная политика реализована внутри приложения
- [ ] После merge включить GitHub Pages из `main /docs`
- [ ] Вручную проверить root, RU и EN URL извне без авторизации
- [ ] Повторно сверить Data Safety с точным финальным подписанным AAB
