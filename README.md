# ⚡ OpenWRT NetCtrl — Android

Нативная обёртка над веб-панелью [openwrt-netctrl](https://github.com/imbazyx/openwrt-netctrl).

Это **не второй сервер**. Приложение не опрашивает роутеры самостоятельно:
оно логинится в панель, получает токен и открывает её UI в WebView.
Всё остальное — карта, графики, SSH-терминал, LuCI, настройки — рисует сама панель.

---

## Зачем так

Панель уже умеет всё, что нужно, и обновляется без публикации нового APK.
Дублировать её нативный UI — значит поддерживать две реализации одного и того же.
Нативным оставлен только вход: он и не требует JavaScript, и даёт нормальное
сохранение токена.

---

## 📱 Скриншоты

<table>
  <tr>
    <td align="center"><b>Логин</b></td>
    <td align="center"><b>Панель</b></td>
    <td align="center"><b>Панель</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/01_login.jpg" width="220"/></td>
    <td><img src="screenshots/03_dashboard.jpg" width="220"/></td>
    <td><img src="screenshots/07_map_sidebar.jpg" width="220"/></td>
  </tr>
</table>

---

## 🏗️ Как это работает

```
[Android APK] ──POST /api/auth/login {password}──→  [панель NetCtrl :3000]
     │                                                  │
     │  ←── {token} ──────────────────────────────────┘
     │
     │  WebView грузит UI панели; токен кладётся в sessionStorage
     │  и оттуда панель сама ставит заголовок x-auth-token
     ▼
  WebView: карта, графики, SSH-терминал, LuCI, настройки
```

### Контракт

Приложение знает ровно два вызова:

| Вызов | Где |
|---|---|
| `POST /api/auth/login` с `{password}` | `ServerApi.kt` |
| всё остальное (`/api/routers`, `/api/stats`, …) | делает JS панели |

Токен едет в заголовке `x-auth-token`. Логина и пароля отдельно нет —
в панели один администратор.

### Куда попадает токен

- в `sessionStorage` origin'а **панели** — оттуда панель берёт его для
  своих запросов и для WebSocket SSH;
- **никуда больше**. Страницы `/proxy/:id` и `/luci-login/:id` — это чужой код
  роутера на origin панели, туда токен не подставляется;
- в `DataStore` приложения, чтобы не вводить пароль каждый раз.

Если пароль сменить прямо в панели, приложение подхватывает новый токен
при следующей загрузке страницы.

---

## 🔐 Про сборку

Подпись берётся из `keystore.properties` в корне репозитория:

```properties
storeFile=netctrl-release.jks
storePassword=...
keyAlias=netctrl
keyPassword=...
```

Если файла нет или он не найден — релиз просто собирается **без подписи**,
сборка не падает. Путь к keystore больше не зашит в `build.gradle.kts`.

`keystore.properties` и `*.jks` в `.gitignore`.

---

## 🔧 Сборка

```bash
# отладочная — APK подписан debug-ключом, ставится сразу
./gradlew :app:assembleDebug

# релизная (подпишется, если есть keystore.properties)
./gradlew :app:assembleRelease
```

Требуется JDK 17 и Android SDK 34. Путь к SDK — в `local.properties`
(`sdk.dir=...`, прямые слэши, не обратные).

Имя файла зависит от подписи:

| Сборка | Файл |
|---|---|
| debug | `app/build/outputs/apk/debug/app-debug.apk` |
| release с keystore | `app/build/outputs/apk/release/app-release.apk` |
| release без keystore | `app/build/outputs/apk/release/app-release-unsigned.apk` |

Без подписи APK ставится на телефон только через `adb install`, из магазина и
обновлением поверх ранее установленного — нет. Подписать можно отдельно:

```bash
$ANDROID_HOME/build-tools/34.0.0/zipalign -f 4 in.apk aligned.apk
$ANDROID_HOME/build-tools/34.0.0/apksigner sign \
  --ks netctrl-release.jks --out app-release.apk aligned.apk
```

На Windows wrapper — `gradlew.bat`, он теперь есть в репозитории.

---

## 📐 Стек

| Компонент | Технология |
|---|---|
| UI | Kotlin, Jetpack Compose (Material 3) |
| Состояние | `ViewModel` + `StateFlow` |
| Хранилище | DataStore Preferences |
| Сеть | OkHttp |
| Панель | WebView |

Зависимости минимальные: ни Retrofit, ни GSON, ни навигации — всё, что нужно
для одного запроса логина и одного WebView.

---

## 📦 Готовая сборка

[v1.1.0](https://github.com/imbazyx/openwrt-netctrl-android/releases/tag/v1.1.0)
— [`netctrl-1.1.0.apk`](https://github.com/imbazyx/openwrt-netctrl-android/releases/download/v1.1.0/netctrl-1.1.0.apk)

Собрана и проверена: `assembleDebug` и `assembleRelease` проходят, подпись
APK Signature Scheme v2/v3 валидна. Ключ подписи — самоподписанный, лежит
только локально и в репозиторий не попадает, так что обновить приложение
поверх чужой подписи не получится.

---

## 📌 Связанные проекты

- [openwrt-netctrl](https://github.com/imbazyx/openwrt-netctrl) — сервер и веб-панель.
  Без запущенной панели приложение бесполезно.

---

## 👤 Автор

**imbazyx** — [GitHub](https://github.com/imbazyx)

---

*OpenWRT NetCtrl — управление роутерами из одного окна*