# Market Data API

#api #market-data #quotes #candles

Сервис: [[market-data-service]] · Порт: 8081 · Базовый путь: `/api/v1`

> [!info] Все запросы через gateway
> Внешний клиент обращается через `http://localhost:8080/api/v1/market/...` с JWT.

## Эндпоинты

### GET /instruments

Поиск торговых инструментов.

**Query params:**
| Параметр | Тип | По умолчанию | Описание |
|---|---|---|---|
| `q` | string | — | Строка поиска (символ или название) |
| `limit` | int | 10 | Количество результатов |
| `offset` | int | 0 | Смещение |

**Ответ 200:**
```json
{
  "instruments": [
    { "symbol": "AAPL", "name": "Apple Inc.", "venue": "NASDAQ" },
    { "symbol": "MSFT", "name": "Microsoft Corp.", "venue": "NASDAQ" }
  ],
  "total": 2
}
```

---

### GET /instruments/{symbol}

**Ответ 200:**
```json
{
  "symbol": "AAPL",
  "name":   "Apple Inc.",
  "venue":  "NASDAQ"
}
```

---

### GET /quotes

Текущие котировки для списка символов.

**Query params:**
| Параметр | Пример | Описание |
|---|---|---|
| `symbols` | `AAPL,MSFT,BTC/USDT` | Символы через запятую |

**Ответ 200:**
```json
{
  "quotes": {
    "AAPL": {
      "symbol":     "AAPL",
      "bid_price":  "150.25",
      "bid_size":   "100",
      "ask_price":  "150.30",
      "ask_size":   "150",
      "last_price": "150.27",
      "mid_price":  "150.275",
      "spread":     "0.05",
      "timestamp":  "2026-05-03T12:00:00Z"
    }
  }
}
```

---

### GET /quotes/{symbol}

Котировка одного символа. Структура ответа аналогична элементу выше.

---

### GET /history/candles

Исторические свечи OHLCV.

**Query params:**
| Параметр | Обязательный | Пример | Описание |
|---|---|---|---|
| `symbol` | да | `AAPL` | Тикер |
| `from` | да | `2026-01-01` | Начало периода |
| `to` | да | `2026-02-01` | Конец периода |
| `resolution` | нет | `1H` | Временной интервал |
| `limit` | нет | `1000` | Макс. свечей |

**Значения resolution:** `1m` `5m` `15m` `30m` `1H` `4H` `1D` `1W`

**Ответ 200:**
```json
{
  "symbol": "AAPL",
  "resolution": "1H",
  "candles": [
    {
      "time":   "2026-01-01T00:00:00Z",
      "open":   "148.50",
      "high":   "151.20",
      "low":    "147.80",
      "close":  "150.30",
      "volume": "1250000"
    }
  ]
}
```

---

### GET /order-book/{symbol}

Стакан заявок (bid/ask уровни).

**Query params:**
| Параметр | По умолчанию | Описание |
|---|---|---|
| `depth` | 10 | Количество уровней |

**Ответ 200:**
```json
{
  "symbol": "AAPL",
  "bids": [
    { "price": "150.25", "size": "100" },
    { "price": "150.20", "size": "250" }
  ],
  "asks": [
    { "price": "150.30", "size": "150" },
    { "price": "150.35", "size": "300" }
  ],
  "timestamp": "2026-05-03T12:00:00Z"
}
```

---

### Health checks

| Метод | Путь | Ответ |
|---|---|---|
| GET | `/system/health` | `{"status": "ok"}` |
| GET | `/system/ready` | `{"status": "ready"}` |

## Связанные страницы

- [[market-data-service]] — детали сервиса
- [[ClickHouse]] — хранилище данных
