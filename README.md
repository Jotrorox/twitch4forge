# twicht4forge

A Minecraft Forge modpack managed with [packwiz](https://packwiz.infra.link/).

- Minecraft: 1.18.2
- Forge: 40.3.0

## Usage

```sh
# install packwiz (requires Go)
go install github.com/packwiz/packwiz@latest

# add mods
packwiz modrinth add <mod>
packwiz curseforge add <mod>

# after editing files by hand
packwiz refresh

# export
packwiz modrinth export     # .mrpack
packwiz curseforge export   # CurseForge zip
```
