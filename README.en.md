# Safety Signs

[日本語](README.md)

A Minecraft mod that adds decorative warning signs inspired by ISO 7010 and other safety signs and hazard symbols.
Put a "radioactive material" sign around your reactor or an "electricity" sign on your switchboard. The signs are purely decorative and have no gameplay effect.

> **This is an unofficial mod and is not affiliated with ISO, the United Nations or any standards body.** The pictograms were drawn for this mod with ISO 7010, the GHS (Globally Harmonized System of Classification and Labelling of Chemicals) and ISO 21482 as references.

![Sign list](doc/textures/preview.png)

## Download

- Get the jar for your Minecraft version and loader (`safetysigns-1.12.2-forge-<version>.jar`, `safetysigns-1.20.1-forge-<version>.jar` or `safetysigns-1.21.1-neoforge-<version>.jar`) from [GitHub Releases](https://github.com/zeusisgood/safetysigns/releases) and put it in your `mods` folder.
- **Required on both the client and the server.** Only the same minor version can connect (e.g. 1.1.x with 1.1.x).

## Supported versions

| Minecraft | Loader | Developed on | Notes |
|---|---|---|---|
| 1.12.2 | Forge | 14.23.5.2860 | Some features may not work on older Forge |
| 1.20.1 | Forge (47.1.32 or later) | 47.1.32 | |
| 1.21.1 | NeoForge | 21.1.264 | |

- No other mods are required. Signs, pictograms, controls and recipes are the same on all three versions.

## Signs

| Item | Size | Placement |
|---|---|---|
| Safety Sign (Wall, 1x1) | 1 block | A thin plate facing the player who placed it |
| Safety Sign (Wall, 2x2) | 2 wide × 2 tall | The clicked spot becomes the bottom-left; it extends right and up. All 4 spaces must be free |
| Safety Sign (Pole) | 3 tall | Placed on a floor (top face of a block). The bottom two are the pole, the top is a 1x1 sign |

- Signs face one of 4 directions, toward the player who placed them.
- If any part of a 2x2 or pole sign is broken, the whole sign is removed and drops a single item.
- The plate is cut out to the shape of the pictogram (triangle, circle or diamond).

## Controls

| Action | Result |
|---|---|
| Right-click with a sign item | Places it, keeping the item's pictogram and settings |
| Right-click a placed sign (empty hand, or holding anything but a sign item) | Opens the pictogram picker |
| Sneak + right-click a placed sign while holding a sign item | Copies the sign's pictogram and settings to the held item (type and size may differ) |
| Pick block (middle click) | Gives the item with the pictogram (creative) |
| Break the sign | Drops an item that keeps the pictogram and settings |
| Put a single sign item in the crafting grid | Gives a blank item of the same kind |

### Pictogram picker

- Use the tabs at the top (Warning, Prohibition, Mandatory action, Safe condition, Fire equipment, GHS, Other) and pick a pictogram from the grid. **It is applied to the sign right away** (no confirm button).
- The search box filters by code (`W003` etc.), English name, Japanese name and keywords, across all categories.
- "Reset to blank" removes the pictogram.
- Each sign has the following settings (also applied right away).

| Setting | Meaning | Default |
|---|---|---|
| Cutout | ON: plate in the shape of the pictogram / OFF: square plate (light metal around the pictogram) | ON |
| Back | Silver: plain metal / Pattern: the same pictogram on the back, readable from behind | Silver |
| Night glow | ON: only the front pictogram is drawn at full brightness (does not light the area) / OFF: uses the surrounding light | ON |

## Pictograms

| Code | Category | Name |
|---|---|---|
| W001 | Warning | General warning sign |
| W002 | Warning | Explosive material |
| W003 | Warning | Radioactive material or ionizing radiation |
| W004 | Warning | Laser beam |
| W005 | Warning | Non-ionizing radiation |
| W006 | Warning | Magnetic field |
| W009 | Warning | Biological hazard |
| W010 | Warning | Low temperature/freezing conditions |
| W012 | Warning | Electricity |
| W016 | Warning | Toxic material |
| W017 | Warning | Hot surface |
| W018 | Warning | Automatic start-up |
| W019 | Warning | Crushing |
| W021 | Warning | Flammable material |
| W023 | Warning | Corrosive substance |
| W026 | Warning | Battery charging |
| W029 | Warning | Pressurized cylinder |
| W042 | Warning | Arc flash |
| W079 | Warning | Hot content |
| W080 | Warning | Hot steam |
| M001 | Mandatory action | General mandatory action sign |
| M002 | Mandatory action | Refer to instruction manual/booklet |
| M003 | Mandatory action | Wear ear protection |
| M004 | Mandatory action | Wear eye protection |
| M008 | Mandatory action | Wear safety footwear |
| M009 | Mandatory action | Wear protective gloves |
| M010 | Mandatory action | Wear protective clothing |
| M013 | Mandatory action | Wear a face shield |
| M014 | Mandatory action | Wear head protection |
| M016 | Mandatory action | Wear a mask |
| M017 | Mandatory action | Wear respiratory protection |
| P001 | Prohibition | General prohibition sign |
| P002 | Prohibition | No smoking |
| P003 | Prohibition | No open flame; Fire, open ignition source and smoking prohibited |
| P004 | Prohibition | No thoroughfare |
| P010 | Prohibition | Do not touch |
| P011 | Prohibition | Do not extinguish with water |
| P013 | Prohibition | No activated mobile phones |
| E001 | Safe condition | Emergency exit (left hand) |
| E002 | Safe condition | Emergency exit (right hand) |
| E003 | Safe condition | First aid |
| E011 | Safe condition | Eyewash station |
| E012 | Safe condition | Safety shower |
| E020 | Safe condition | Emergency stop button |
| F001 | Fire equipment | Fire extinguisher |
| F005 | Fire equipment | Fire alarm call point |
| GHS01 | GHS | Exploding bomb |
| GHS02 | GHS | Flame |
| GHS03 | GHS | Flame over circle |
| GHS04 | GHS | Gas cylinder |
| GHS05 | GHS | Corrosion |
| GHS06 | GHS | Skull and crossbones |
| GHS07 | GHS | Exclamation mark |
| GHS08 | GHS | Health hazard |
| GHS09 | GHS | Environment |
| ISO 21482 | Other | Ionizing radiation (large sources) |

Resource packs can add or replace pictograms (see [doc/resourcepack.md](doc/resourcepack.md), in Japanese). Players without the pack see a "?" pictogram.

## Recipes

All recipes are shapeless.

| Result | Ingredients | Count |
|---|---|---|
| Safety Sign (Wall, 1x1) | 1 Sign (wooden) + 1 Iron Ingot | 1 |
| Safety Sign (Wall, 2x2) | 4 Signs (wooden) + 1 Iron Ingot | 1 |
| Safety Sign (Pole) | 3 Signs (wooden) + 1 Iron Ingot | 1 |
| Reset to blank | A single sign item | 1 blank item of the same kind |

- Iron ingots from other mods also work (the ore dictionary entry `ingotIron` on 1.12.2, the tag `forge:ingots/iron` on 1.20.1, the tag `c:ingots/iron` on 1.21.1). On 1.12.2 only, the ore dictionary entry `plateIron` (iron plates) also works.
- The wooden sign is the vanilla sign (any wood on 1.20.1 and 1.21.1).
- Crafted signs are blank with default settings (cutout ON, back silver, night glow ON).

## License

MIT (code and textures). See [LICENSE](LICENSE).

## For developers

- Changelog: [CHANGELOG.md](CHANGELOG.md) (in Japanese)
- Design and docs are in `doc/` (in Japanese).
- Build: `./gradlew build` in each version's folder (JDK 8 for 1.12.2, JDK 17 for 1.20.1, JDK 21 for 1.21.1) → `mc<version>/build/libs/safetysigns-<Minecraft version>-<loader>-<version>.jar`
