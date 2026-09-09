# FPV Drones

Buildable FPV quadcopters and fixed-wing aircraft for Minecraft **1.21.1 / NeoForge 21.1.244**.

Assemble airframes part by part, bind a radio transmitter, put on the goggles and fly —
acro, angle and horizon flight modes, a real OSD, racing with ghosts, payloads and
warheads, hostile NPC drones and an anti-drone layer to shoot them down.

---

## Getting started

1. Craft an **Assembly Station** (copper unlocks it).
2. Build a frame, motors, props, a battery, a flight controller, a camera and a VTX,
   then assemble them into a **Modular Drone** at the assembly station.
3. Craft an **RC Transmitter** and **FPV Goggles**.
4. Place the drone, then **sneak + right-click** it with the transmitter to bind.
5. **Right-click** with the transmitter to take control.

Without goggles the transmitter still flies the drone, but you keep your own eyes —
line-of-sight flying, limited to the transmitter's range.

## Controls

The whole FPV block is rebindable under **Options → Controls → FPV Drones**.

### Radio

| Input | Action |
| --- | --- |
| Sneak + right-click (or punch) an aircraft | Bind / unbind the transmitter |
| Right-click with the transmitter | Take control of the bound aircraft |
| Sneak while flying | Leave the aircraft and return to your body |

### Quadcopter

| Key | Action |
| --- | --- |
| `W` `A` `S` `D` / mouse | Throttle, yaw, roll, pitch |
| `Z` | Arm / disarm |
| `R` | Cycle flight mode (Acro → Angle → Horizon) |
| `Page Up` / `Page Down` | Camera tilt |
| `X` | Drop one payload charge |
| `H` | Toggle the HUD |
| `O` | FPV settings screen |

### Fixed wing

| Key | Action |
| --- | --- |
| Mouse | Fly, or pan the belly camera (see `C`) |
| Left mouse button | Lock the camera on the point under the crosshair |
| `C` | Swap mouse between flying and camera |
| `V` | Recenter the camera gimbal |
| `G` | Orbit autopilot around the locked point |
| `B` | Scout wing: call a kamikaze strike on the locked point |
| `H` / `O` | HUD / settings |

`G` and `B` both need a locked point first — aim at a block and click the left mouse
button, then press the key.

### Replays

| Key | Action |
| --- | --- |
| `F6` | Save the last 30 seconds |
| `F7` | Play / exit |
| `F8` | Pause / resume |
| `F9` | Cycle playback speed |
| `[` / `]` | Skip 5 seconds back / forward |

### Gamepads and real radios

Any controller GLFW recognises works, including USB transmitters in joystick mode.
Arming lives on an AUX axis — see the settings screen (`O`) to map it. Without an
arm switch bound the throttle stays inert.

## What's in the mod

- **Airframes** — tiny whoop, cinewhoop, 5", 7", hexa 6", octo 8"; delta wing, glider,
  twin-boom cargo, carrier, scout, long-range and interceptor planes.
- **Flight** — acro / angle / horizon, per-frame mass and thrust curves, battery sag,
  radio failsafe, propwash and turbulence.
- **Racing** — start / finish / gate blocks, routes, lap timing, personal and track
  record ghosts.
- **Payloads** — drop charges, storage bays, dart guns and six warhead families
  (frag, HE, heavy, shaped, thermobaric, cluster).
- **Stations** — assembly, control, kamikaze and charging stations, plus a drone nest
  that deploys and recovers aircraft on its own.
- **Threats and defence** — hostile, patrol and interceptor NPC drones; a drone
  detector that drives redstone and a jammer that cuts control links.

## Configuration

- `config/fpv-common.toml` — server-side rules: transmitter and goggle requirements,
  radio ranges, detector and jammer behaviour.
- `config/fpv-client.toml` — HUD elements, camera and input feel.
- `<world>/serverconfig/fpv-track-generation.toml` — procedural race track generation.
- In-game: press `O` while flying.

## Building

```
./gradlew build
```

The jar lands in `build/libs/`.

## License

MIT — see [LICENSE](LICENSE).
