# Portfolio API

#api #portfolio #positions #pnl

Сервис: [[portfolio-service]] · Порт: 8082 · Базовый путь: `/api/v1`

> [!info] Все запросы через gateway
> Внешний клиент → `http://localhost:8080/api/v1/portfolio/...`  
> Gateway добавляет `X-User-Id` — сервис использует его для изоляции данных.

## Эндпоинты

### GET /portfolio

Полный снапшот портфеля: баланс + позиции + нереализованный P&L.

**Ответ 200:**
```json
{
  "user_id":      "550e8400-...",
  "cash_balance": "5000.00000000",
  "positions": [
    {
      "symbol":           "AAPL",
      "quantity":         "10.00000000",
      "avg_price":        "150.00000000",
      "current_price":    "155.00000000",
      "market_value":     "1550.00000000",
      "unrealized_pnl":   "50.00000000",
      "realized_pnl":     "100.00000000"
    }
  ],
  "total_market_value": "6550.00000000",
  "total_unrealized_pnl": "50.00000000"
}
```

---

### GET /positions

Список позиций.

**Query params:**
| Параметр | Пример | Описание |
|---|---|---|
| `symbol` | `AAPL` | Фильтр по символу (опционально) |

---

### GET /positions/{symbol}

Позиция по конкретному инструменту. Возвращает 404 если позиция не открыта.

---

### GET /pnl

Сводка P&L.

**Ответ 200:**
```json
{
  "user_id":          "550e8400-...",
  "realized_pnl":     "250.00000000",
  "unrealized_pnl":   "50.00000000",
  "total_pnl":        "300.00000000"
}
```

---

### GET /balance/cash

**Ответ 200:**
```json
{
  "user_id":      "550e8400-...",
  "cash_balance": "5000.00000000",
  "currency":     "USD"
}
```

---

### POST /balance/deposit

Пополнение счёта.

**Тело запроса:**
```json
{
  "amount":   "1000.00",
  "currency": "USD"
}
```

**Ответ 200:**
```json
{
  "cash_balance": "6000.00000000"
}
```

---

### POST /balance/withdraw

Вывод средств. Возвращает 400 если недостаточно средств.

**Тело запроса:**
```json
{
  "amount":   "500.00",
  "currency": "USD"
}
```

---

### GET /orders

История ордеров пользователя (агрегируется из trading DB).

---

### GET /trades

История сделок пользователя.

---

### GET /assets/{symbol}/quantity

Количество актива в портфеле.

**Ответ 200:**
```json
{
  "symbol":   "AAPL",
  "quantity": "10.00000000"
}
```

---

### Health checks

| Метод | Путь | Ответ |
|---|---|---|
| GET | `/system/health` | `{"status": "ok"}` |
| GET | `/system/ready` | `{"status": "ready"}` |

## Связанные страницы

- [[portfolio-service]] — детали сервиса и схема БД
- [[Kafka trading-events]] — как обновляется портфель
- [[Создание ордера]] — полный flow
