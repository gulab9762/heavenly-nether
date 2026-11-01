# Unharvest

A reverse-farming indie-game inspired simulation mod for **Minecraft 26.3** (Fabric, Forge & NeoForge).

Drain the life from endless wheat fields, unleash decaying blights, deploy hungry locust swarms, and upgrade your plagues across an authentic graph skill tree!

---

## 🌾 Gameplay Overview

- **Plague Arena World Preset**:
  - Create a new world and select **Unharvest (Wheat Field)** under world presets.
  - Automatically generates an organic, non-squared wheat arena (~9,000+ crops) at world spawn surrounded by peaceful terrain.
  - World difficulty is enforced as **Peaceful**.

- **Withering Scythe**:
  - Automatically provided to the player when entering the Unharvest world.
  - **Left-Click Wheat**: Instantly withers wheat into a **Wither Rose**, converting the soil beneath into corrupted coarse dirt and soul soil, earning **Blight Essence**.
  - **Right-Click**: Deploys the placeable **Locust Swarm** when unlocked and ready!
  - **Shift + Right-Click** or **[U] Key**: Opens the Plague Upgrade Tree.

- **Graph-Style Upgrade Tree ([U])**:
  - **Center Tree (Scythe)**: Scythe Swing Speed, Wither Radius AoE, and Essence Multipliers leading up to the **Grim Reaper Apex**.
  - **Left Branch (Crimson Spores)**: Fungal Genesis contagion, chain-reaction infection chances, and expanding cascade radius leading up to **Primal Rot**.
  - **Right Branch (Dark-Moss Swarms)**: Placeable Locust Swarm ability, cooldown reductions, devour speed, and swarm area leading up to the **Pestilence Horde**.

- **Placeable Locust Swarm ([R])**:
  - Press **[R]** to target the ground and unleash a voracious, buzzing swarm of locusts that aggressively consumes crops in a designated zone for 7 seconds.
  - Tracked via a HUD ability card at the bottom-right of the screen with a dynamic cooldown sweep overlay and countdown timer.

---

## 💬 Commands

- `/unharvest` or `/unharvest upgrades`: Opens the Plague Upgrade Tree screen.
- `/unharvest scythe`: Gives the player a Withering Scythe if lost.
- `/unharvest regenerate`: Re-clears the airspace of any structure obstructions and regenerates the field.
- `/unharvest reset`: Resets all upgrade progress, currencies, and stats to zero.
