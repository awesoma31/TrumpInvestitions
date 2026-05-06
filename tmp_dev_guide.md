# Dev Guide: запуск backend с нуля

## Требования

- Docker Desktop (запущен)
- `make` (на Windows — через Git Bash, WSL или [GnuWin32](https://gnuwin32.sourceforge.net/packages/make.htm))
- На Linux для `live-data`: GCC, kernel headers

---

## Windows (userspace-режим, Python pricing engine)

Модуль ядра на Windows не работает. Вместо него используется `userspace_generator.py`,
который запускается как Docker-контейнер с профилем `userspace`.

```bash
# 1. Поднять инфраструктуру + Python pricing engine
make up-userspace

# 2. Инициализировать PostgreSQL (создать юзеров и БД)
make db-init

# 3. Создать таблицу quotes в ClickHouse
make clickhouse-init

# 4. Загрузить исторические котировки (нужен GCC/make для сборки C-бинаря)
#    Если C-тулчейн недоступен — пропустить, Python-генератор заполнит quotes live
make load-data

# 5. Дождаться готовности всех сервисов
make wait

# 6. Проверить статус
make status
```

> **Если `make load-data` не работает** (нет GCC на Windows) — просто пропусти этот шаг.
> `pricing-engine` контейнер сам начнёт генерировать котировки в ClickHouse.
> Данные появятся через несколько секунд после старта.

### Остановить

```bash
make down-userspace   # только userspace-профиль
# или
make down             # всё (volumes сохранятся)
make down-clean       # всё + удалить volumes (полный сброс)
```

---

## Linux (полный режим, модуль ядра)

```bash
# 1. Поднять инфраструктуру (без pricing engine — он отдельно)
make up

# 2. Инициализировать PostgreSQL
make db-init

# 3. Создать таблицу quotes в ClickHouse
make clickhouse-init

# 4. Собрать C-бинарь и загрузить исторические котировки в ClickHouse
make load-data

# 5. Дождаться готовности всех сервисов
make wait

# 6. Проверить статус
make status
```

### Live-стриминг котировок через модуль ядра (опционально)

Запускается **вместо** `load-data`, стримит котировки в реальном времени (блокирующий процесс):

```bash
make live-data
# Внутри: собирает модуль, sudo insmod, запускает ingest_to_clickhouse.sh
```

### Остановить

```bash
make down        # volumes сохранятся
make down-clean  # полный сброс с volumes
```

---

## Сервисы и порты

| Сервис            | URL                              |
|-------------------|----------------------------------|
| Auth Gateway      | http://localhost:8080/api/v1     |
| Market Data       | http://localhost:8081/api/v1     |
| Portfolio         | http://localhost:8082/api/v1     |
| Trading           | http://localhost:8083/api/v1     |
| ClickHouse HTTP   | http://localhost:8123            |
| Kafka             | localhost:9092                   |
| Swagger UI        | http://localhost:8090            |
| Jaeger (трейсинг) | http://localhost:16686           |

---

## Load testing

### Можно ли запустить на Windows?

**Да.** `load-test` полностью работает на Windows — это Kotlin-сервис в Docker,
никаких нативных зависимостей нет.

**Требования:** бекенд уже запущен (`make up-userspace` или `make up` + инициализация).

```bash
# Стандартный тест: 1000 виртуальных пользователей, 120 сек
make load-test

# Указать параметры вручную
make load-test LOAD_USERS=500 LOAD_DURATION_SECONDS=60

# Нагрузочный тест: 10 000 виртуальных пользователей, 300 сек
make load-test-10000

# Открыть дашборд с результатами
make load-dashboard   # http://localhost:8096
```

### Параметры load-test

| Переменная                    | По умолчанию | Описание                          |
|-------------------------------|-------------|-----------------------------------|
| `LOAD_USERS`                  | 1000        | Кол-во виртуальных пользователей  |
| `LOAD_DURATION_SECONDS`       | 120         | Длительность теста                |
| `LOAD_RAMP_UP_SECONDS`        | 30          | Время разгона                     |
| `LOAD_THINK_TIME_MS`          | 250         | Пауза между запросами             |
| `LOAD_WITHDRAW_REQUEST_PERCENT` | 5         | % запросов на вывод средств       |
| `LOAD_SYSTEM_REQUEST_PERCENT` | 10          | % системных запросов              |
| `LOAD_HISTORY_WINDOW_SECONDS` | 86400       | Окно истории (сек)                |
| `LOAD_HISTORY_LIMIT`          | 100         | Лимит записей истории             |
