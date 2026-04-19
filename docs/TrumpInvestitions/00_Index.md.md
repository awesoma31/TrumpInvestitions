# TrumpInvestitions — Документация

Платформа для торговли акциями с мобильным Android-клиентом и микросервисным бэкендом.

## Навигация

| Страница | Описание |
|---|---|
| [[Архитектура]] | Общая архитектура системы, компоненты, потоки данных |
| [[Android_Приложение]] | Структура Android-приложения, экраны, навигация |
| [[Сервисы_Бекенда]] | Все микросервисы: что делают, как общаются |
| [[API_Endpoints]] | Полный список публичных эндпоинтов через API Gateway |
| [[Базы_Данных]] | Схема PostgreSQL и ClickHouse |

## Быстрый старт — что есть в репозитории

```
TrumpInvestitions/
├── front-ktl/        Android-приложение (Kotlin + Jetpack Compose)
├── swagger/          OpenAPI 3.1 спеки всех сервисов
├── docs/             Документация (этот vault) + диаграммы
└── README.md         Техническое задание проекта
```

## Стек технологий

| Слой | Технологии |
|---|---|
| Android-клиент | Kotlin, Jetpack Compose, Navigation Compose |
| Бэкенд | Go (Auth, Market Data), Kotlin (Order, Portfolio) |
| Эмулятор биржи | C |
| Брокер сообщений | Kafka |
| Базы данных | PostgreSQL, ClickHouse, Redis |
| Прокси / Gateway | Nginx + API Gateway |
| Наблюдаемость | OpenTelemetry |
| Аутентификация | JWT + bcrypt |
