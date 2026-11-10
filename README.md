# twitch4forge

A client-side Minecraft Forge mod that shows a Twitch channel's live chat as a
HUD overlay in game, so you can read chat without alt-tabbing.

The mod joins the chat anonymously and read-only over TLS, so you don't need a
Twitch account or OAuth token.

| | |
|---|---|
| Minecraft | 1.18.2 |
| Forge | 40 or newer (built against 40.3.0) |
| Side | Client only |
| Mod version | 1.1.0 |

## Features

- Movable, resizable chat box: drag it where you want it, drag the corner to resize
- Three styles: **Panel**, **Lines** (looks like vanilla chat) and **Minimal** (messages only)
- Three themes: **Dark**, **Light** and **Twitch**
- Messages fade after a set time (15 s by default, or never)
- Shows the full history while the Minecraft chat is open
- Can hide itself while chat is quiet, and can hide `!commands`
- Optional timestamps, name colours, header, text shadow and notices
- Everything is set in game. Hover any option to see what it does

## Installation

1. Install [Forge](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.18.2.html) for Minecraft 1.18.2.
2. [Build the jar](#building) and put `twitch4forge-1.1.0.jar` in your `mods` folder.

The mod is client-side only, so servers don't need it.

## Usage

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
Settings are saved to `config/twitch4forge.json`.

## Building

You need JDK 17, and Gradle itself has to run on it. If your default Java is
newer, point `JAVA_HOME` at a JDK 17 first.

```sh
./gradlew build
```

The jar is written to `build/libs/twitch4forge-1.1.0.jar`.

To start a development client with the mod loaded:

```sh
./gradlew runClient
```

## License

[BSD 2-Clause](LICENSE)
