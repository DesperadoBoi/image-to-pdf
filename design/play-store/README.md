# Google Play assets foundation

## Название

- название будущей карточки: `ImageToPDF — фото в PDF`;
- короткий Android launcher label остаётся `ImageToPDF`.

## Обязательные материалы

- Play Store icon: `512×512 PNG`, без прозрачности;
- Google Play самостоятельно применяет скругление, поэтому в исходник не добавляются
  внешний rounded-square, рамка или прозрачные углы;
- знак `PDF` должен оставаться внутри safe zone и сохранять свободное поле;
- feature graphic: `1024×500`;
- phone screenshots для основных пользовательских сценариев;
- short description: до 80 символов;
- full description: до 4000 символов;
- название: до 30 символов.

## Подготовленные документы

- короткие описания: [`SHORT_DESCRIPTION_RU.md`](SHORT_DESCRIPTION_RU.md) и
  [`SHORT_DESCRIPTION_EN.md`](SHORT_DESCRIPTION_EN.md);
- полные описания: [`FULL_DESCRIPTION_RU.md`](FULL_DESCRIPTION_RU.md) и
  [`FULL_DESCRIPTION_EN.md`](FULL_DESCRIPTION_EN.md);
- планы 7 phone screenshots: [`SCREENSHOT_PLAN_RU.md`](SCREENSHOT_PLAN_RU.md) и
  [`SCREENSHOT_PLAN_EN.md`](SCREENSHOT_PLAN_EN.md);
- briefs: [`FEATURE_GRAPHIC_BRIEF.md`](FEATURE_GRAPHIC_BRIEF.md) и
  [`PLAY_STORE_ICON_BRIEF.md`](PLAY_STORE_ICON_BRIEF.md);
- release notes: [`RELEASE_NOTES_RU.md`](RELEASE_NOTES_RU.md) и
  [`RELEASE_NOTES_EN.md`](RELEASE_NOTES_EN.md);
- technical form draft: [`DATA_SAFETY_DRAFT.md`](DATA_SAFETY_DRAFT.md);
- общий handoff: [`ASSET_CHECKLIST.md`](ASSET_CHECKLIST.md).

Финальные screenshots, feature graphic и Play Store icon не создаются программно в этой
ветке. Перед публикацией тексты и графика проходят отдельную продуктовую, локализационную и
privacy-проверку.

Production-графика следует правилам из [`docs/BRANDING.md`](../../docs/BRANDING.md).
Исходный референс сохраняется в
[`design/branding/app-icon-reference.png`](../branding/app-icon-reference.png).

Актуальные требования к размерам и текстовым лимитам сверены с официальной справкой
[Google Play](https://support.google.com/googleplay/android-developer/answer/9859152) и
[preview asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151).
