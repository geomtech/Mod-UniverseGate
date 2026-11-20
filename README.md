# UniverseGate

UniverseGate is a portal-focused mod for **Fabric** and **NeoForge** that introduces a new energy system, advanced machinery, and a mysterious dimension called **The Rift**. Build portal networks, generate power, travel between villages, manipulate the weather, harness Dark Energy and even clone the creatures you meet.

## Main Features

- **Advanced Portals**: Build 3x4 gateways fueled by a new energy system.
- **Portal Network**: Name your portals and dial any other gate in the network using the **Portal Controller** or the handheld **Mobile Portal Controller**.
- **Energy System**: Generate power with **Solar Panels** and **Combustion Generators**, store it in **Energy Condensers**, transport it via **Energy Conduits** and watch it with the **Energy Monitor**.
- **The Rift Dimension**: A hostile, void-like world filled with unique resources like **Kelo Wood**, **Rift Crystals**, and dangerous **Rift Shades**.
- **Village Portals**: Large villages get their own gate, and **Portal Engineer** villagers travel the network.
- **Meteorological Control**: Construct a multi-block machine to set the weather to clear, rain or thunder.
- **Dark Energy**: Refine Rift Crystals into **Dark Matter**, burn it in a **Dark Energy Generator**, and unlock the **Dark Dimension** destination.
- **ZPC**: Find rare **ZPC** power cells and plug them into a **ZPC Interface Controller** to feed huge amounts of energy into your network.
- **DNA & Cloning**: Extract a mob's **DNA** and clone it with the **Mob Cloner**.

## Getting Started

### 1. Power Generation
Before building a portal, you need energy.
1.  Craft **Solar Panels**.
2.  Place an **Energy Condenser**. This acts as a battery (20,000 FE) and network controller.
3.  Connect Solar Panels to the Condenser using **Energy Conduits**.
    *   *Note: Panels must see the sky and only work during the day.*
4.  Need power at night? A **Combustion Generator** burns any furnace fuel, or **Rift Crystals** (24,000 FE each), and outputs up to **480 FE/s**.
5.  Place an **Energy Monitor** on the network to check stored energy, total capacity and connected devices.

#### Energy from other mods (optional)
UniverseGate energy is measured in **FE** (Forge Energy) and speaks the standard energy APIs, so cables from other mods plug straight into it: **Stellaris**, and any mod using NeoForge energy (FE) or, on Fabric, Team Reborn Energy (1 FE = 1 E).
*   **Energy Condenser** and **Energy Conduit**: in and out. A conduit shares the energy of every condenser on its network, so a foreign cable can join the network anywhere.
*   **ZPC Interface**: out only (ZPCs are still recharged with dark energy only).
*   Up to **1,000 FE per tick** per connected block.
*   Dark energy keeps its own network and does not connect to other mods.
*   None of these mods is required: without them, UniverseGate works exactly the same.

### 2. Building a Portal
1.  Construct a **Portal Frame** with a **3x4 interior** (5x6 outer size).
2.  Place the **Portal Core** at the **bottom center** of the frame.
3.  Connect the Portal Core (or Frame) to your energy network using Conduits.
    *   *Opening a portal uses a dynamic cost: base **1200 FE**, then increases with distance and if dimensions differ.*
    *   *Maintaining the connection costs **120 FE/s** (more while the vortex is unstable).*
4.  Place a **Portal Controller** nearby (within 8 blocks).
5.  Optional: craft a **Mobile Portal Controller** to dial from your hand while standing near a portal.

### 3. Entering The Rift (Unstable Link)
To reach the Rift for the first time, you need to force an unstable connection:
1.  Craft a **Portal Lightning Rod**.
2.  Place it within range of your portal.
3.  Wait for a **Thunderstorm** (or create one with the Meteorological Machine, see below).
4.  When lightning strikes the rod, it will force the portal to open an unstable Rift link.
    *   *Warning: Unstable portals are volatile.*

## Village Portals

UniverseGate brings portals to the villages of the Overworld:
- When a player gets near a large village (at least **9 adult villagers**), a **Village Gate** is built 10 to 30 blocks from its center, with a **Natural Portal Controller**.
- Once you own a portal in the Overworld, a few extra village gates (up to 7) are also generated in distant villages (more than 1200 blocks from spawn), so the network has places to go.
- Opening a portal from a Natural Portal Controller is **free**.
- The Portal Controller is the workstation of a new villager profession, the **Portal Engineer**.
- **Engineer Expeditions**: every 4 to 5 in-game days, a Portal Engineer and 2 or 3 companions dial another portal, travel through it, then come back home. If you are near the destination when they arrive, they give you a thank-you gift (energy blocks, conduits, emeralds and other materials).
- Operators can manage expeditions with `/universegate engineer_event force|status|cancel`.

## The Meteorological Machine

Build the Meteorological Machine to change the weather.
1.  **Structure**:
    *   Place a **Meteorological Controller**.
    *   Next to it (within 1 block), stack **2 Meteorological Condensers** to form a tower.
    *   Place a **Meteorological Catalyst** on top of the tower and insert an **Energy Crystal** in it.
2.  **Power**: Connect the tower to your energy network with Energy Conduits, and add a **Parabola** on the same network. The Parabola draws **120 FE/s** while powered.
3.  **Charge**: Once everything is in place, the controller charges for **5 minutes**.
4.  **Activate**: Choose **Clear**, **Rain** or **Thunder**. Activation costs **2500 FE** and consumes the catalyst's Energy Crystal. The chosen weather lasts **10 minutes**.
    *   *Thunder is the easiest way to strike your Portal Lightning Rods.*
- Operators can skip the charge time with `/universegate weather_machine charge [pos]`.

## The Rift

The Rift is a dark, void-based dimension.
- **Resources**: Gather **Kelo Logs**, **Rift Meat** and **Rift Crystals**. Crystals drop from Rift Shades and grow as **Rift Crystal Clusters** on the void ground (mine them with a pickaxe, Fortune works, Silk Touch keeps the cluster).
- **Rift Cubes**: Huge white temples with a Subspatial Emitter. Their chests hold Rift Crystals, Energy Crystals, conduits, Dark Matter and, rarely, a charged ZPC.
- **Rift Guardian**: The first time a player comes near a cube's emitter, its Guardian wakes up: a giant Rift Shade with a boss bar, that blinds and slows on hit and calls Shades for help when wounded. It drops a **Rift Core**, the key ingredient of the ZPC.
- **Dangers**: Watch out for **Rift Shades** and **Rift Beasts**.
- **Return**: Portals in the Rift are **auto-powered** by the ambient energy of the dimension, allowing for an easy return trip if you have a frame.

## Advancements

The UniverseGate advancement tab follows the whole progression, from your first Solar Panel to the Rift Guardian, the ZPC and the Mob Cloner. Use it as a guide.

## Advanced Tech

### Dark Matter & Dark Energy
1.  **Rift Refiner**: Insert **Rift Crystals** to turn them into liquid **Dark Matter** (156 mB per crystal, 10,000 mB internal tank).
2.  **Fluid Pipes**: Carry Dark Matter from the Refiner to the generator (or place the generator directly against the Refiner).
3.  **Dark Energy Generator**: Burns **250 mB of Dark Matter per unit of Dark Energy**.
4.  **Dark Energy Conduits**: Link the generator to a portal (core or frame) or to other dark-energy machines.

### The Dark Dimension
Once a Portal Core has stored **400 units of Dark Energy** and stays linked to a running Dark Energy Generator, the **Dark Dimension** entry unlocks in its Portal Controller. It opens a link to the Rift without waiting for a lightning strike. Until then, it shows as `???` and cannot be dialed.

### ZPC (Zero Point Cell)
A **ZPC** stores up to **40,000,000,000 FE**.
- **Where to find one**:
    *   Each **Ancient City** holds one guaranteed ZPC, placed in the first loot chest you open there.
    *   Any vanilla loot chest has a very small chance (0.35%) to contain one.
    *   Found ZPCs are charged between 1% and 30%.
    *   A ZPC can also be crafted (Rift Crystals, Energy Crystals and a **Rift Core**), but it comes out **empty**.
- **Recharging**: Link a running **Dark Energy Generator** to the ZPC Interface Controller (adjacent or through Dark Energy Conduits). Each unit of Dark Energy adds **10,000,000 FE** to the emptiest installed ZPC.
- **ZPC Interface Controller**: Holds up to **3 ZPCs** and feeds them into the energy network it is connected to.
    *   Right-click with a ZPC to insert it.
    *   Sneak + right-click to engage the inserted ZPCs (short insertion animation). Only engaged ZPCs supply energy.
    *   Sneak + right-click again to eject a ZPC.
    *   Right-click to open its interface (charge, output, network link).

### DNA Extraction & Mob Cloning
1.  **DNA Extractor**: Aim at a mob (up to 14 blocks away) and hold right-click for **20 seconds**.
    *   Each extraction costs **64 Rift Crystals** from your inventory.
    *   The extraction is cancelled if the mob takes damage or leaves your sight.
2.  **DNA Sample**: Stores the mob type (and its custom name). A sample has **100 uses**, plus **50 per level of Unbreaking** (apply it with an anvil, max Unbreaking III).
3.  **Mob Cloner**: Place the **Mob Cloner** and a **Mob Cloner Controller** on the same energy network (Energy Conduits). The Cloner must also be linked to a running **Dark Energy Generator** with Dark Energy Conduits, and needs free space above it.
4.  **Cloning**: Insert the DNA into the Controller and start the process. After an **8 second** charge, the clone appears on top of the Cloner.
    *   *Cost: at least **1,000,000,000 FE** per clone, higher for monsters, high-health mobs and bosses (Wither, Ender Dragon, Warden, Elder Guardian). A ZPC is practically required.*

## Requirements

- Minecraft **1.21.1**
- **Fabric**: Fabric Loader and Fabric API
- **NeoForge**: NeoForge 21.1.x
- Optional: **Stellaris** (or any FE / Team Reborn Energy mod) to share energy with UniverseGate's network

## Installation

Each release ships two jars. Pick the one that matches your loader:

| Loader | Jar |
| --- | --- |
| Fabric | `universegate-fabric-<version>.jar` (requires **Fabric API**) |
| NeoForge | `universegate-neoforge-<version>.jar` |

1.  Download the jar for your loader.
2.  Place it in your `mods` folder.
3.  For multiplayer, install the **same loader's jar on both the client and the server** (a NeoForge server needs NeoForge clients).

## Building from source

The project is a multi-loader Gradle build:

- `common/`: all game code and assets, compiled against vanilla Minecraft (Mojang mappings), plus the JUnit tests.
- `fabric/`: Fabric entrypoints and Fabric API glue.
- `neoforge/`: NeoForge entrypoints and NeoForge glue.

Run `./gradlew build` (JDK 21+). The jars end up in `fabric/build/libs/` and `neoforge/build/libs/`. Dev servers: `./gradlew :fabric:runServer` or `./gradlew :neoforge:runServer`.

## License

CC0-1.0 (Public Domain).
