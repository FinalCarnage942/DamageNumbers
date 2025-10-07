# DamageNumbers

**DamageNumbers** is a lightweight, high-performance Minecraft plugin that displays floating damage indicators when entities take damage. Designed for clarity, visual feedback, and performance, this plugin gives combat a more dynamic and satisfying feel — similar to what you'd find in RPGs and action games.

## Features

- **Floating Damage Indicators**: Shows customizable holographic numbers above entities when they take damage.  
- **Customizable Colors & Styles**: Configure colors for critical hits, healing, and normal damage.  
- **Performance Optimized**: Uses packet-based holograms and efficient caching to minimize lag.  
- **Supports All Damage Sources**: Works with melee, ranged, magic, explosions, and more.   
- **Configurable Lifespan**: Control how long the numbers stay on screen.  
- **Highly Customizable**: Modify text formats, color gradients, and behavior via `config.yml`.  

---

## Installation

1. **Requirements**:
   - Minecraft server running **Spigot** or **Paper 1.21+**

2. **Steps**:
   - Download the latest `DamageNumbers.jar` from the [Releases](https://github.com/FinalCarnage942/DamageNumbers/releases) page or build it from source.  
   - Place the file in your server’s `plugins` folder.  
   - Restart or reload your server.  
   - A new folder `plugins/DamageNumbers` will be created with the configuration file.

3. **Verify Installation**:
   - You should see `DamageNumbers enabled successfully!` in your console.  
   - Hit an entity to confirm damage numbers appear above it.

---

## Configuration

All plugin options are located in `config.yml`.  
Below is an example configuration:

```yaml
# Whether to show damage indicators
enabled: true

# Customize appearance
formats:
  normal: "&c&l%s DMG"          # Bold red for normal damage, e.g., "1.0 DMG"
  critical: "&e&l%s &4✧"        # Bold yellow with dark red sparkle for critical hits
  healing: "&a&l+%s ❤"          # Bold green with heart for healing

# Visibility & timing
display:
  visibility: damager
  view-range: 32.0
    x: 0.0  # No X offset for centered spawning
    y: 0.8  # Spawn 0.8 blocks above entity’s head for visibility
    z: 0.0  # No Z offset for centered spawning
  random-offset: 0.5  # ±0.5 blocks on X/Z axes to avoid overlap

# Filtering options
triggers:
  player-vs-mob: true
  mob-vs-player: false
  player-vs-player: true
  healing: true
  ignore-invisible: true
