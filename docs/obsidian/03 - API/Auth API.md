# Auth API

#api #auth #jwt

Сервис: [[auth-gateway]] · Порт: 8080 · Базовый путь: `/api/v1/auth`

> [!info] Swagger
> Спецификация OpenAPI доступна по адресу `http://localhost:8080/swagger` в разделе **auth-gateway**.

## Эндпоинты

### POST /api/v1/auth/register

Регистрация нового пользователя.

**Тело запроса:**
```json
{
  "email":    "user@example.com",
  "password": "SecurePass123"
}
```

**Ответ 201:**
```json
{
  "user_id":      "550e8400-e29b-41d4-a716-446655440000",
  "email":        "user@example.com",
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4...",
  "expires_in":   3600
}
```

**Ошибки:**
| Код | Причина |
|---|---|
| 400 | Невалидный формат email или слабый пароль |
| 409 | Email уже зарегистрирован |

---

### POST /api/v1/auth/login

Вход по email + пароль.

**Тело запроса:**
```json
{
  "email":    "user@example.com",
  "password": "SecurePass123"
}
```

**Ответ 200:**
```json
{
  "user_id":       "550e8400-...",
  "access_token":  "eyJ...",
  "refresh_token": "dGhp...",
  "expires_in":    3600
}
```

**Ошибки:**
| Код | Причина |
|---|---|
| 401 | Неверный email или пароль |

---

### POST /api/v1/auth/refresh

Обновление access-токена по refresh-токену.

**Тело запроса:**
```json
{
  "refresh_token": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4..."
}
```

**Ответ 200:**
```json
{
  "access_token":  "eyJ...",
  "refresh_token": "bmV3UmVmcmVzaFRva2Vu...",
  "expires_in":    3600
}
```

**Ошибки:**
| Код | Причина |
|---|---|
| 401 | Refresh-токен невалиден или истёк |

---

### POST /api/v1/auth/logout

Инвалидация refresh-токена.

**Заголовки:** `Authorization: Bearer <access_token>`

**Ответ 204:** (без тела)

---

## Аутентификация для других сервисов

Все запросы к бизнес-сервисам через gateway требуют заголовка:

```
Authorization: Bearer <access_token>
```

Gateway добавляет к проксируемому запросу:
```
X-User-Id: <uuid пользователя>
```

## Связанные страницы

- [[auth-gateway]] — детали сервиса
- [[Регистрация и логин]] — flow с диаграммой
