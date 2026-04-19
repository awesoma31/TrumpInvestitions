# Android-приложение

**Язык:** Kotlin  
**UI:** Jetpack Compose + Material3  
**Навигация:** Navigation Compose  
**Min SDK:** 24 (Android 7.0)  
**Target SDK:** 36  

## Структура проекта

```
front-ktl/app/src/main/java/org/awesoma/trumpinvestitions/
│
├── MainActivity.kt                 точка входа, навигация верхнего уровня
│
├── navigation/
│   └── Screen.kt                   маршруты навигации (sealed class)
│
├── data/
│   ├── model/
│   │   └── Models.kt               data-классы: Stock, Order, Position, User, PricePoint
│   └── stub/
│       └── StubRepository.kt       заглушка данных (заменить на Retrofit/WS)
│
└── ui/
    ├── theme/                      цвета, типографика, тема Material3
    └── screens/
        ├── auth/
        │   ├── LoginScreen.kt
        │   └── RegisterScreen.kt
        ├── market/
        │   ├── StocksScreen.kt
        │   └── StockDetailScreen.kt
        ├── portfolio/
        │   └── PortfolioScreen.kt
        └── profile/
            └── ProfileScreen.kt
```

## Навигация

Приложение использует два независимых графа навигации.

### Auth-граф (не авторизован)

```
LoginScreen  ←→  RegisterScreen
     │
 onLoginSuccess
     │
     ▼
  MainApp
```

### Main-граф (авторизован)

Нижняя панель (`NavigationSuiteScaffold`) с тремя вкладками:

| Вкладка | Маршрут | Экран |
|---|---|---|
| Рынок | `stock_list` | `StocksScreen` |
| Портфель | `portfolio` | `PortfolioScreen` |
| Профиль | `profile` | `ProfileScreen` |

Из вкладки **Рынок** можно перейти вглубь:

```
StocksScreen  →  StockDetailScreen(symbol)
```

### Переключение auth ↔ main

В `MainActivity` хранится состояние `isLoggedIn: Boolean` (через `rememberSaveable`).  
- `false` → рендерится `AuthFlow`  
- `true` → рендерится `MainApp`  
- Кнопка "Выйти" в `ProfileScreen` переключает обратно в `false`

## Экраны

### LoginScreen

Поля: логин, пароль.  
Кнопка "Войти" → вызывает `onLoginSuccess` (сейчас без валидации — заглушка).  
Ссылка "Зарегистрироваться" → `RegisterScreen`.

### RegisterScreen

Поля: логин, пароль, подтверждение пароля.  
Кнопка "Зарегистрироваться" → `onRegisterSuccess` (заглушка).

### StocksScreen

Список акций из `StubRepository.stocks`.  
Каждый элемент показывает: тикер, название, цену, изменение % (зелёный/красный).  
Тап по элементу → `StockDetailScreen(symbol)`.

### StockDetailScreen

Получает `symbol` из аргументов навигации.  
Находит акцию через `StubRepository.getStock(symbol)`.

Содержимое:
- Название, текущая цена, изменение %
- `ChartPlaceholder` — заглушка под график (TODO: интегрировать библиотеку графиков)
- Биржевой стакан (5 уровней Bid/Ask, сгенерированных из `highestBid`/`lowestAsk`)
- Bottom bar: кнопки **Купить** / **Продать** → открывают `OrderDialog`

**OrderDialog** — `AlertDialog` с полем количества акций.  
Подтверждение сейчас ничего не делает (заглушка для `POST /api/v1/orders`).

### PortfolioScreen

Карточка сводки: баланс пользователя + суммарный PnL.  
Две вкладки (`TabRow`):
- **Позиции** — список `Position`: тикер, кол-во акций, средняя цена покупки, PnL в $ и %
- **История заявок** — список `Order`: тикер, тип (BUY/SELL), кол-во, цена, статус, дата

### ProfileScreen

Логин и баланс пользователя.  
Кнопка "Выйти из аккаунта" → `onLogout`.

## Модели данных

```kotlin
data class Stock(symbol, name, price, changePercent, highestBid, lowestAsk)

data class Order(id, symbol, type: OrderType, quantity, price, status: OrderStatus, createdAt)
enum class OrderType  { BUY, SELL }
enum class OrderStatus { NEW, ACCEPTED, FILLED, CANCELLED }

data class Position(symbol, name, quantity, avgBuyPrice, currentPrice)
// вычисляемые: pnl, pnlPercent

data class User(id, username, balance)

data class PricePoint(timestamp: Long, price: Double)
```

## Заглушки (StubRepository)

`StubRepository` — `object`, содержит статичные списки.  
Когда бэкенд будет готов — заменить на репозиторий с Retrofit (HTTP) и WebSocket для realtime.

| Поле | Что заменить |
|---|---|
| `stocks` | `GET /api/v1/market/instruments` + `/api/v1/market/quotes` |
| `getPriceHistory()` | `GET /api/v1/market/history` |
| `orders` | `GET /api/v1/orders` |
| `positions` | `GET /api/v1/portfolio/positions` |
| `currentUser` | `GET /api/v1/auth/me` |
| `OrderDialog.onConfirm` | `POST /api/v1/orders` |

## Зависимости (build.gradle.kts)

```
Compose BOM 2025.12.00
  material3
  material3-adaptive-navigation-suite
  ui, ui-graphics, ui-tooling
  material-icons-core

navigation-compose       2.8.9
lifecycle-runtime-ktx    2.6.1
activity-compose         1.8.0
core-ktx                 1.10.1
```
