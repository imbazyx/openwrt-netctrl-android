# Правила работы с репозиторием

## Публикуем только release

В репозиторий и в GitHub Releases кладётся **только подписанный release-APK**.
Debug-сборки наружу не выкладываются никогда.

- `app-debug.apk` не коммитится, не заливается в Releases, не выкладывается наружу.
- `netctrl-1.1.0-debug.apk`, который лежал в `apk/`, удалён из истории этой ветки
  как исключение — повторение не предполагается.
- Коммит делается после того, как сборка прошла: `assembleDebug` для проверки
  компиляции и `assembleRelease` для публикации.
- Релиз публикуется только после проверки подписи:
  `apksigner verify --verbose app-release.apk` → `Verified using v2 scheme: true`.

## Собирать в WSL

С Windows `dl.google.com` отдаёт 404 на androidx-артефакты — сборка невозможна.
В WSL те же URL отдают 200. Рабочий путь:

```bash
export ANDROID_HOME=~/Android/Sdk
cp -r /mnt/d/project/openwrt-netctrl-android ~/netctrl-android
cd ~/netctrl-android
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug --no-daemon --console=plain --refresh-dependencies \
  -Dorg.gradle.internal.http.socketTimeout=300000
```

Сеть до Google Maven рвётся. Gradle закеширует первую неудачу как
«артефакт не найден» и дальше повторяет её — без `--refresh-dependencies`
сборка не пройдёт. `--max-workers=1` снижает число одновременных загрузок.

Репозиторий копируется в файловую систему WSL, а не собирается на `/mnt/d`.

## Подпись

Ключ самоподписанный, лежит только в WSL (`~/netctrl-release.jks`) и в
репозиторий не попадает. Обновить приложение поверх сборки с другим ключом
нельзя — для Play Store нужен свой ключ, и его потеря означает потерю
возможности обновлять приложение.

## Коммиты

Сообщения на русском, описательные: что было сломано и почему, а не «fix».
Автор — `imbazyx <damianov@mail.ru>`. Remote — SSH
(`git@github.com:imbazyx/openwrt-netctrl-android.git`).