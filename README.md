# Hylock

A Zelda-style target lock-on system plugin for Hytale servers. Part of the **Hyvanced** plugin suite.

## Features

- **Lock-On Targeting** — Acquire locks on nearby entities with middle mouse click
- **Smooth Camera Control** — Interpolated camera movement that follows locked targets
- **Target Switching** — Seamlessly switch between nearby targets while locked
- **Auto-Switch on Kill** — Automatically lock onto the next target when current one dies
- **PvP Mode** — Optional player targeting (toggleable)
- **Visual Indicators** — On-screen feedback showing lock status and target distance
- **Highly Configurable** — Adjust lock range, camera smoothness, detection parameters, and more

## Commands

| Command | Description |
|---------|-------------|
| `/hylock` | Show help menu and current settings |
| `/lock` | Toggle lock onto nearest target |
| `/unlock` | Release current lock |
| `/lockswitch` | Switch to next available target |
| `/locktoggle` | Toggle player targeting on/off |
| `/hylockstatus` | Display detailed current settings |
| `/hylockreset` | Reset all settings to defaults |

## How It Works

Press the **middle mouse button** while looking at an entity to lock onto it. The camera will smoothly follow the target as it moves. Press again on empty space or use `/unlock` to release.

```text
Middle Mouse Click → Lock acquired → Camera follows target → Kill or unlock to release
```

### Lock States

- `IDLE` — No target locked
- `SEARCHING` — Looking for targets
- `LOCKED` — Target acquired and tracked
- `TARGET_LOST` — Target died or went out of range
- `SWITCHING` — Transitioning between targets

## Configuration

All settings can be adjusted in-game or via config:

| Setting | Default | Range | Description |
|---------|---------|-------|-------------|
| `lockOnRange` | 20 | 5-50 | Detection range in blocks |
| `minLockDistance` | 2 | 0.5-5 | Minimum lock distance |
| `cameraSmoothing` | 0.15 | 0-1 | Camera interpolation (0=instant) |
| `cameraHeightOffset` | 1.5 | 0-3 | Eye level adjustment |
| `autoSwitchOnKill` | true | — | Auto-lock next target on kill |
| `prioritizeHostile` | true | — | Prioritize hostile mobs |
| `lockOnPlayers` | true | — | Allow PvP targeting |
| `strafeSpeedMultiplier` | 0.9 | 0.5-1.5 | Movement speed while locked |

## Author

**Nolucci** — Part of the Hyvanced plugin suite
