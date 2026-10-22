# Сборка и запуск

Minecraft 1.21.1, NeoForge 21.1.264, Java 21. Все команды — из корня проекта в PowerShell.

## Собрать jar

```powershell
.\gradlew.bat build
```

Без сети (если зависимости уже скачаны):

```powershell
.\gradlew.bat build --offline
```

Только проверить, что компилируется (быстрее, без jar):

```powershell
.\gradlew.bat compileJava --offline
```

Чистая пересборка:

```powershell
.\gradlew.bat clean build
```

## Куда ложится файл

```
build\libs\fpv-1.0.0+1.21.1-neoforge.jar
```

Имя = `mod_id`-`mod_version`+`minecraft_version`-neoforge из `gradle.properties`.

## Установить в игру

Профиль лаунчера `fpvtest`:

```powershell
Copy-Item build\libs\fpv-1.0.0+1.21.1-neoforge.jar "$env:APPDATA\.minecraft\versions\fpvtest\mods\" -Force
```

Сборка + установка одной строкой:

```powershell
.\gradlew.bat build --offline; if ($?) { Copy-Item build\libs\fpv-1.0.0+1.21.1-neoforge.jar "$env:APPDATA\.minecraft\versions\fpvtest\mods\" -Force }
```

## Запуск из IDE/Gradle (dev-клиент)

```powershell
.\gradlew.bat runClient
```

Рабочая папка dev-клиента — `run\` (логи в `run\logs\latest.log`).

## Где что лежит в клиентском коде

| Пакет | Что там |
|---|---|
| `client/ui/hud` | HUD в полёте: `FpvHud` (точка входа), `DroneOsd`, `PlaneOsd`, `RaceHud`, **`DamageHud` — схема сломанных модулей справа снизу**, `HudWidgets` (прицел, авиагоризонт, стики, газ) |
| `client/ui/screen` | Все экраны (GUI): настройки, станции, сборка дрона |
| `client/render` | Рендер мира: рендереры сущностей и блоков, призраки гонок, частицы, `FpvCamera` (углы FPV-камеры) |
| `client/sound` | Звук моторов |
| `client` | Ввод, клавиши, `PilotView` (что сейчас пилотируем), `ClientRaceState` (состояние гонки с сервера) |
