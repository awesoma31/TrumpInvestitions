# TrumpInvestitions — Android-клиент

Нативное Android-приложение для торговли акциями и криптовалютой.  
Написано на Kotlin, UI — Jetpack Compose + Material3.

---

## Архитектура

```
┌─────────────────────────────────────────────────┐
│                   UI Layer                      │
│  Composable-экраны + NavigationSuiteScaffold    │
└──────────────┬──────────────────────────────────┘
               │ StateFlow / collectAsState
┌──────────────▼──────────────────────────────────┐
│               ViewModel Layer                   │
│  AuthVM · StocksVM · StockDetailVM · PortfolioVM│
└──────────────┬──────────────────────────────────┘
               │ suspend fun / Flow
┌──────────────▼──────────────────────────────────┐
│             Repository Layer                    │
│      AuthRepo · MarketRepo · PortfolioRepo      │
└──────────────┬──────────────────────────────────┘
               │ Retrofit + OkHttp
┌──────────────▼──────────────────────────────────┐
│              Network Layer                      │
│   ApiService · AuthApiService · AuthInterceptor │
│   TokenRefreshAuthenticator · OpenTelemetry     │
└─────────────────────────────────────────────────┘
```

Паттерн: **MVVM + Repository**. DI ручной — синглтоны через `TrumpApp`.

---

## Структура пакетов

```
org.awesoma.trumpinvestitions/
├── TrumpApp.kt                          # Application: сеть, токены, OTel
├── MainActivity.kt                      # Compose entry point
├── navigation/
│   └── Screen.kt                        # sealed class маршрутов
├── ui/
│   ├── screens/
│   │   ├── auth/                        # LoginScreen, RegisterScreen
│   │   ├── market/                      # StocksScreen, StockDetailScreen
│   │   ├── portfolio/                   # PortfolioScreen
│   │   └── profile/                     # ProfileScreen
│   ├── viewmodel/
│   │   ├── AuthViewModel.kt
│   │   ├── StocksViewModel.kt
│   │   ├── StockDetailViewModel.kt
│   │   └── PortfolioViewModel.kt
│   └── theme/
└── data/
    ├── network/
    │   ├── AppNetwork.kt                # OkHttp + Retrofit конфигурация
    │   ├── ApiService.kt                # основные эндпоинты (рынок, ордера)
    │   └── AuthApiService.kt            # /auth/* эндпоинты
    ├── repository/
    │   ├── AuthRepository.kt
    │   ├── MarketRepository.kt
    │   └── PortfolioRepository.kt
    ├── auth/
    │   ├── TokenManager.kt              # DataStore: хранение токенов
    │   ├── AuthInterceptor.kt           # добавляет Bearer в каждый запрос
    │   └── TokenRefreshAuthenticator.kt # авто-рефреш на 401
    ├── model/                           # DTO и доменные модели
    └── settings/
        └── SettingsManager.kt           # хост сервера (SharedPreferences)
```

---

## Инициализация приложения (TrumpApp)

При старте `TrumpApp` лениво создаёт:

```
TrumpApp
├── SettingsManager       — читает/пишет хост сервера из SharedPreferences
├── TokenManager          — DataStore для access/refresh токенов
├── OpenTelemetry         — OTLP HTTP exporter → jaeger:4318
└── AppNetwork            — OkHttp + Retrofit, baseUrl из SettingsManager
```

При смене хоста в настройках вызывается `TrumpApp.rebuildNetwork()` —
пересоздаёт `AppNetwork` с новым URL без перезапуска приложения.

---

## Сеть (AppNetwork)

Два Retrofit-клиента на одном OkHttp:

| Клиент | Interceptors | Для чего |
|---|---|---|
| `authApiService` | Logging, OTel | `/auth/login`, `/auth/register`, `/auth/refresh` |
| `apiService` | Logging, OTel, **AuthInterceptor**, **TokenRefreshAuthenticator** | Все остальные запросы |

**AuthInterceptor** — синхронно читает токен из DataStore и добавляет:
```
Authorization: Bearer <access_token>
```

**TokenRefreshAuthenticator** — перехватывает 401, вызывает `/auth/refresh`,
сохраняет новые токены и повторяет оригинальный запрос. При ошибке рефреша —
очищает токены, приложение возвращается на экран логина.

---

## Подключение к бэкенду

```kotlin
// SettingsManager.kt
var serverHost: String = "10.0.2.2:8080"      // дефолт для Android-эмулятора
val baseUrl: String = "http://$serverHost/api/v1/"
```

| Окружение | Хост |
|---|---|
| Android-эмулятор (localhost) | `10.0.2.2:8080` |
| Реальное устройство (та же сеть) | `192.168.x.x:8080` |
| Linux VM (bridge network) | IP виртуальной машины`:8080` |

Хост меняется в экране настроек — сохраняется в SharedPreferences,
`TrumpApp.rebuildNetwork()` пересоздаёт клиент без перезапуска.

Все запросы идут через `auth-gateway` (порт 8080), который проксирует к
`market-data-service`, `trading-service`, `portfolio-service`.

---

## Навигация

`MainActivity` реализует двухуровневую навигацию:

```
Старт приложения
    │
    ├─ токен есть? ──Да──► MainApp (Bottom Nav)
    │                           ├── Market (StocksScreen)
    │                           ├── Portfolio (PortfolioScreen)
    │                           └── Profile (ProfileScreen)
    │
    └─ Нет ──► AuthFlow
                   ├── LoginScreen
                   └── RegisterScreen
```

`NavigationSuiteScaffold` адаптирует нижнюю / боковую навигацию
под размер экрана (телефон / планшет / fold).

**Маршруты** (`Screen.kt`):
```kotlin
sealed class Screen(val route: String) {
    object Login       : Screen("login")
    object Register    : Screen("register")
    object StockList   : Screen("stocks")
    object StockDetail : Screen("stock/{symbol}")
    object Portfolio   : Screen("portfolio")
    object Profile     : Screen("profile")
}
```

---

## Экраны и ViewModels

### StocksScreen + StocksViewModel
- Список инструментов: AAPL, MSFT, TSLA, BTCUSDT, ETHUSDT
- Котировки обновляются каждые **5 секунд** через `MarketRepository.getStocksFlow()`
- Показывает: последняя цена, изменение за день (%), bid/ask

### StockDetailScreen + StockDetailViewModel
- График свечей с выбором таймфрейма: 1H / 4H / 1D / 1W / 1M
- Таймфреймы маппятся на интервалы API: `1m`, `5m`, `15m`, `1h`, `1d`
- Стакан заявок (order book)
- Форма размещения рыночного ордера (BUY / SELL)

### PortfolioScreen + PortfolioViewModel
- Баланс, открытые позиции, история ордеров
- Обновляется каждые **10 секунд**
- Депозит / вывод средств с валидацией
- Ошибки (напр. `INSUFFICIENT_FUNDS`) переводятся в понятные сообщения

### LoginScreen / RegisterScreen + AuthViewModel
- Логин и регистрация, сохранение токенов через `TokenManager`
- После успеха — навигация в MainApp

---

## Polling (реального времени без WebSocket)

```kotlin
// MarketRepository.kt
fun getStocksFlow(): Flow<List<Stock>> = flow {
    while (true) {
        val quotes = apiService.getQuotes(symbols = SYMBOLS)
        emit(mapToStocks(quotes))
        delay(5_000)
    }
}
```

Аналогично для портфеля (10 секунд). WebSocket не используется —
данные получаются периодическими HTTP-запросами.

---

## Хранение данных

| Данные | Хранилище |
|---|---|
| Access token | DataStore Preferences |
| Refresh token | DataStore Preferences |
| Username | DataStore Preferences |
| Хост сервера | SharedPreferences |
| OTEL endpoint | SharedPreferences |
| Рыночные данные | Только в памяти (нет Room) |

---

## Обработка ошибок

`ApiError.parse(Throwable)` конвертирует ошибки сети/сервера в
русскоязычные строки для отображения в UI. Специфичные коды ответа
(`INSUFFICIENT_FUNDS`, `SYMBOL_NOT_FOUND` и др.) маппятся в понятный текст.

---

## Наблюдаемость (OpenTelemetry)

`TrumpApp` инициализирует OTel SDK с OTLP HTTP exporter.
Все OkHttp-запросы автоматически инструментируются —
трейсы отправляются в Jaeger (порт 4318) для сквозной трассировки
от мобильного клиента до бэкенд-сервисов.

---

## Сборка и запуск

```bash
# Открыть в Android Studio и нажать Run
# или через Gradle:
./gradlew assembleDebug

# На эмуляторе бэкенд доступен по 10.0.2.2:8080 (localhost хост-машины)
# На реальном устройстве — указать IP машины в настройках приложения
```

Требования: Android Studio Iguana+, JDK 11+, Android SDK 36.
