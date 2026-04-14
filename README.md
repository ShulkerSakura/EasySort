# EasySort

A Minecraft Fabric client-side mod that provides one-click inventory sorting functionality.

## Features

- **One-Click Sorting**: Quickly sort your backpack and container items with a hotkey
- **Smart Sorting**: Sorts items following the creative inventory tab order
- **Auto Merge**: Automatically merges stackable items of the same type
- **Wide Compatibility**: Supports chests, shulker boxes, player inventory, and more

## Usage

### Hotkey

| Key | Function |
|-----|----------|
| Default `R` key | Sort items in the currently opened container |

The hotkey can be changed in Game Settings > Controls > Key Binds.

### Supported Containers

- Player inventory (Survival/Creative mode)
- Chests, Trapped Chests
- Shulker Boxes
- Furnaces, Smokers, Blast Furnaces
- Hoppers, Droppers, Dispensers
- Brewing Stands
- And other standard containers

## Requirements

- Minecraft: 1.26.2+
- Fabric Loader: 0.19.1+
- Fabric API: Any version
- Java: 25+

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/)
2. Download and install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Place this mod's `.jar` file into the `.minecraft/mods` folder

## Building

```bash
./gradlew build
```

The built mod file will be located in the `build/libs/` directory.

## License

This project is open source under the [MIT License](LICENSE).

---

**Note**: This is a client-side only mod, no server-side installation required.
