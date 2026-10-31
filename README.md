# Tree Shake Animations

A client-side visual mod for **Minecraft 26.3** (Fabric, Forge & NeoForge) that adds delightful tree punch shake physics when punching or right-clicking trees and wooden logs.

---

## ✨ Features

### 🌴 Tree Shake Physics (Client-Side)
- **Topological Tree BFS Detection**:
  - Automatically identifies connected tree trunks (`#minecraft:logs`, `#minecraft:warped_stems`, `#minecraft:crimson_stems`, `mushroom_stem`, `bamboo_block`, `mangrove_roots`) and canopy foliage (`#minecraft:leaves`, `#minecraft:wart_blocks`).
  - Distinguishes natural trees and stems from man-made structures like log cabins or bridges.
  - **Wooden Blocks Only**: Punching or right-clicking wooden logs/stems triggers the shake; interacting with loose foliage does not.
- **Spring & Inverse Pendulum Physics**:
  - **Firm Ground Anchor**: The base log touching the ground stays 100% anchored with zero gap underneath.
  - The trunk bends with height-dependent curvature, swaying along the punch direction with organic 3D cross-wobble figure-8 physics.
  - **Squash & Stretch**: Compresses vertically on punch impact while bulging outwards, stretching tall on spring rebound.
- **Foliage Flutter & Leaf Particles**:
  - Independent high-frequency micro-rustle and breathing scale for each leaf block.
  - Punches cause leaf particles and wood splinters to burst out and float down with gravity.
  - Punch impact sound effects (`WOOD_HIT` + foliage rustle).
- **Interactive Triggers**:
  - **Punch (Left-Click)**: Delivers rhythmic spring impulses on each swing, keeping the tree in continuous wobble while mining.
  - **Interact (Right-Click)**: Right-clicking any wooden block shakes the entire tree.
- **Zero-Flicker Transition**:
  - Seamless chunk mesh handoff: chunk sections re-bake during settling without any visual popping.

---

## 💬 Client Commands & Configuration UI

- `/treeshake` or `/treeshake toggle`: Toggles tree shake animations on/off.
- `/treeshake on|off`: Explicitly enables or disables tree shaking.
- `/treeshake config` (or `/treeshake gui`, `/treeshake menu`): Opens the in-game Configuration Screen.

---

## 🛠️ In-Game Configuration Screen
Access via `/treeshake config`:
- **Toggle Tree Shake**: Enable/disable tree shake.
- **Wobble Intensity**: Adjust spring displacement from `0.20x` to `2.50x`.
- **Duration**: Adjust wobble length from `6 ticks (0.3s)` to `30 ticks (1.5s)`.
- **Tree Particles**: Toggle leaf burst & wood particles on/off.
- **Tree Sound**: Toggle impact audio effects on/off.
- **Reset to Defaults**: Restore optimal physics settings.
