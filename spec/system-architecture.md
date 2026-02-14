# 1. Общая архитектурная модель

Тип архитектуры:
**Микросервисная + Event-Driven + CQRS (частично)**

Почему так:

* 10k клиентов → нужна масштабируемость
* есть поток котировок → событийная модель
* ClickHouse → аналитика и агрегации
* PostgreSQL → транзакционные операции
* Redis → кэш + брокер

---

# 2. Логическая схема системы

## Уровень 1 — Клиенты

1. Android App (Kotlin + Compose)
2. Cross-platform App (React Native)

Они:

* получают котировки
* создают заявки на покупку/продажу
* смотрят портфель
* получают историю операций

Связь с backend:

* REST API
* WebSocket (для котировок в реальном времени)

---

## Уровень 2 — API Gateway (Ktor)

Функции:

* Аутентификация
* Авторизация
* REST endpoints
* WebSocket сервер
* Rate limiting
* Валидация запросов

Он НЕ работает напрямую с БД.
Он вызывает внутренние сервисы.

---

## Уровень 3 — Внутренние микросервисы

### 3.1 Trading Service (Kotlin)

Отвечает за:

* создание ордеров
* проверку баланса
* фиксацию сделок
* бизнес-логику

Работает с:

* PostgreSQL (транзакции)
* Redis (кэш)
* отправляет события в Redis Pub/Sub

---

### 3.2 Market Data Service (Go)

Получает котировки от:

Linux Driver (C) → генерирует поток котировок

Сервис:

* нормализует данные
* отправляет их:

  * в Redis (для realtime)
  * в ClickHouse (для хранения истории)

---

### 3.3 Analytics Service (Kotlin)

Работает с ClickHouse.

Отвечает за:

* построение графиков
* агрегации (OHLC)
* средние значения
* объём торгов
* статистику по клиентам
* топ бумаг

---

# 3. Где используется ClickHouse

Она используется для:

1. Истории котировок
   Таблица market_quotes:

   * timestamp
   * ticker
   * price
   * volume

2. Истории сделок (event store)

   * client_id
   * ticker
   * side
   * price
   * timestamp

3. Агрегаций:

   * свечи (1m, 5m, 1h)
   * средняя цена
   * волатильность
   * топ активов

Почему именно ClickHouse:

* Column-oriented
* Очень быстрые GROUP BY
* Отлично работает с time-series
* Высокая скорость чтения
* Хорошая компрессия

---

# 4. PostgreSQL — зона ответственности

PostgreSQL — OLTP база.

Храним:

* users
* accounts
* balances
* orders
* portfolios

Все операции:

* транзакционные
* ACID
* Consistency важнее скорости чтения

---

# 5. Redis — роль в системе

Redis используется как:

1. Кэш котировок
2. Pub/Sub для realtime
3. Кэш сессий
4. Буфер между сервисами

Поток:

Driver → MarketDataService → Redis PubSub → API WebSocket → Клиенты

---

# 6. Потоки данных

## Поток котировок

Linux Driver (C)
→ Market Data Service (Go)
→ Redis (realtime)
→ ClickHouse (хранение)
→ Analytics Service
→ API
→ Клиент

---

## Поток сделки

Клиент
→ API
→ Trading Service
→ PostgreSQL (транзакция)
→ событие в Redis
→ запись в ClickHouse (для аналитики)

---

# 7. Разделение ответственности

| Компонент           | Тип нагрузки | СУБД       |
| ------------------- | ------------ | ---------- |
| Trading             | OLTP         | PostgreSQL |
| Market data history | OLAP         | ClickHouse |
| Realtime quotes     | in-memory    | Redis      |
| Analytics           | heavy read   | ClickHouse |

---

ClickHouse используется как аналитическое хранилище временных рядов и агрегированных данных, разгружая транзакционную PostgreSQL и обеспечивая быструю выдачу графиков и статистики клиентам.
