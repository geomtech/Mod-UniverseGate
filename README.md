# UniverseGate

UniverseGate is a portal-focused mod for Fabric that introduces a new energy system, advanced machinery, and a mysterious dimension called **The Rift**. Build complex portal networks, generate power, manipulate the weather, and explore the void.

## Main Features

- **Advanced Portals**: Build 3x4 gateways fueled by a new energy system.
- **Energy System**: Generate power with **Solar Panels**, store it in **Energy Condensers**, and transport it via **Conduits**.
- **The Rift Dimension**: A hostile, void-like world filled with unique resources like **Kelo Wood**, **Rift Crystals**, and dangerous **Rift Shades**.
- **Meteorological Control**: Construct a massive multi-block machine to control the weather.
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
4.  **Activate**: Select "Thunder" to charge your Lightning Rods instantly.

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
- Fabric Loader
- Fabric API

## Installation

1.  Download the `.jar` file.
2.  Place it in your `mods` folder.
3.  Ensure **Fabric API** is installed.
4.  For multiplayer, install on **both client and server**.

## License

CC0-1.0 (Public Domain).
