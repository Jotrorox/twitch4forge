# twicht4forge

A Minecraft **1.18.2** Forge modpack, managed with [packwiz](https://packwiz.infra.link/),
that ships with **Twitch Chat Widget**: a client-side mod that shows a Twitch
channel's live chat as a HUD overlay in game.

| | |
|---|---|
| Minecraft | 1.18.2 |
| Forge | 40.3.0 |
| Pack version | 0.1.0 |
| Twitch Chat Widget | 1.1.0 |

## Twitch Chat Widget

Read a Twitch channel's chat without alt-tabbing. The mod joins the chat
anonymously and read-only over TLS, so you don't need a Twitch account or
OAuth token.

### Features

- Movable, resizable chat box: drag it where you want it, drag the corner to resize
- Three styles: **Panel**, **Lines** (looks like vanilla chat) and **Minimal** (messages only)
- Three themes: **Dark**, **Light** and **Twitch**
- Messages fade after a set time (15 s by default, or never)
- Shows the full history while the Minecraft chat is open
- Can hide itself while chat is quiet, and can hide `!commands`
- Optional timestamps, name colours, header, text shadow and notices
- Everything is set in game. Hover any option to see what it does

### Usage

1. Press **K** (rebindable under *Controls*), or click *Config* for the mod in the Mods list.
2. Type a channel name.
3. Drag the outlined box to move the chat, and drag its corner to resize it.

The settings screen has three tabs:

| Tab | Settings |
|---|---|
| **General** | Channel, on/off, reset to defaults |
| **Look** | Style, theme, text size, spacing, background opacity, header, text shadow, name colours |
| **Messages** | Fade delay, hide when quiet, history while chat is open, timestamps, notices, hide `!commands` |

A second key binding, unbound by default, shows and hides the widget.
Settings are saved to `config/twitchchat.json`.

## Repository layout

```
.
├── pack.toml        # packwiz pack definition (MC + Forge versions)
├── index.toml       # packwiz file index
├── mods/            # mod jars included in the pack
├── config/          # config files shipped with the pack
└── twitchchat/      # source of the Twitch Chat Widget mod (Gradle / ForgeGradle)
```

## Working on the pack

You need [packwiz](https://packwiz.infra.link/installation/), which you can
install with Go:

```sh
go install github.com/packwiz/packwiz@latest
```

```sh
# Add mods
packwiz modrinth add <mod>
packwiz curseforge add <mod>

# Update the index after editing files by hand
packwiz refresh

# Export
packwiz modrinth export     # .mrpack
packwiz curseforge export   # CurseForge .zip
```

## Building the mod

You need JDK 17.

```sh
cd twitchchat
./gradlew build
cp build/libs/twitchchat-1.1.0.jar ../mods/
cd .. && packwiz refresh
```

If you bump the mod version, delete the old jar from `mods/` before running
`packwiz refresh`.

## License

[BSD 2-Clause](LICENSE). This covers the code and files in this repository.
Third-party mods added to the pack keep their own licenses.
