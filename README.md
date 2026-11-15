# StructEntity - Glass Grid Generator (Fabric)

A high-performance Fabric mod for Minecraft 26.3 that generates massive 3D glass-based grids across the world incrementally to prevent server lag.

## Features
- **Origin**: Generates from `playerPos + 2` as the grid origin.
- **Spacing**: Glass blocks separated by **3 empty blocks distance** (step of 4).
- **Quadrant**: Spreads in one quadrant (+X, +Z) up to **500 blocks wide** on both axes.
- **Vertical Coverage**: Expands from origin Y up to the world build limit (Y=320).
- **Hanging Lanterns (Optional)**: Can attach hanging lanterns directly beneath every glass block (matching the aesthetic in the reference image).
- **Tick-based Incremental Generation**: Generates blocks smoothly across server ticks without freezing the game or causing watchdog timeouts.
- **Action Bar Feedback**: Displays live progress percentage and placed block counts in the action bar.

## In-Game Commands
- `/grid` or `/grid start`: Starts grid generation with default settings (Glass, 500 width, 3 block spacing).
- `/grid start lanterns`: Starts generation with glass blocks and hanging lanterns underneath.
- `/grid start <glass|lanterns> <width> <spacing>`: Starts with custom width and spacing.
- `/grid stop`: Pauses/cancels the current generation task immediately.
- `/grid status`: Shows active generation percentage, coordinates, and blocks placed.
- `/grid speed <blocksPerTick>`: Sets placement speed (default: 2,500 blocks/tick = 50,000 blocks/sec).

## Building & Deploying
- **Build Fabric JAR**:
  ```powershell
  .\gradlew.bat buildFabric
  ```
- **Deploy to MultiMC & Downloads**:
  ```powershell
  .\scripts\deploy.ps1
  ```
