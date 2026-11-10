# Changelog

All notable changes to twitch4forge are listed here. Versions follow
[Semantic Versioning](https://semver.org/).

## 1.1.0

First public release.

### Added

- Settings screen with **General**, **Look** and **Messages** tabs, tooltips on
  every option, and a panel that docks away from the widget
- Three styles: Panel, Lines and Minimal
- Three themes: Dark, Light and Twitch
- Messages fade after a set time (15 s by default, or never)
- Option to hide the widget while chat is quiet
- Option to show the full history while the Minecraft chat is open
- Timestamps, message spacing and text shadow options
- Filters for Twitch notices and `!commands`
- Key binding to show and hide the widget (unbound by default)

### Changed

- The mod is now called twitch4forge (was "Twitch Chat Widget"). The mod id is
  `twitch4forge` and settings are saved to `config/twitch4forge.json`, so
  settings from `config/twitchchat.json` are not carried over.

## 1.0.0

Initial version, not published.

- Shows a Twitch channel's live chat in a movable, resizable HUD widget
- Joins chat anonymously and read-only over TLS, with automatic reconnect
- Channel, position, size and look are set in an in-game screen (default key: K)
