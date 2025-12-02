# 🏰 TerritoryBeacons - Immersive Land Claiming (1.21+)

> **Forget command-based claiming. Protect your land using powered Beacons!**
> TerritoryBeacons adds an RPG-style protection system where players place beacons to establish, upgrade, and maintain their territories.
> **Includes Pl3xMap support and an Influence Decay system.**

![Java](https://img.shields.io/badge/Java-17-orange) ![Spigot](https://img.shields.io/badge/API-1.21-yellow) ![License](https://img.shields.io/badge/License-MIT-blue)

---

## 💎 Why TerritoryBeacons?
Most protection plugins break immersion. **TerritoryBeacons** integrates seamlessly into survival gameplay. Players must craft and place Beacons to claim land, use **Diamonds/Money** to upgrade their radius, and log in regularly to maintain "Influence" preventing decay.

### ✨ Key Features

* **📍 Beacon-Based Claims**
    * Place a Beacon block to instantly claim the surrounding area.
    * **Visual Borders:** Automatically places temporary torches to show boundaries.
    * **Upgradable Tiers:** Upgrade your territory from Tier 1 (16 blocks) to Tier 8 (150 blocks) using GUI.

* **📉 Influence & Decay System**
    * Territories require active owners. If a player is offline for too long (configurable), the territory loses **Influence**.
    * At 0% influence, the protection is removed, keeping your map clean of abandoned claims.

* **🗺️ Pl3xMap Integration**
    * Automatically draws territory borders on your web map.
    * Shows owner name, tier, and influence status directly on the map.

* **🛡️ Full Management GUI**
    * Manage **Trusted Players**, toggle **PvP/Mob Spawning**, and buy **Beacon Effects** (Speed, Haste, Regen) directly from a menu.

---

## ⚙️ Configuration
Customize costs, tiers, and decay timers in `config.yml`.

```yaml
# Example: Territory Tiers
tiers:
  tier-1:
    radius: 16
    upgrade-cost: 0
  tier-2:
    radius: 32
    upgrade-cost: 20 # 20 Diamonds/Money to upgrade

# Decay Settings
decay-time-hours: 160 # Start decaying after ~7 days offline
