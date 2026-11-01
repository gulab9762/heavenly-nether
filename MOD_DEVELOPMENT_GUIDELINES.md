# Modern Minecraft Mod Development Guidelines (26.3+ MultiLoader)

This document outlines architectural patterns, modern Minecraft API quirks, performance practices, and UI/UX conventions established during mod development. Share this file with other AI agents or developers working on new mods in this codebase.

---

## 1. Project Architecture (MultiLoader Pattern)

- **`common/`**: Contains 95%+ of all game logic, algorithms, UI screens, network packets, state managers, and data definitions. Keep common pure and free of loader-specific imports.
- **`fabric/`, `forge/`, `neoforge/`**: Thin wrapper subprojects. Their only responsibility is:
  1. Initializing the mod (`ModInitializer` for Fabric, `@Mod` / event bus for Forge & NeoForge).
  2. Registering keybindings and creative tabs / items.
  3. Registering client/server tick event listeners that delegate directly to common handlers (e.g., `UnharvestManager.tick()`).
- **Unified Build**:
  - JDK 25 toolchain with Gradle 9.x.
  - Run `.\gradlew.bat universalJar` to compile common and all loaders into a single universal bundle at `build/libs/<mod>-universal-<version>.jar`.

---

## 2. Modern Minecraft 26.3+ API Standards & Gotchas

### UI & Screen Navigation
- **Screen Transition**: Never call `Minecraft.getInstance().setScreen(screen)`. In modern versions, call:
  ```java
  Minecraft client = Minecraft.getInstance();
  client.setScreenAndShow(new TargetScreen(parentScreen));
  ```
- **Confirmation Prompts**: Always protect destructive buttons (e.g., "Reset Progress") with `ConfirmScreen`:
  ```java
  client.setScreenAndShow(new ConfirmScreen(
      confirmed -> {
          if (confirmed) {
              performDestructiveAction();
          }
          client.setScreenAndShow(this);
      },
      Component.literal("§4§lAre you sure?"),
      Component.literal("This action cannot be undone."),
      Component.literal("§cConfirm"),
      CommonComponents.GUI_CANCEL
  ));
  ```

### Color Collections (`WOOL`, `BED`, `CARPET`)
- Modern Minecraft groups colored variants under `ColorCollection<Block>` instead of individual constants.
- Access via:
  - `Blocks.WOOL.white()` (not `Blocks.WHITE_WOOL`)
  - `Blocks.BED.red()` (not `Blocks.RED_BED`)

### Complex Multi-Block BlockStates (e.g., Beds)
- When placing beds manually, you must place both halves:
  ```java
  BlockState bed = Blocks.BED.red().defaultBlockState();
  level.setBlock(headPos, bed.setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD), 2);
  level.setBlock(footPos, bed.setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT), 2);
  ```
  *(Note: `FACING` points from Foot to Head).*

### Hanging Blocks & Slabs
- `LanternBlock.HANGING = true` must attach underneath a solid face. Prefer placing full solid blocks (e.g. `Blocks.SPRUCE_PLANKS`) over half-slabs directly above hanging lanterns to prevent detachment or floating artifacts.

### Datapack World Preset Translations
- World preset JSONs in `data/<modid>/worldgen/world_preset/<name>.json` use the translation key:
  ```json
  "generator.<modid>.<name>": "Display Name"
  ```
  *(Do not use `worldPreset.<modid>.<name>` for the world creation dropdown selector).*

---

## 3. World Generation & High-Performance Block Modification

### Non-Blocking Radial Batching (Zero-Lag World Population)
- Never place thousands of blocks in a single synchronous freeze frame.
- Enqueue target block coordinates sorted by distance from center, and tick them across game frames using a controlled batch rate:
  ```java
  // 40-50 blocks per tick provides smooth ~20 TPS generation with zero micro-stutter
  private static final int BLOCKS_PER_TICK = 50;
  ```

### Smart Block Diffing
- Before modifying a block in the world, check if it already matches the target state:
  ```java
  if (!level.getBlockState(pos).is(targetBlock)) {
      level.setBlock(pos, targetBlockState, 2); // Flag 2 = Client notification, suppress neighbor cascade
  }
  ```
  Skipping redundant block placements prevents expensive lighting re-calculations, chunk section re-meshing, and network packet storms.

### Air Clearance Limits
- When clearing clearance envelopes above field terrain, keep vertical scans tightly bounded (`groundY + 1` to `groundY + 2`).
- Scanning 30+ blocks vertically across 9,000 coordinates causes over 250,000 redundant block queries and server stalls.

### Structure Footprints: Solid Perimeter vs. Overhangs
- **Solid Footprint**: Bound only the exact interior, walls, and porch where ground crops are disallowed. This allows crops to grow naturally right up against exterior walls without artificial square dirt voids.
- **Overhang Protection (`isNearStructure`)**: Keep a separate bounding buffer that disables vertical air-clearing near buildings to ensure roof eaves, porches, and windmill blades are not accidentally cleaved.

---

## 4. World Setup, Gamerules, and Gamemode Enforcement

- **Execute Only on Creation and Regenerate**:
  - Never execute gamemode sweeps, time adjustments, or gamerule commands inside continuous tick loops (`tick()`).
  - Isolate setup into a helper (e.g., `applyWorldRules()`) and invoke it **strictly once** when a new world is created or when a player triggers `/mod reset` or force regeneration.
- **Setting Time and Gamerules**:
  - To freeze a permanent sunset/dusk without celestial bodies peeking over the horizon, time `12200` hides the rising moon.
  - Suppress command log spam using `server.createCommandSourceStack().withSuppressedOutput()`:
    ```java
    CommandSourceStack stack = server.createCommandSourceStack().withSuppressedOutput();
    server.getCommands().performPrefixedCommand(stack, "gamerule advance_time false");
    server.getCommands().performPrefixedCommand(stack, "gamerule doDaylightCycle false");
    server.getCommands().performPrefixedCommand(stack, "time set 12200");
    ```

---

## 5. UI & HUD Best Practices

- **Tooltip Screen Boundary Clamping**:
  - Always wrap multi-line descriptions using `font.split(Component, maxWidth)`.
  - Calculate background box dimensions before rendering, and clamp `boxX` and `boxY` so tooltips never clip off-screen edges.
- **Exact Numeric Representation**:
  - For currency, score, and clearance counters where players track exact gains, format exact integer amounts with thousands commas (`String.format(Locale.US, "%,d", value)`) instead of abbreviating to imprecise numbers (like `174.7k`).

---

## 6. Testing & Deployment Workflow

1. **Clean Rebuild**: Run `.\gradlew.bat universalJar`.
2. **Safe MultiMC Deployment**:
   - Check if `javaw.exe` is currently holding an exclusive lock on the mod archive before copying.
   - Hot-copying over an in-memory mapped jar can corrupt the JVM zip file header (`ZipException: invalid LOC header`).
   - Copy to `<instance-dir>\.minecraft\mods\` cleanly.

