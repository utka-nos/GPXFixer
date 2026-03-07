# GPXFixer

Минимальный scaffold Kotlin Multiplatform проекта для будущего мобильного приложения-редактора GPX-файлов.

## Текущий стек

- Kotlin Multiplatform
- Compose Multiplatform
- Android + iOS targets
- Gradle Kotlin DSL

## Модули

- `shared` — общий KMP-модуль с Compose UI-заглушкой и базовой структурой слоев (`core`, `domain`, `data`, `feature`).
- `app-android` — Android-приложение, использующее UI из `shared`.
- `iosApp` — базовый iOS scaffold (SwiftUI entrypoint + обвязка для подключения `shared` из Xcode).

## Что реализовано на этом этапе

- Инициализирована сборка Gradle (wrapper + Kotlin DSL).
- Настроены KMP targets: Android и iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`).
- Настроен Compose Multiplatform.
- Добавлен стартовый экран-заглушка с текстом:
  - `GPX Editor App`
  - `Initial project scaffold`
- Настроена Android-точка входа (`MainActivity`) для запуска приложения.
- Подготовлен iOS entrypoint (`MainViewController`) в `shared` для использования в Xcode.

## Что намеренно НЕ реализовано

- GPX import/export
- GPX parser
- Карта
- File picker
- Backend
- Доменные сущности
- DI
- Навигация
- Локальная БД
- Сетевой слой
