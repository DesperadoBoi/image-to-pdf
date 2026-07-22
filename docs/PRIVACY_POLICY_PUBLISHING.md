# Публикация privacy policy

Этот документ описывает ручной publishing handoff для русской и английской политик с
подтверждёнными публичными данными. В `docs` подготовлены корневая страница, отдельные RU/EN
маршруты и `.nojekyll`, но GitHub Pages нужно включить после merge. До этого оба публичных
URL считаются неподтверждёнными и требуют ручной проверки извне.

## Публичные данные

| Данные | Значение | Проверка перед публикацией |
|---|---|---|
| Дата вступления в силу | `22 июля 2026 года` / `July 22, 2026` | Сохранить один календарный день в RU и EN версиях |
| Разработчик | `DesperadoBoi` | Сверить с developer entity в карточке Google Play |
| Privacy contact | `mihaelkruspe@gmail.com` | Подтвердить, что адрес контролируется и регулярно проверяется |
| Публичный адрес RU | `https://desperadoboi.github.io/image-to-pdf/privacy/` | После deploy проверить HTTPS без авторизации и редиректа |
| Публичный адрес EN | `https://desperadoboi.github.io/image-to-pdf/privacy/en/` | После deploy проверить отдельную английскую страницу и ссылку обратно на RU |

Эти значения должны оставаться одинаковыми по смыслу в
[`PRIVACY_POLICY_RU.md`](PRIVACY_POLICY_RU.md),
[`PRIVACY_POLICY_EN.md`](PRIVACY_POLICY_EN.md), локальном экране приложения, статической
странице и карточке Google Play.

## Как разместить статическую страницу

1. Влить `docs/index.html`, `docs/privacy/index.html`,
   `docs/privacy/en/index.html` и `docs/.nojekyll` в default branch репозитория
   `DesperadoBoi/image-to-pdf`.
2. В GitHub открыть `Settings → Pages`, выбрать `Deploy from a branch`, ветку `main` и каталог
   `/docs`.
3. Дождаться успешного Pages deployment. Структура файлов должна дать адреса:
   `https://desperadoboi.github.io/image-to-pdf/`,
   `https://desperadoboi.github.io/image-to-pdf/privacy/` и
   `https://desperadoboi.github.io/image-to-pdf/privacy/en/`.
4. Открыть все три URL в private/incognito window с мобильного и desktop browser, затем
   проверить внешней сетью, что страницы доступны без session cookie, JavaScript, login,
   CAPTCHA или скачивания файла.
5. Проверить относительные ссылки root → RU/EN, RU → EN и EN → RU, email-ссылки, canonical
   URL, узкий экран и отсутствие 404.
6. Использовать соответствующий RU или EN canonical адрес в локализованных карточках Google
   Play и сохранить дату первого успешного deployment для release records.

## Какой URL нужен Google Play

Нужен стабильный публичный HTTPS URL на полноценную privacy policy для ImageToPDF. Он не
должен вести на PDF-файл, страницу входа, editable document, общую главную страницу без
политики или временную среду. На странице должны быть название приложения, developer entity,
privacy contact, описание доступа/обработки/передачи данных, retention и deletion.

Публичный доступ без авторизации нужен, чтобы пользователь мог прочитать правила до и после
установки, а Google и автоматические проверки могли открыть тот же текст без аккаунта,
cookie или специального разрешения.

## Повторная проверка перед публикацией

- developer name и contact email заполнены реальными значениями и совпадают в RU, EN и
  карточке приложения;
- дата вступления в силу актуальна и одинакова по смыслу в обеих версиях;
- точный подписанный AAB по-прежнему не содержит `INTERNET`, ads/analytics SDK и неизвестных
  data-collection libraries;
- merged release manifest, dependency graph и Data Safety draft повторно проверены;
- camera/media permissions, локальная MediaStore-галерея, Photo Picker, Files, CameraX,
  временные captures, Storage Access Framework и системный Share/Open описаны точно;
- правила удаления app-owned captures и пользовательских PDF соответствуют текущему коду;
- нет аккаунтов, backend, cloud storage, рекламы и аналитики;
- URL доступен публично, не геоблокирован, не редактируем посетителями и не является PDF;
- политика отображается без ошибок на узком экране, а языковые ссылки не ведут на 404;
- store listing, Data Safety и политика не противоречат друг другу.

В приложении политика реализована локально по пути `Home → О приложении → Политика
конфиденциальности`; отдельное вторичное действие открывает RU или EN URL по текущей locale
через системный браузер. Локальный текст не зависит от сети.

Английская локализация этой ветки намеренно ограничена новыми About/Privacy ресурсами.
Прежний интерфейс продолжает использовать русские fallback-строки; полный перевод всего
приложения не входит в эту ветку.

Официальные ориентиры:

- [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Google Play Data Safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469)
