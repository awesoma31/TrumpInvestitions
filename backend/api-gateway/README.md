# TrumpInvestitions API Gateway

API Gateway для торговой платформы TrumpInvestitions, построенный на Ktor.

## Архитектура

API Gateway является точкой входа для всех клиентских запросов и выполняет следующие функции:

- **Аутентификация и авторизация** через JWT токены
- **Маршрутизация запросов** к соответствующим микросервисам
- **WebSocket сервер** для реальных данных (котировки, обновления ордеров)
- **Rate limiting** для защиты от перегрузок
- **Валидация запросов** и обработка ошибок
- **Логирование** запросов и ответов

## Структура проекта

```
backend/api-gateway/
├── src/main/kotlin/com/trumpinvestitions/gateway/
│   ├── Application.kt                    # Точка входа приложения
│   ├── config/                          # Конфигурация Ktor
│   │   ├── Authentication.kt
│   │   ├── HTTP.kt
│   │   ├── RateLimit.kt
│   │   ├── Routing.kt
│   │   ├── Serialization.kt
│   │   ├── StatusPages.kt
│   │   └── WebSockets.kt
│   ├── routes/                          # API маршруты
│   │   ├── AnalyticsRoutes.kt
│   │   ├── PortfolioRoutes.kt
│   │   ├── QuotesRoutes.kt
│   │   ├── TradingRoutes.kt
│   │   └── UserRoutes.kt
│   ├── service/                         # Сервисы для взаимодействия с микросервисами
│   │   ├── QuotesService.kt
│   │   ├── TradingService.kt
│   │   └── WebSocketService.kt
│   ├── security/                        # Безопасность
│   │   └── JwtService.kt
│   ├── di/                             # Dependency Injection
│   │   └── DependencyInjection.kt
│   └── model/dto/                      # DTO классы
│       ├── OrderRequest.kt
│       └── OrderResponse.kt
└── src/main/resources/
    └── application.conf                  # Конфигурация приложения
```

## API Endpoints

### Public Routes
- `GET /api/v1/public/health` - Health check

### Protected Routes (требуется JWT токен)
- `GET /api/v1/trading/orders` - Получить все ордера пользователя
- `GET /api/v1/trading/orders/{orderId}` - Получить ордер по ID
- `POST /api/v1/trading/orders` - Создать новый ордер
- `DELETE /api/v1/trading/orders/{orderId}` - Отменить ордер

- `GET /api/v1/portfolio` - Получить портфель пользователя
- `GET /api/v1/portfolio/performance` - Получить производительность портфеля

- `GET /api/v1/user/profile` - Получить профиль пользователя
- `GET /api/v1/user/balances` - Получить балансы пользователя
- `GET /api/v1/user/transactions` - Получить историю транзакций

- `GET /api/v1/analytics/market` - Получить обзор рынка
- `GET /api/v1/analytics/{ticker}` - Получить аналитику по тикеру
- `GET /api/v1/analytics/{ticker}/candles` - Получить свечные данные

### Quotes Routes (высокий лимит запросов)
- `GET /api/v1/quotes` - Получить все котировки
- `GET /api/v1/quotes/{ticker}` - Получить котировку по тикеру
- `GET /api/v1/quotes/{ticker}/history` - Получить исторические котировки

### WebSocket Endpoints
- `WS /ws/quotes` - Поток котировок в реальном времени
- `WS /ws/orders/{userId}` - Обновления ордеров пользователя

## Запуск

```bash
./gradlew run
```

Приложение будет доступно на `http://localhost:8080`

## Конфигурация

Конфигурация находится в `src/main/resources/application.conf`:

- JWT настройки для аутентификации
- Настройки подключения к базе данных и Redis
- URL внутренних микросервисов
- Параметры rate limiting

## Следующие шаги

1. Реализовать подключение к внутренним микросервисам
2. Настроить Redis Pub/Sub для WebSocket
3. Добавить кэширование ответов
4. Реализовать метрики и мониторинг
5. Добавить интеграционные тесты