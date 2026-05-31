# GPXFixer

Kotlin Multiplatform приложение для импорта и будущего редактирования GPX-файлов.

Проект уже умеет выбирать GPX-файл на Android и iOS, парсить его в общей KMP-логике,
сохранять исходный файл и вести локальную историю импортированных треков.

## Текущий стек

- Kotlin Multiplatform
- Compose Multiplatform
- Android + iOS targets
- Gradle Kotlin DSL

## Модули

- `shared` — общий KMP-модуль с доменными моделями GPX, парсером/сериализатором,
  use case импорта, use case просмотра трека, iOS facade и тестами.
- `app-android` — Android-приложение с выбором GPX-файла, импортом, локальным
  хранением исходников и списком импортированных треков.
- `iosApp` — iOS-приложение на SwiftUI с выбором GPX-файла, импортом и локальной
  историей импортированных треков.

## Что реализовано на этом этапе

- Инициализирована сборка Gradle (wrapper + Kotlin DSL).
- Настроены KMP targets: Android и iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`).
- Настроен Compose Multiplatform.
- Добавлены доменные модели GPX-документа, metadata, треков, сегментов и точек.
- Реализован GPX parser с поддержкой namespace, escaped text, CDATA и self-closing tags.
- Реализован GPX serializer для обратной записи документа в XML.
- Реализован общий `ImportGpxTrackUseCase`:
  - валидирует GPX через parser;
  - выбирает display name из metadata, имени трека или имени файла;
  - сохраняет исходный GPX-файл;
  - записывает metadata импорта;
  - откатывает сохраненный файл, если запись metadata не удалась.
- Реализован общий `TrackDetailUseCase`:
  - загружает сохраненный GPX по `storageKey`;
  - повторно парсит файл в `GpxDocument`;
  - считает количество треков, сегментов и точек;
  - считает дистанцию, набор/сброс высоты и диапазон высот;
  - определяет старт/финиш и временной диапазон;
  - возвращает предупреждения по неполным данным.
- Android:
  - выбор GPX через системный document picker;
  - импорт GPX из `ACTION_VIEW`;
  - локальное хранение исходных GPX-файлов;
  - JSON-хранилище истории импортов;
  - экран со статусом импорта и списком импортированных треков;
  - экран просмотра импортированного трека по нажатию на элемент списка;
  - read-only просмотр линии трека на Google Maps;
  - полноэкранная карта трека по нажатию на preview.
- iOS:
  - выбор GPX через `UIDocumentPickerViewController`;
  - импорт GPX из `.onOpenURL`;
  - импорт через общий KMP `ImportGpxTrackUseCase`;
  - просмотр импортированного трека через общий KMP `TrackDetailUseCase`;
  - iOS-реализации storage/store/id/clock в `shared/src/iosMain`;
  - локальное хранение исходных GPX-файлов;
  - JSON-хранилище истории импортов;
  - SwiftUI-экран со статусом импорта, списком импортированных треков и экраном
    деталей трека;
  - read-only preview карты и полноэкранная карта трека.
- Добавлены common tests для GPX parser/serializer и use case импорта.

## Проверка

```bash
./gradlew test
```

## Google Maps API key для Android

Android-карта использует Maps SDK for Android через Google Maps Compose. Ключ не
коммитится: добавьте его локально в `local.properties` в корне проекта:

```properties
MAPS_API_KEY=your_api_key_here
```

Получить ключ можно в Google Cloud Console:

1. Создать или выбрать Google Cloud project.
2. Включить `Maps SDK for Android`.
3. Создать API key в разделе `APIs & Services` -> `Credentials`.
4. Ограничить ключ для Android-приложения `com.gpxeditor.android` и подписывающего
   сертификата SHA-1.

## Что намеренно НЕ реализовано

- Редактируемая карта
- Редактирование трека
- GPX export из отредактированного документа
- Backend
- DI
- Навигация
- Локальная БД вместо JSON-файлов
- Сетевой слой

## Ближайший логичный следующий шаг

Добавить первичный просмотр геометрии трека на карте:

- показать линию трека по координатам из `GpxDocument`;
- начать с read-only карты без редактирования;
- после карты перейти к базовому редактированию и экспорту.
