# Lua Client Mobile v0.1.1 — module status

This file distinguishes implemented features from planned features. A module must not be presented as working when it has no safe implementation.

## Available in the MVP

| Area | Feature | Status | Notes |
| --- | --- | --- | --- |
| Launcher | Import official Minecraft | Available | Uses the LeviLaunchroid import and version-management flow. Minecraft is not bundled. |
| Launcher | Version selection/isolation | Available | Unsupported or invalid imports show the upstream validation error. |
| Account | Microsoft/Xbox Live | Preserved | No Lua Client password or token collection was added. |
| Native core | `libLuaClient.so` | Available | ARM64 core with initialization and diagnostic metadata; no Windows/DirectX hooks. |
| HUD | FPS | Compatibility-limited | Disabled by default and blocked on Minecraft 1.26.45 until its native hook is validated. |
| HUD | CPS/touch count | Available | Enabled by default; movable and configurable. |
| HUD | HUD editor | Available | Move/reset supported overlay elements. |
| Visual | Zoom | Available | Local visual control only. |
| Visual | Hide HUD/perspective/Snaplook | Available | Local interface/camera controls. |
| Controls | Quick Drop | Available | Local button; users should use care with valuable items. |
| Controls | Virtual cursor, gyro, Pojav controls, more buttons, hotbar shortcuts | Available | Touch-focused controls inherited from the licensed base. |
| Interface | Search, favorites, module cards, configuration | Available | Every module name has a non-empty fallback. Hold a card for description, usage and risk. |
| Diagnostics | Local logs/export | Available | Controlled Lua log only; no account credentials. |

## Planned but unavailable in v0.1.1

These features require version-specific, tested access to Minecraft runtime state and are intentionally unavailable instead of simulated:

- coordinates, speed, direction/compass, current server and ping;
- Armor HUD, Item HUD, item durability and waypoints;
- Fullbright, visual time and particle controls;
- Fast Inventory beyond existing safe UI behavior;
- chat mute/history controls implemented inside Minecraft;
- configuration profiles beyond the existing instance backup/settings;
- safe FOV controls beyond the existing Zoom module.

No anticheat bypass, malicious packet manipulation, reach, combat automation, movement automation or other unfair feature is planned.
