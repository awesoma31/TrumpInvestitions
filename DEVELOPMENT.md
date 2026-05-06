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
| Linux kernel headers | текущего ядра | сборка kernel module (`make live-data`) |
| Python 3 + `requests` | 3.8+ | интеграционные тесты |
| Go | 1.22+ | локальная разработка Go-сервисов |
| JDK | 21 | локальная разработка auth-gateway |
| Gradle | 8.10+ | локальная сборка auth-gateway |

Установить kernel headers (если нет):

```bash
# Arch Linux
sudo pacman -S linux-headers

# Ubuntu/Debian
sudo apt install linux-headers-$(uname -r)
```

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
5. `make -C pricing_engine` + загрузка 5 сценариев котировок (5×1000 строк, статика)

После `make setup` котировки статичны. Для живого потока запустить в отдельном терминале:

```bash
make live-data
```

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

## Нагрузочное тестирование

Нагрузочный сервис запускается через отдельный Docker Compose profile и не стартует при обычном `make up`.

```bash
# Стандартный запуск: 1000 пользователей, 120 секунд
make load-test

# Кастомные параметры
make load-test LOAD_USERS=500 LOAD_DURATION_SECONDS=60 LOAD_RAMP_UP_SECONDS=15

# 10 000 виртуальных пользователей
make load-test-10000

# Статус теста (во время или после)
make load-status
# или напрямую:
curl http://localhost:8095/api/v1/load/status
```

Сервис доступен на `http://localhost:8095`. Управление через REST API:

```bash
# Запустить тест вручную с кастомными параметрами
curl -X POST http://localhost:8095/api/v1/load/run \
  -H "Content-Type: application/json" \
  -d '{"virtualUsers": 200, "durationSeconds": 60, "rampUpSeconds": 10}'
```

Перед запуском нагрузочного теста убедись что инфраструктура поднята и данные загружены:

```bash
make setup   # если ещё не запускали
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

Linux kernel module (character device driver). Генерирует синтетические котировки для 5 символов (BTCUSDT, AAPL, ETHUSDT, MSFT, TSLA) прямо в kernel space и экспортирует их через `/dev/pricing_engine`.

Есть два режима работы: **статическая загрузка** (разовый seed из YAML) и **живой поток** (kernel module, непрерывно).

#### Сборка kernel module

```bash
make -C pricing_engine
# или:
cd pricing_engine && make
```

Артефакты: `pricing_engine.ko` (модуль), `test_reader` (валидатор).

#### Режим 1 — статические данные (YAML-сценарии)

Разовая загрузка 5×1000 котировок из предопределённых сценариев:

```bash
# ClickHouse должен быть запущен и инициализирован
docker compose up -d clickhouse
make clickhouse-init

# Загрузить все 5 сценариев
make load-data

# Сбросить и перезагрузить
make reset-data
```

Загрузить один сценарий вручную:

```bash
./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/btcusdt_1000.yaml \
  | ./pricing_engine/push_input_to_db.sh
```

Доступные сценарии:

| Файл | Символ | Шагов |
|---|---|---|
| `generated/btcusdt_1000.yaml` | BTCUSDT | 1000 |
| `generated/ethusdt_1000.yaml` | ETHUSDT | 1000 |
| `generated/aapl_1000.yaml` | AAPL | 1000 |
| `generated/msft_1000.yaml` | MSFT | 1000 |
| `generated/tsla_1000.yaml` | TSLA | 1000 |

#### Режим 2 — живой поток (kernel module)

Непрерывная генерация котировок для всех 5 символов через `/dev/pricing_engine`:

```bash
# Требуется отдельный терминал — процесс блокирующий
make live-data
```

Выполняет по порядку:

1. Собирает `.ko`
2. `sudo insmod pricing_engine.ko` — загружает модуль, создаёт `/dev/pricing_engine`
3. Читает батчи из `/dev/pricing_engine` и вставляет в ClickHouse в бесконечном цикле

Остановить: `Ctrl+C`. Выгрузить модуль:

```bash
sudo rmmod pricing_engine
```

Параметры модуля (опционально при `insmod`):

| Параметр | Дефолт | Описание |
|---|---|---|
| `spread_cents` | 50 | Спред bid/ask в центах |
| `max_move_cents` | 25 | Макс. случайное движение цены за тик |
| `default_size_units` | 100 | Базовый размер котировки |
| `max_last_move_cents` | 10 | Макс. отклонение last от mid |

```bash
sudo insmod pricing_engine.ko spread_cents=100 max_move_cents=50
```

#### Проверка модуля

```bash
# Прочитать несколько котировок вручную
head -n 5 /dev/pricing_engine

# Запустить встроенный валидатор
./pricing_engine/test_reader
```

#### Очистка

```bash
make -C pricing_engine clean
```
