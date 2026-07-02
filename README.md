# MineColonies Addons for Forge 1.20.1

This repository contains two custom addons built for a Forge 1.20.1 MineColonies-based modpack.

## Included Projects

### `traveleraddon`
A traveler trading addon for MineColonies.

Main features:
- Spawns a traveler near the Town Hall during day and night phases
- Opens an independent trading GUI on right click
- Supports colony-demand supply purchase
- Supports free market purchase for selected items
- Automatically delivers purchased items into the colony warehouse
- Includes compatibility safeguards for problematic Domum Ornamentum item data

### `auto_recipes`
An automation patch for MineColonies crafting workers.

Main features:
- Automatically unlocks compatible recipes for crafting buildings
- Supports configurable unlock level
- Adds automatic handling for Domum Ornamentum custom recipe workflows
- Uses persistent recovery for learned custom recipe input combinations
- Reduces repetitive manual recipe teaching

## Target Environment

Primary target environment:
- Minecraft `1.20.1`
- Forge `47.4.20`
- MineColonies `1.20.1-1.1.1197`
- Structurize `1.20.1-1.0.804`
- Domum Ornamentum `1.20.1-1.0.296`

## Repository Structure

```text
traveleraddon/
auto_recipes/
deps/
Local Build Requirements
Third-party dependency jars are intentionally not included in this repository.
Before building locally, place the following files into the deps/ directory:
minecolonies-1.20.1-1.1.1197.jar
structurize-1.20.1-1.0.804.jar
domum_ornamentum-1.20.1-1.0.296-universal.jar
Build
Use local Gradle 8.8 in each project directory:
gradle build
Notes
This repository contains source code only.
Current source is primarily aligned to MineColonies 1.20.1-1.1.1197.
If you adapt it to other MineColonies 1.20.1 builds, re-test API compatibility carefully.
Back up your world before testing these mods in an existing save.
License
This repository is currently published with an All Rights Reserved notice unless changed explicitly by the author.
Uploading code to GitHub does not automatically grant redistribution or commercial usage rights.
