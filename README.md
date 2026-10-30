# Block Place Animations & Tree Shake

A client-side Minecraft mod (for **Minecraft 26.3** Fabric & NeoForge) that adds fluid, satisfying visual animations when placing blocks and cartoonish tree punch shake physics when punching or harvesting trees.

Inspired by [A Good Place](https://modrinth.com/mod/a-good-place), this mod gives tactile weight and visual feedback to building and tree interactions.

---

## ✨ Features

### 🌴 Cartoon Tree Punch Shake (Client-Side)
- **Topological Tree BFS Detection**:
  - Automatically identifies connected tree trunks (`#minecraft:logs`, `#minecraft:warped_stems`, `#minecraft:crimson_stems`) and canopy foliage (`#minecraft:leaves`, `#minecraft:wart_blocks`).
  - Distinguishes natural trees and bare trunks from man-made structures like log cabins or bridges.
- **Cartoon Spring & Inverse Pendulum Physics**:
  - Anchors firmly at the root while tilting and swaying in the direction of the player's punch.
  - The trunk bends with height-dependent curvature ($h^{1.25}$), so the upper canopy sways wider than the base.
  - Bouncy damped spring harmonic oscillation with organic 3D cross-wobble figure-8 physics.
  - **Cartoon Squash & Stretch**: Compresses vertically on punch impact while bulging outwards, stretching tall on spring rebound.
- **Foliage Flutter & Leaf Particles**:
  - Independent high-frequency micro-rustle and breathing scale for each leaf block.
  - Punches cause leaf particles and wood splinters to burst out and gently float down with gravity.
  - Punch impact sound effects (`WOOD_HIT` + `CHERRY_LEAVES_FALL` rustle).
- **Mining Combo / Continuous Punching**:
  - Holding down attack while chopping down a tree delivers rhythmic impulses on each fist swing, keeping the tree in continuous cartoon wobble until the block breaks.
- **Zero-Flicker Transition**:
  - Seamless chunk mesh handoff: chunk sections re-bake during settling without any visible popping.

### 🧱 Hand-to-Target Block Placement
- **Dynamic Hand Positioning**: Calculates the 3D hand coordinates in first-person view, accounting for camera angle, FOV, arm swing, and main/offhand.
- **Fluid Animation Styles**:
  - `smooth`: Steady smoothstep travel arc.
  - `rotating`: Decelerating 3D spin into grid alignment.
  - `pop`: Cartoon squash and stretch pop-in.
  - `drop`: Gravity drop from above with impact settle.
  - `spiral`: Helical vortex spiral corkscrew.
  - `expand`: Miniature voxel burst overshoot.
  - `spring`: Energetic trampoline bounce.

---

## 💬 Client Commands

- `/blockplace tree`: Toggles cartoon tree punch shake animations on/off.
- `/blockplace tree on|off`: Explicitly enables or disables tree shaking.
- `/blockplace style <name>`: Switch placement animation style (`smooth, rotating, pop, drop, spiral, expand, spring, vanilla`).
- `/blockplace toggle`: Toggles block placement animations on/off.

---

## 📁 Project Structure

```
block-place/
├── build-logic/         # Shared multi-loader Gradle convention plugins
├── common/              # Platform-agnostic client logic, trajectories & mixins
│   └── src/main/java/com/blockplace/
│       ├── BlockPlaceCommon.java
│       ├── Constants.java
│       ├── client/
│       │   ├── animation/
│       │   │   ├── BlockFlyAnimation.java
│       │   │   ├── BlockPlacementManager.java
│       │   │   ├── FirstPersonHandHelper.java
│       │   │   └── tree/
│       │   │       ├── TreeBlockEntry.java
│       │   │       ├── TreeDetector.java
│       │   │       ├── TreeShakeAnimation.java
│       │   │       ├── TreeShakeManager.java
│       │   │       └── TreeStructure.java
│       │   └── config/
│       │       └── BlockPlaceConfig.java
│       └── mixin/
│           ├── BlockEntityRenderDispatcherMixin.java
│           ├── BlockItemMixin.java
│           ├── LevelRendererMixin.java
│           ├── MultiPlayerGameModeMixin.java
│           └── RenderSectionRegionMixin.java
├── fabric/              # Fabric loader module
│   └── src/main/java/com/blockplace/fabric/
│       ├── BlockPlaceFabric.java
│       └── client/BlockPlaceFabricClient.java
└── neoforge/            # NeoForge loader module
    └── src/main/java/com/blockplace/neoforge/
        └── BlockPlaceNeoForge.java
```

---

## 🛠️ VS Code Tasks

The project includes pre-configured tasks in [.vscode/tasks.json](file:///c:/Users/Gulab/IdeaProjects/block-place/.vscode/tasks.json) accessible via `Ctrl+Shift+P` -> **Tasks: Run Task** or `Ctrl+Shift+B`:

1. **`Minecraft: Build and Deploy (Fabric)`** *(Default Build Task - `Ctrl+Shift+B`)*:
   - Builds the Fabric JAR and automatically deploys it to:
     - Downloads: `C:\Users\Gulab\Downloads\`
     - MultiMC: `D:\MultiMC\instances\26.3\.minecraft\mods\`
2. **`Minecraft: Start Fabric Client`** *(Default Test/Run Task)*:
   - Starts the Fabric 26.3 development game client (`:fabric:runClient`).
3. **`Minecraft: Start Fabric Client (Debug JVM)`**:
   - Launches client with debug JVM port enabled for attaching IDE debugger.
4. **`Minecraft: Deploy JAR Only (Skip Build)`**:
   - Copies existing built JAR to Downloads and MultiMC without re-compiling.
5. **`Minecraft: Start NeoForge Client`**:
   - Starts the NeoForge 26.3 development game client.

---

## 🛠️ Building & Running

### Prerequisites
- **JDK 25** (installed and configured in `gradle.properties`)

### Gradle Commands

- **Build Fabric Mod JAR**:
  ```powershell
  .\gradlew.bat buildFabric
  ```
  *Output:* `fabric/build/libs/blockplace-fabric-26.3-1.0.0.jar`

- **Build NeoForge Mod JAR**:
  ```powershell
  .\gradlew.bat buildNeoForge
  ```
  *Output:* `neoforge/build/libs/blockplace-neoforge-26.3-1.0.0.jar`

- **Build All Loader JARs**:
  ```powershell
  .\gradlew.bat buildAllMods
  ```

---

## ⚙️ Configuration

Animation parameters can be adjusted via [BlockPlaceConfig.java](file:///c:/Users/Gulab/IdeaProjects/block-place/common/src/main/java/com/blockplace/client/config/BlockPlaceConfig.java) or persisted in `config/blockplace.json`:

| Option | Default | Description |
|---|---|---|
| `enableTreeShake` | `true` | Enables cartoon tree punch shake animations |
| `treeShakeIntensity` | `1.0f` | Multiplier for wobble amplitude and tilt |
| `treeShakeDurationTicks` | `14` (~700 ms) | Duration of the tree spring oscillation in game ticks |
| `enableTreeParticles` | `true` | Spawns falling leaves and punch impact wood dust |
| `enableTreeSound` | `true` | Plays wood impact and rustling foliage audio |
| `style` | `rotating` | Active block placement animation style |
| `durationTicks` | `6` (~300 ms) | Duration of placement flight animation |
| `startScale` | `0.28f` | Initial scale when leaving the player hand |
| `arcHeight` | `0.05f` | Parabolic peak height along trajectory |
| `firstPersonOnly` | `true` | Enable placement animations exclusively in first-person camera |
