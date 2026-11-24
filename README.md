# UniverseGate

UniverseGate is a portal-focused mod for NeoForge 1.21.1 that introduces a new energy system, advanced machinery, and a mysterious dimension called **The Rift**. Build complex portal networks, generate power, manipulate the weather, and explore the void.

## Main Features

- **Advanced Portals**: Build 3x4 gateways fueled by a new energy system.
- **Energy System**: Generate power with **Solar Panels**, store it in **Energy Condensers**, and transport it via **Conduits**.
- **The Rift Dimension**: A hostile, void-like world filled with unique resources like **Kelo Wood**, **Rift Crystals**, and dangerous **Rift Shades**.
- **Meteorological Control**: Construct a massive multi-block machine to control the weather.
- **Acid Rain**: Corrosive rain damages exposed creatures and armor, destroys crops, turns dirt into Acidified Dirt, and brings no-drop Rift creatures through the fog.
- **Acid Chemistry**: Craft charged acid batteries for generators and neon lamps, poison-tipped acid arrows, phosphate fertilizer with multiple growth attempts, and nitro-acid explosives stronger than TNT.
- **Rift Refining**: Process Rift materials into **Dark Matter** for advanced technology.
- **Portal Network**: Name your portals and dial any other gate in the network using the **Portal Controller**.

## Getting Started

### 1. Power Generation
Before building a portal, you need energy.
1.  Craft **Solar Panels**.
2.  Place an **Energy Condenser**. This acts as a battery and network controller.
3.  Connect Solar Panels to the Condenser using **Energy Conduits**.
    *   *Note: Panels must see the sky and only work during the day.*

### 2. Building a Portal
1.  Construct a **Portal Frame** with a **3x4 interior** (5x6 outer size).
2.  Place the **Portal Core** at the **bottom center** of the frame.
3.  Connect the Portal Core (or Frame) to your energy network using Conduits.
    *   *Opening a portal uses a dynamic cost: base **1200 EU**, then increases with distance and if dimensions differ.*
    *   *Maintaining the connection costs **120 EU/s**.*
4.  Place a **Portal Controller** (formerly Keyboard) nearby (within 8 blocks).

### 3. Entering The Rift (Unstable Link)
To reach the Rift for the first time, you need to force an unstable connection:
1.  Craft a **Portal Lightning Rod**.
2.  Place it within range of your portal.
3.  Wait for a **Thunderstorm** (or force one, see below).
4.  When lightning strikes the rod, it will force the portal to open an unstable Rift link.
    *   *Warning: Unstable portals are volatile.*

### 4. The Meteorological Machine
Tired of waiting for rain? Build the Meteorological Controller to change the weather.
1.  **Structure**: Requires a specific multi-block setup (Parabola, Controller, Catalyst).
2.  **Power**: Connect it to your energy network (requires **2500 EU** per operation).
3.  **Catalyst**: Insert a **Meteorological Catalyst** crystal.
4.  **Activate**: Select "Thunder" to charge your Lightning Rods instantly, or choose **Acid Rain** for a corrosive storm.
    *   *Acid Rain destroys exposed crops, corrodes armor, hurts exposed entities except Rift creatures, and turns exposed dirt into Acidified Dirt. Rift creatures that appear in the fog drop nothing.*
    *   *Leave an empty Cauldron under the open sky during Acid Rain. Once it is full, use an empty Bucket to collect the acid rain.*
    *   *Each new natural rain in the Overworld has a **10% chance** of becoming acidic. The sky turns green and fog gradually thickens for **30 seconds** before any acid effects begin. Ending the rain cancels the episode.*
    *   *Episodes, including those requested by the controller, require a **three Minecraft day gap** after the previous episode ends (**72,000 ticks / one hour of play time**). Episode progress and the cooldown are saved with the world; an ongoing rain is not rolled again after a reload.*
5.  **Acid-powered technology**: Craft an **Acid Battery** and insert it into a Combustion Generator to supply your energy network. Use a battery on an **Acid Neon Lamp** to power it; the lamp returns its remaining charge as a battery if broken.
6.  **Acid products**: Combine acid with arrows for poison-tipped shots, bone meal for phosphate fertilizer that attempts four growth boosts, or TNT and gunpowder for a throwable explosive with greater blast power than TNT.

## Acid Arsenal and Mutations

- **Laser Rifle**: Right-click to fire an armor-piercing beam up to 64 blocks for 18 damage. Each shot draws **1,000 EU** from Acid Batteries in your inventory. Refill depleted batteries at a crafting table with an Acid Rain Bucket; the empty bucket is returned.
- **Laser Assault Horse**: Use a **Laser Cannon Harness** on your own adult, tamed horse. While riding it, the Laser Rifle fires a **40-damage cannon** up to 96 blocks with a small impact explosion, for **2,000 EU** per shot. At 8 and 16 shots the horse starts to slow under corrosion; at **24 mounted shots**, it becomes a **Skeleton Horse**. The saddle, owner, name, harness and rider are retained, and horse armor is dropped. The skeleton mount can keep firing. Sneak-right-click an equipped mount with an empty hand to remove its harness.
- **Rusted Golems**: Use an Acid Rain Bucket on an Iron Golem, or expose it to Acid Rain, to rust it. Rusted golems have **60 health**, lower damage and slower movement. They drop **Oxidized Iron**, worth **8,000 EU** per ingot in the Combustion Generator.
- **Acidified Creepers**: Use an Acid Rain Bucket on a creeper to make it your companion. It follows you, resists Acid Rain and no longer chooses players as hostile targets. Aim at a block or entity and right-click the **Creeper Commander** to send your nearby companions to detonate there. Sneak-right-click to recall unignited creepers. Only the owner can issue orders; player targeting respects server PvP and team settings.
- **Acid Beacon**: Crafted with **four acid buckets** and a **Nether Star**. Insert batteries and leave a clear view of the sky. It grants **Speed II, Haste II, Strength II, Resistance I and Regeneration I** within **64 blocks**, at **80 EU/s**, without a mineral pyramid. Empty-hand interaction displays its stored energy.

## The Rift

The Rift is a dark, void-based dimension.
- **Resources**: Gather **Kelo Logs**, **Rift Crystals**, and **Rift Meat**.
- **Dangers**: Watch out for **Rift Shades** and **Rift Beasts**.
- **Return**: Portals in the Rift are **auto-powered** by the ambient energy of the dimension, allowing for an easy return trip if you have a frame.

## Advanced Tech

- **Rift Refiner**: Use **Rift Crystals** and a Bucket to craft **Dark Matter**.
- **Dark Energy**: (Coming Soon) Use Dark Matter to power advanced **Dark Energy Generators**.
- **Fluid Pipes**: Transport fluids like Dark Matter between machines.

## Requirements

- Minecraft **1.21.1**
- NeoForge 21.1.x
- Forgified Fabric API 0.116.15 or newer for 1.21.1

## Installation

1.  Download the `.jar` file.
2.  Place it in your `mods` folder.
3.  Ensure **Forgified Fabric API** is installed alongside NeoForge.
4.  For multiplayer, install on **both client and server**.

## Development checks

Run `./gradlew runGameTestServer` for the Minecraft integration tests, or `./gradlew build` to compile and generate the installable mod. GameTest classes live in `src/gametest` and are excluded from the production JAR.

The tests cover laser energy and damage, horse conversion and equipment recovery, rusted-golem loot and fuel, creeper ownership and orders, beacon operation, energy recovery, and natural acid rain scheduling, warning, synchronization and persistence.

## License

CC0-1.0 (Public Domain).
