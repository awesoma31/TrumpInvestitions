# Сборка, запуск и тестирование

## Содержание

- [Требования](#требования)
- [Вся система целиком](#вся-система-целиком)
- [Отдельные сервисы](#отдельные-сервисы)
  - [auth-gateway](#auth-gateway)
  - [market-data-service](#market-data-service)
  - [portfolio-service](#portfolio-service)
  - [trading-service](#trading-service)
  - [pricing_engine](#pricing_engine)

---

## Требования

| Инструмент | Версия | Зачем |
|---|---|---|
| Docker + Compose | любая актуальная | запуск всей системы |
| GCC | любая | сборка `pricing_engine` |
| Python 3 + `requests` | 3.8+ | интеграционные тесты |
| Go | 1.22+ | локальная разработка Go-сервисов |
| JDK | 21 | локальная разработка auth-gateway |
| Gradle | 8.10+ | локальная сборка auth-gateway |

```bash
pip install requests   # один раз, для тестов
```

---

## Вся система целиком

### Порты сервисов

| Сервис | Порт |
|---|---|
| auth-gateway | 8080 |
| market-data-service | 8081 |
| portfolio-service | 8082 |
| trading-service | 8083 |
| Swagger UI | 8090 |
| PostgreSQL | 5433 (хост) → 5432 (контейнер) |
| ClickHouse | 8123, 9000 |
| Kafka | 9092 |

### Первый запуск (чистое окружение)

```bash
make setup
```

Выполняет по порядку:
1. `docker compose up -d --build` — сборка и запуск всех контейнеров
2. `scripts/init-postgres.sh` — создание пользователей и БД (`trading`, `auth_gateway`) в postgres
3. `docker compose restart auth-gateway` — перезапуск после готовности postgres
4. `db/clickhouse/init_clickhouse.sh` — создание таблицы `quotes` в ClickHouse
5. `make -C pricing_engine` + загрузка 5 сценариев котировок (5×1000 строк)

### Повторный запуск (тома сохранены)

Данные postgres и ClickHouse живут в docker volumes и переживают `docker compose down`.

```bash
docker compose up -d --build
make db-init          # создаёт пользователей/БД только если их нет
make clickhouse-init  # CREATE TABLE IF NOT EXISTS
```

### Остановка

```bash
make down          # остановить контейнеры, тома сохранить
make down-clean    # остановить контейнеры и удалить все тома (полный сброс)
```

### Перезагрузить котировки в ClickHouse

```bash
make reset-data    # TRUNCATE quotes → загрузить все 5 сценариев заново
```

### Дождаться готовности всех сервисов

```bash
make wait          # опрашивает /system/health каждые 2с, таймаут 60с
```

### Остановить и запустить заново с нуля

```bash
make down-clean && make setup
```

---

## Тестирование всей системы

Все тестовые команды автоматически ждут готовности сервисов через `make wait`.

```bash
# Юнит-тесты Go (не нужна инфраструктура)
make test-unit

# Интеграционные тесты portfolio-service (напрямую, без gateway)
make test-portfolio

# E2E: portfolio + trading + Kafka (напрямую, без gateway)
make test-integration

# E2E через auth-gateway (JWT, portfolio, trading, Kafka)
make test-gateway

# Всё вместе: поднять → инициализировать → юнит + интеграция + gateway
make test-all
```

---

## Отдельные сервисы

---

### auth-gateway

Kotlin + Ktor, порт **8080**. Единая точка входа: авторизация, JWT, проксирование к внутренним сервисам.

**Зависимости:** postgres (БД `auth_gateway`, пользователь `auth`/`auth`)

#### Сборка и запуск через Docker

```bash
docker compose up -d --build auth-gateway postgres
make db-init   # создать пользователя auth и БД auth_gateway
docker compose restart auth-gateway
```

#### Локальный запуск (JDK 21 + Gradle)

```bash
docker compose up -d postgres
make db-init

cd auth-gateway
gradle run
```

#### Юнит-тесты (без инфраструктуры)

```bash
cd auth-gateway
gradle test
```

#### Проверка вручную

```bash
# Health
curl http://localhost:8080/api/v1/system/health

# Регистрация
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"login":"user1","email":"user1@test.com","password":"StrongPass123!"}' | jq

# Логин → получить JWT
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"user1","password":"StrongPass123!"}' | jq -r '.accessToken')

# Запрос через прокси с JWT
curl http://localhost:8080/api/v1/portfolio \
  -H "Authorization: Bearer $TOKEN"
```

---

### market-data-service

Go, порт **8081**. Агрегация котировок из ClickHouse.

**Зависимости:** ClickHouse с таблицей `quotes` и загруженными данными.

#### Сборка и запуск через Docker

```bash
docker compose up -d --build market-data-service clickhouse
make clickhouse-init
make -C pricing_engine
./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/btcusdt_1000.yaml | ./pricing_engine/push_input_to_db.sh
```

#### Локальный запуск

```bash
docker compose up -d clickhouse
make clickhouse-init
make -C pricing_engine
./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/btcusdt_1000.yaml | ./pricing_engine/push_input_to_db.sh

cd market-data-service
go run ./cmd/market-data-service   # стартует на :8080 (без docker override)
```

#### Юнит-тесты

```bash
cd market-data-service
go test ./...
```

#### Smoke-тест (автоматический)

```bash
./scripts/smoke-test-market-data.sh
# с другим сценарием:
./scripts/smoke-test-market-data.sh pricing_engine/examples/generated/aapl_1000.yaml
# не очищать таблицу перед тестом:
RESET_DB=0 ./scripts/smoke-test-market-data.sh
```

#### Проверка вручную

```bash
curl "http://localhost:8081/api/v1/quotes/BTCUSDT"
curl "http://localhost:8081/api/v1/quotes?symbols=BTCUSDT,AAPL"
curl "http://localhost:8081/api/v1/history/candles?symbol=BTCUSDT&from=2024-04-18T00:00:00Z&to=2024-04-19T00:00:00Z&interval=5m"
curl "http://localhost:8081/api/v1/order-book/BTCUSDT"
```

---

### portfolio-service

Go, порт **8082**. Управление портфелем, баланс, позиции, PnL. Читает события из Kafka.

**Зависимости:** postgres, Kafka.

#### Сборка и запуск через Docker

```bash
docker compose up -d --build portfolio-service postgres kafka
```

#### Локальный запуск

```bash
docker compose up -d postgres kafka
cd portfolio-service
go run .
```

#### Юнит-тесты (без инфраструктуры)

```bash
cd portfolio-service
go test ./...
```

#### Юнит-тесты в Docker

```bash
cd portfolio-service
docker build -f Dockerfile.test -t portfolio-test .
docker run --rm portfolio-test
```

#### Интеграционные тесты

Требуют запущенного сервиса.

```bash
make test-portfolio
# или напрямую:
python portfolio-service/test_endpoints.py --base-url http://localhost:8082/api/v1
```

---

### trading-service

Go, порт **8083**. Создание и исполнение рыночных ордеров. Публикует события в Kafka.

**Зависимости:** postgres, Kafka.

#### Сборка и запуск через Docker

```bash
docker compose up -d --build trading-service postgres kafka
```

#### Локальный запуск

```bash
docker compose up -d postgres kafka
cd trading-service
go run ./cmd/server
```

#### Юнит-тесты (без инфраструктуры)

```bash
cd trading-service
go test ./...
```

#### E2E-тесты (portfolio + trading + Kafka)

Требуют оба сервиса и Kafka.

```bash
make test-integration
# или напрямую:
python tests/test_portfolio_trading.py \
  --portfolio-url http://localhost:8082/api/v1 \
  --trading-url   http://localhost:8083/api/v1 \
  --kafka-wait    3
```

Что покрывает E2E-тест:

| Группа | Сценарии |
|---|---|
| Health | `/system/health`, `/system/ready` |
| Валидация | Отсутствующий/невалидный `X-User-Id` |
| BUY ордер | Создание, статус `FILLED` |
| SELL ордер | Создание, статус `FILLED` |
| Отмена | Попытка отменить `FILLED` ордер → 409 |
| Списки | Ордера и сделки с фильтрами и пагинацией |
| **E2E через Kafka** | BUY → событие → позиция появилась в portfolio-service |

#### E2E-тесты через auth-gateway (полный backend)

Требуют все сервисы. Тесты идут только через порт 8080; `X-User-Id` клиент не передаёт — gateway извлекает его из JWT.

```bash
make test-gateway
# или напрямую:
python tests/test_gateway.py \
  --base-url   http://localhost:8080/api/v1 \
  --kafka-wait 3
```

Что покрывает:

| Группа | Сценарии |
|---|---|
| Health | `/system/health`, `/system/ready` |
| Регистрация | Валидация полей, успех, дубликат → 409 |
| Логин | Неверные данные → 401, успех → JWT |
| Refresh | Обновление `accessToken` |
| Logout | Отзыв токена, повторный запрос → 401 |
| Авторизация | Запрос без токена → 401, невалидный токен → 401 |
| Market (публичный) | Котировки, инструменты — без JWT |
| Portfolio via JWT | Депозит, вывод, портфель, позиции, PnL, ордера, сделки |
| Trading via JWT | Валидация, BUY, GET by id, 404, отмена FILLED → 409, SELL, список |
| **E2E** | register → login → deposit → BUY → Kafka → позиция в portfolio |

---

### pricing_engine

C (C11). CLI-инструмент: генерирует котировки по YAML-сценарию и пишет JSON в stdout.

#### Сборка

```bash
make -C pricing_engine
# или:
cd pricing_engine && make
```

Бинарь: `pricing_engine/pricing_engine`

#### Запуск и загрузка в ClickHouse

```bash
# ClickHouse должен быть запущен и инициализирован
docker compose up -d clickhouse
make clickhouse-init

# Загрузить один сценарий
./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/btcusdt_1000.yaml \
  | ./pricing_engine/push_input_to_db.sh

# Загрузить все сценарии
make load-data

# Сбросить и перезагрузить
make reset-data
```

#### Доступные сценарии

| Файл | Символ | Шагов |
|---|---|---|
| `generated/btcusdt_1000.yaml` | BTCUSDT | 1000 |
| `generated/ethusdt_1000.yaml` | ETHUSDT | 1000 |
| `generated/aapl_1000.yaml` | AAPL | 1000 |
| `generated/msft_1000.yaml` | MSFT | 1000 |
| `generated/tsla_1000.yaml` | TSLA | 1000 |
| `basic_btc.yaml` | BTCUSDT | 5 (минимальный) |

Несколько сценариев можно загружать последовательно — они добавляются в таблицу, не перезаписывают друг друга.

#### Очистка

```bash
make -C pricing_engine clean
```
