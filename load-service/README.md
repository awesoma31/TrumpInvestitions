# Load Service

`load-service` — отдельный сервис нагрузочного тестирования для проекта TrumpInvestitions.

Сервис написан на Kotlin/Ktor и генерирует пользовательский трафик через `auth-gateway`, как будто с системой одновременно работают реальные клиенты. Максимальная поддерживаемая нагрузка по конфигурации — до `10000` виртуальных пользователей.

## Что делает сервис

Каждый виртуальный пользователь выполняет типичный сценарий работы с приложением:

1. Регистрируется в системе.
2. Выполняет логин и получает JWT-токен.
3. Пополняет баланс.
4. Запрашивает рыночные данные.
5. Просматривает портфель.
6. Выставляет market-заявки.
7. Читает историю заявок и сделок.

Сценарий запускается параллельно для заданного количества виртуальных пользователей. Пользователи стартуют постепенно в течение `RAMP_UP_SECONDS`, чтобы нагрузка на систему росла плавно.

## Какие API вызываются

Сервис обращается к gateway API `http://auth-gateway:8080/api/v1`.

Проверка состояния:

- `GET /system/health`
- `GET /system/ready`
- `GET /market/system/health`

Авторизация:

- `POST /auth/register`
- `POST /auth/login`

Рыночные данные:

- `GET /market/instruments`
- `GET /market/instruments/{symbol}`
- `GET /market/quotes`
- `GET /market/quotes/{symbol}`
- `GET /market/history/candles`
- `GET /market/order-book/{symbol}`

Портфель:

- `GET /portfolio`
- `GET /portfolio/positions`
- `GET /portfolio/positions/{symbol}`
- `GET /portfolio/pnl`
- `GET /portfolio/balance/cash`
- `POST /portfolio/balance/deposit`
- `POST /portfolio/balance/withdraw`
- `GET /portfolio/assets/{symbol}/quantity`
- `GET /portfolio/orders`
- `GET /portfolio/trades`

Заявки и сделки:

- `POST /orders`
- `GET /orders`
- `GET /orders/{orderId}`
- `GET /trades`
- `GET /trades/{tradeId}`

## Запуск

Перед запуском нагрузочного теста нужно поднять основную инфраструктуру:

```bash
make setup
```

Обычный запуск нагрузочного сервиса:

```bash
make load-test
```

Запуск с явным количеством пользователей:

```bash
make load-test LOAD_USERS=1000
```

Запуск сценария на 10000 виртуальных пользователей:

```bash
make load-test-10000
```

Также сервис можно запустить напрямую через Docker Compose:

```bash
docker compose --profile load up --build load-service
```

## Просмотр статуса

Через Makefile (форматированный вывод):

```bash
make load-status
```

Или напрямую (raw JSON):

```bash
curl http://localhost:8095/api/v1/load/status
```

Статус содержит:

- выполняется ли тест сейчас;
- сколько пользователей активно;
- сколько запросов выполнено всего;
- сколько запросов завершились успешно;
- сколько запросов завершились ошибкой;
- сколько пользователей зарегистрировалось и залогинилось;
- сколько заявок было отправлено;
- RPS;
- latency: `min`, `avg`, `p50`, `p95`, `p99`, `max`.

## Дашборд реального времени

Веб-дашборд автоматически опрашивает `load-service` каждые 2 секунды и показывает живые графики.

Запустить вместе с тестом:

```bash
docker compose --profile load up -d load-dashboard
make load-dashboard   # откроет http://localhost:8096 в браузере
```

Или запустить вручную отдельно:

```bash
docker compose --profile load up -d load-service load-dashboard
```

Дашборд доступен на `http://localhost:8096` и показывает:

- статус теста (запущен / остановлен) и время старта;
- активные пользователи, total/success/error rate, RPS, p95, p99, ордера;
- графики по времени: RPS, процент ошибок, активные пользователи, latency (p50/p95/p99);
- таблицу эндпоинтов с количеством запросов и ошибок по каждому.

## HTTP API load-service

Проверка состояния самого load-service:

```http
GET /api/v1/system/health
```

Получение текущего статуса теста:

```http
GET /api/v1/load/status
```

Запуск нового теста:

```http
POST /api/v1/load/run
```

Пример тела запроса:

```json
{
  "virtualUsers": 500,
  "durationSeconds": 120,
  "rampUpSeconds": 30,
  "orderEveryNIterations": 5
}
```

Если тест уже выполняется, сервис вернет `409 Conflict`.

## Переменные окружения

| Переменная | Значение по умолчанию | Описание |
| --- | --- | --- |
| `GATEWAY_URL` | `http://auth-gateway:8080/api/v1` | URL auth-gateway внутри Docker-сети |
| `VIRTUAL_USERS` | `1000` | Количество виртуальных пользователей |
| `MAX_VIRTUAL_USERS` | `10000` | Верхний лимит пользователей |
| `DURATION_SECONDS` | `120` | Длительность теста в секундах |
| `RAMP_UP_SECONDS` | `30` | Время плавного запуска пользователей |
| `REQUEST_TIMEOUT_MS` | `10000` | Таймаут HTTP-запроса |
| `ORDER_EVERY_N_ITERATIONS` | `5` | Как часто пользователь выставляет заявку |
| `SELL_ORDER_PERCENT` | `0` | Процент SELL-заявок среди всех заявок |
| `MAX_ORDER_QUANTITY` | `5` | Максимальное количество бумаг в заявке |
| `INITIAL_DEPOSIT` | `1000000.00` | Начальное пополнение баланса пользователя |
| `WITHDRAW_AMOUNT` | `10.00` | Сумма частичного вывода средств |
| `WITHDRAW_REQUEST_PERCENT` | `5` | Вероятность вызова withdraw/deposit в цикле |
| `SYSTEM_REQUEST_PERCENT` | `10` | Вероятность вызова health/ready endpoint'ов |
| `PROGRESS_LOG_INTERVAL_SECONDS` | `10` | Как часто писать progress-лог во время теста |
| `HISTORY_WINDOW_SECONDS` | `86400` | Размер окна истории свечей |
| `HISTORY_LIMIT` | `100` | Количество свечей в запросе |
| `THINK_TIME_MS` | `250` | Пауза между итерациями пользователя |
| `SYMBOLS` | `AAPL,MSFT,TSLA,BTCUSDT,ETHUSDT` | Инструменты для тестирования |
| `RUN_ON_START` | `true` | Запускать тест автоматически при старте сервиса |
| `START_DELAY_SECONDS` | `10` | Задержка перед автозапуском |

## Примеры запуска

Короткий тест на 100 пользователей:

```bash
make load-test LOAD_USERS=100 LOAD_DURATION_SECONDS=60 LOAD_RAMP_UP_SECONDS=10
```

Более агрессивный тест на 5000 пользователей:

```bash
make load-test LOAD_USERS=5000 LOAD_DURATION_SECONDS=300 LOAD_RAMP_UP_SECONDS=60 LOAD_THINK_TIME_MS=100
```

Тест с большим количеством заявок:

```bash
make load-test LOAD_USERS=1000 LOAD_ORDER_EVERY_N_ITERATIONS=2
```

Тест с периодическими SELL-заявками:

```bash
make load-test LOAD_USERS=1000 LOAD_ORDER_EVERY_N_ITERATIONS=3
```

Для SELL-заявок внутри контейнера используется переменная `SELL_ORDER_PERCENT`. При необходимости ее можно добавить в `docker-compose.yml` по аналогии с остальными `LOAD_*` параметрами.

## Важные замечания

- `load-service` запускается через Docker Compose profile `load`, поэтому не стартует при обычном `docker compose up`.
- Для корректных рыночных данных перед нагрузочным тестом нужно выполнить загрузку котировок через `make setup` или `make load-data`.
- Каждый запуск создает новых пользователей с уникальными именами вида `load_<runId>_<index>`.
- При нагрузке `10000` пользователей машине может потребоваться много CPU, RAM и сетевых соединений.
- Результаты теста показывают нагрузку на весь путь через `auth-gateway`, а не на отдельный микросервис напрямую.
