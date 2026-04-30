# TrumpInvestitions Android Native App

Приложение для инвестиций и торговли на платформе Android, разработанное на Kotlin с использованием Jetpack Compose.

## Архитектура

**MVVM + Clean Architecture** с модульной структурой проекта.

## Структура проекта

```
mobile/android-native/
├── app/                          # Основной модуль приложения
├── core/                         # Базовый модуль (общие утилиты, DI)
├── data/                         # Модуль данных (Repository, API, DB)
├── domain/                       # Модуль доменной логики (UseCases, Models)
├── ui/                           # Общие UI компоненты и тема
├── navigation/                   # Модуль навигации
└── feature/                      # Feature-модули
    ├── auth/                     # Модуль авторизации
    ├── trading/                  # Модуль торгового интерфейса
    ├── portfolio/                # Модуль портфеля
    ├── charts/                   # Модуль графиков
    └── settings/                 # Модуль настроек
```

## Технологический стек

- **UI**: Jetpack Compose + Material3
- **Architecture**: MVVM + Clean Architecture
- **Async**: Coroutines + Flow
- **DI**: Hilt
- **Network**: Retrofit + OkHttp
- **WebSocket**: OkHttp WebSocket
- **Database**: Room
- **Testing**: JUnit5 + MockK + Compose Testing

## Основные функции

- Получение котировок в реальном времени
- Создание заявок на покупку/продажу
- Управление портфелем
- Просмотр истории операций
- Графики и аналитика