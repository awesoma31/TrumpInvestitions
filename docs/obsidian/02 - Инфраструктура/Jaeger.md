# Jaeger (Distributed Tracing)

#infrastructure #observability #tracing #opentelemetry

| Параметр | Значение |
|---|---|
| Docker image | `jaegertracing/all-in-one` |
| UI порт | **16686** |
| OTLP gRPC | 4317 |
| OTLP HTTP | 4318 |

## Роль в системе

Сбор и визуализация distributed traces. Все микросервисы инструментированы через OpenTelemetry SDK и отправляют спаны в Jaeger.

## Какие сервисы отправляют трейсы

| Сервис | Имя в Jaeger |
|---|---|
| [[auth-gateway]] | `auth-gateway` |
| [[market-data-service]] | `market-data-service` |
| [[portfolio-service]] | `portfolio-service` |
| [[trading-service]] | `trading-service` |

## Что трейсится

- Входящие HTTP-запросы (span на каждый запрос)
- Исходящие HTTP-запросы между сервисами
- Запросы к базам данных (PostgreSQL, ClickHouse)
- Операции с Kafka (produce/consume)

## Propagation

Trace context передаётся через HTTP-заголовки по стандарту W3C TraceContext:
- `traceparent: 00-<trace-id>-<span-id>-<flags>`
- `tracestate: ...`

auth-gateway также поддерживает проброс кастомного заголовка `X-Trace-Id`.

## Пример трейса для POST /orders

```
auth-gateway: POST /api/v1/trading/orders         [200ms]
  └─ trading-service: POST /api/v1/orders          [180ms]
       ├─ market-data: GET /quotes/AAPL             [15ms]
       ├─ postgres: INSERT orders                   [5ms]
       ├─ postgres: INSERT trades                   [4ms]
       └─ kafka: produce trading-events             [3ms]
            └─ portfolio-service: consume event     [10ms]
                 ├─ postgres: UPDATE portfolios     [3ms]
                 └─ postgres: UPSERT positions      [4ms]
```

## Открыть UI

```
http://localhost:16686
```

Выбрать сервис → Find Traces → смотреть waterfall-диаграмму.

## Переменная окружения (все сервисы)

```
OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317
OTEL_SERVICE_NAME=<имя сервиса>
```

## Связанные страницы

- [[Архитектура]] — место в инфраструктуре
- [[Локальный запуск]] — как запустить Jaeger локально
