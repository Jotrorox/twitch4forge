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

## Twitch Chat Widget mod

`twitchchat/` holds the source of a client-side Forge mod that shows a Twitch
channel's live chat in a HUD widget. The built jar is in `mods/`.

In game, press **K** (rebindable under Controls) or use the Config button in the
Mods list. Type a channel name, drag the box to move it, and drag its corner to
resize it. Settings are saved to `config/twitchchat.json`.

```sh
# rebuild (needs JDK 17 to run Gradle)
cd twitchchat && ./gradlew build
cp build/libs/twitchchat-1.0.0.jar ../mods/   # then: packwiz refresh
```
