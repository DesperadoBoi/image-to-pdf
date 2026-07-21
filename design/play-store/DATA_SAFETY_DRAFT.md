# Data Safety draft

Статус: предварительный технический аудит для `1 / 1.0.0`. Перед заполнением Google Play
Data safety ответы нужно повторно сверить с точным подписанным AAB, merged manifest и
актуальным release dependency graph. Этот файл не является отправленной декларацией.

## Предварительные ответы

| Вопрос | Предварительный ответ | Основание и оговорка |
|---|---|---|
| Собираются ли данные | По проверенному коду и release dependencies не обнаружена передача пользовательских данных разработчику или за пределы устройства. Изображения, их metadata и PDF обрабатываются локально | Локальная обработка сама по себе не отмечается как сбор вне устройства; вывод необходимо подтвердить для каждого будущего AAB |
| Передаются ли данные третьим лицам | Автоматической передачи нет | Пользователь может явно вызвать системный Share/Open и самостоятельно выбрать стороннее приложение; это пользовательское действие, а не фоновая передача разработчиком |
| Используются ли аккаунты | Нет | Регистрация, вход и backend отсутствуют |
| Есть ли удаление аккаунта | Не применимо | Пользовательские аккаунты не создаются |
| Есть ли реклама | Нет | Рекламные SDK, рекламные permissions и рекламный UI отсутствуют |
| Шифруются ли данные при передаче | Не применимо к текущей реализации | Приложение не выполняет сетевую передачу и не имеет `INTERNET` permission; нельзя отмечать шифрование передачи как реализованную сетевую функцию |
| Может ли пользователь запросить удаление данных | Удалённого хранилища разработчика нет, поэтому удалять серверную копию или аккаунт не требуется | Локальные app data удаляются через настройки Android/удаление приложения; сохранённые PDF пользователь удаляет в выбранном хранилище |
| Какие файлы обрабатываются | Выбранные пользователем изображения, снимки камеры и создаваемые PDF | Источники доступны через MediaStore, Photo Picker, Storage Access Framework, camera intent и CameraX; обработка локальная |

## Аудит release dependencies

Граф проверен командой `:app:dependencies --configuration releaseRuntimeClasspath`.
Test-only зависимости JUnit, Espresso и AndroidX Test в release runtime не входят.

| Dependency | Network capability | Analytics / ads | Data collection risk | Conclusion |
|---|---|---|---|---|
| `androidx.activity:activity-ktx:1.13.0` и Kotlin/coroutines transitive runtime | Сетевого клиента нет | Нет | Низкий; UI/lifecycle runtime | Не собирает данные сам по себе |
| `androidx.appcompat:appcompat:1.7.1` | Сетевого клиента нет | Нет | Низкий; UI compatibility | Не собирает данные сам по себе |
| `com.google.android.material:material:1.14.0` | Сетевого клиента нет | Нет | Низкий; XML UI components | Не собирает данные сам по себе |
| `androidx.constraintlayout:constraintlayout:2.2.1` | Нет | Нет | Низкий; layout runtime | Риска сбора не обнаружено |
| `androidx.recyclerview:recyclerview:1.4.0` | Нет | Нет | Низкий; list UI | Риска сбора не обнаружено |
| `androidx.exifinterface:exifinterface:1.4.2` (resolved) | Нет | Нет | Читает локальную EXIF metadata выбранного изображения | Используется локально для ориентации, данные не передаёт |
| `androidx.camera:camera-core/camera2/lifecycle/view:1.6.1` | CameraX не требует интернет для Preview/ImageCapture | Нет | Имеет доступ к камере только после runtime permission; снимки app-owned | Используется для локального сканирования; серверной передачи нет |
| `androidx.camera:camera-video:1.6.1` transitive | Прямо приложением не используется | Нет | Подключена через `camera-view`; может увеличить поверхность кода | Не используется пользовательским flow; не является SDK сбора данных |
| `androidx.media3:media3-common/container/muxer:1.9.0` transitive | `media3-common` объявляет `ACCESS_NETWORK_STATE`, но не добавляет `INTERNET`; permission удалён manifest merger | Нет | Неожиданное manifest-разрешение без используемого video/network flow | `ACCESS_NETWORK_STATE` удалён; повторно проверять после обновления CameraX |
| `androidx.core:core:1.18.0`, Lifecycle, Startup, ProfileInstaller | Сетевого клиента нет | Нет | Startup components и внутренний signature permission, без document access | Риска сбора не обнаружено |
| Dagger, Guava, Kotlin stdlib/coroutines и другие CameraX transitives | Сами по себе не отправляют данные | Нет | Общие runtime utilities; Dagger используется внутренне CameraX | Риска сбора не обнаружено, но граф нужно повторять после обновлений |
| Platform `PdfDocument`, `PdfRenderer`, `ContentResolver`, `FileProvider` | Сетевого клиента в приложении не добавляют | Нет | Работают с выбранными `Uri` и локальными/app-owned файлами | Локальная генерация, preview и выдача временных read grants |

## Manifest conclusion

В merged release manifest нет `INTERNET`, `ACCESS_NETWORK_STATE`, `AD_ID`, advertising,
location, contacts, audio, notifications или broad storage permissions. Остаются только
camera/media permissions, legacy read access до API 32 и внутренний signature permission
AndroidX Core. Полный список и поведение при отказе зафиксированы в
[`docs/RELEASE_PERMISSION_AUDIT.md`](../../docs/RELEASE_PERMISSION_AUDIT.md).
