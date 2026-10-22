# FPV Drones — архитектура

Minecraft 26.1.2, NeoForge 26.1.2.122, Java 25. Сборка: `.\gradlew.bat build`, деплой: скопировать
`build\libs\fpv-1.0.jar` в `%APPDATA%\.minecraft\versions\fpvtest\mods\`.

## Пакеты

| Пакет | Что лежит |
|---|---|
| `org.restor.fpv` | `Fpv` (входная точка, креатив-таб), `Config` (клиентский конфиг: пульт, HUD, dev-параметры) |
| `.entity` | `DroneEntity` — БАЗА: физика (SI-единицы), переключение режимов полёта, клиентское предсказание, удалённое управление, код связи. Подклассы: Cinewhoop/Racing/TinyWhoop (езда верхом), `RemoteDroneEntity` → RemoteRacing/Kamikaze (по коду), `OperatorDummyEntity`, `ModEntities` |
| `.assembly` | **Модульный конструктор**: `DroneAssembly` (состав + производные характеристики + перекос), `FrameSpec`/`FrameSpecs` (ТОЧКИ КРЕПЛЕНИЯ — сюда вводить координаты из Blockbench), `MountPoint`, `ModularDroneEntity/Item`, `AssemblyStationBlock/BlockEntity/Menu` |
| `.flight` | `FlightController` (режимы, expo), `PIDController`, `FlightMode` |
| `.data` | `DroneData` (пресеты характеристик), `Battery` |
| `.component` | Предметы-компоненты: `ComponentItem/Stats/Type`, `ModComponents` (рамы/моторы/пропы/аккумы/камеры) |
| `.controller` | `RCController` — чтение GLFW-джойстика (RadioMaster и т.п.), маппинг осей |
| `.block`, `.block.entity` | Верстак (легаси), станция управления (список кодов), пусковая станция камикадзе, регистрации |
| `.menu` | Контейнерные меню (верстак — легаси, станция сборки в `.assembly`) |
| `.item` | `DroneItem` (спавн дрона из предмета), `ModItems` |
| `.network` | `ModNetworking` + пакеты: ввод с пульта, коды, rates, станции |
| `.client` | Тик-обработчик ввода, клавиши, `PilotView`, `ClientRaceState` |
| `.client.ui.hud` | HUD в полёте: `FpvHud`, `DroneOsd`, `PlaneOsd`, `RaceHud`, `DamageHud` (схема повреждений модулей), `HudWidgets` |
| `.client.ui.screen` | Все экраны настроек/кодов/станций |
| `.client.render` | `FpvCamera`, рендереры сущностей/блоков, призраки, частицы |

Команды сборки — в `BUILD.md`.

## Ключевые механики

- **Физика** (`DroneEntity`): 1 блок = 1 м. Тяга — кватернионный поворот body-up,
  сопротивление ½ρ·CdA·v² анизотропное (×4 вдоль оси пропов). Вращение
  интегрируется в body-frame кватернионом (`integrateRotation`), камера/модель
  интерполируются slerp'ом (без рывков на ±90°).
- **Предсказание**: клиент интегрирует вращение из своих стиков сразу
  (`setClientPredictedInputs` → `clientTick`), сервер авторитетен; коррекция —
  slerp 8%/тик + мягкая коррекция позиции 15%/тик (`lerpTo` перехвачен).
- **Удалённое управление**: `beginRemoteControl` = vanilla `setCamera` (тело
  пилота следует за дроном — ЭТО механизм стриминга чанков клиенту, проверено по
  декомпилу `ServerPlayer.tick`), плюс форс-лоад чанков вокруг дрона; выход —
  Shift (vanilla `wantsToStopRiding`), тело возвращается на станцию; у станции
  остаётся `OperatorDummyEntity`.

## Конструктор дронов (как доделывать)

1. **Модели**: положи Blockbench-экспорт (block model JSON) в
   `assets/fpv/models/item/<имя_компонента>.json` (например `frame_5inch.json`,
   `motor_2207_2450kv.json`, `prop_5x4x3.json`, `battery_4s_1300.json`,
   `camera_standard.json`). Рендерер собирает дрона из МОДЕЛЕЙ ПРЕДМЕТОВ —
   код менять не нужно.
2. **Координаты креплений**: `assembly/FrameSpecs.java` — для каждой рамы
   `MountPoint.fromBlockbench(x, y, z)` в пикселях модели (0..16, центр = 8).
   Порядок моторов: 0=перед-право, 1=перед-лево, 2=зад-право, 3=зад-лево, дальше
   парами. Нос = меньший Z (north).
3. **Новая рама** (в т.ч. гекса/окто): предмет в `ModComponents` +
   `FrameSpec(6, List.of(...6 mounts...), ...)` в `FrameSpecs` — слоты в станции
   сборки уже рассчитаны на 8 моторов.
4. **Перекос от пропов**: `DroneAssembly.computeTrim()` — разница тяги мотор+проп
   по слотам даёт постоянный увод по тангажу/крену/рысканью, пропорциональный
   газу (хуки `trim*Rate()` в `DroneEntity.integrateRotation`).

## Легаси

`WorkbenchBlock/Menu/Screen`, `DroneBuilder`, `ComponentDroneEntity` — старый
конструктор (один слот пропов). Новый путь — станция сборки (`.assembly`).
Можно удалить, когда новый конструктор устроит.
