# peyajCustomDisc

Custom music disc plugin for Paper servers. Upload any audio file, get a playable disc.

## Features

- **Web Dashboard** — Upload and manage discs from a browser
- **Auto Conversion** — Accepts MP3, WAV, FLAC, M4A, MP4, WMA, AAC, WebM → OGG
- **Resource Pack Generation** — Java & Bedrock packs built automatically
- **Jukebox Integration** — Full hologram display with Now Playing info
- **Loop Mode** — Shift-Right-Click any jukebox to toggle looping
- **Region Music** — Assign discs to WorldGuard regions (auto-loops)
- **In-Game Catalog** — Browse and collect discs via GUI
- **Geyser Support** — Bedrock players hear the customdisc with holograms

## Requirements

| Dependency | Version |
|---|---|
| Paper | 26.x - 26.2 (latest) |
| Java | 25+ |
| FFmpeg | Required for non-OGG uploads |
| WorldGuard | Optional — enables region music |
| Geyser-Spigot | Optional — Bedrock hologram & custom disc support |

## Commands

| Command | Description |
|---|---|
| `/disc add <id> <url> <name> <author> [style]` | Download, convert, and add a custom disc (use underscores for spaces) |
| `/disc delete <id>` | Delete a custom disc (removes registry & deletes files) |
| `/disc give <player> <id>` | Give a custom disc |
| `/disc list` | List all discs |
| `/disc play <id>` | Play a disc (portable) |
| `/disc stop` | Stop all disc music |
| `/disc catalog` | Open the disc catalog GUI |
| `/disc region set <region> <disc>` | Assign music to a WorldGuard region |
| `/disc region remove <region>` | Remove region music |
| `/disc region list` | List region mappings |
| `/disc web` | Generate a one-time admin login link |
### In-Game Disc Addition Example

You can add custom music discs directly in-game. To represent spaces in the track name and author, use underscores (`_`):
```text
/disc add rushe https://mywebsite.com/audio/rushe.mp3 Rush_E Sheet_Music_Boss relic
```
This downloads `rushe.mp3` in the background, converts it to OGG using FFmpeg, registers the disc under the name **"Rush E"** by **"Sheet Music Boss"**, uses the **"relic"** style texture, and regenerates the resource pack automatically.

## Permissions

| Permission | Description |
|---|---|
| `pjcustomdisc.admin` | Access to all commands |
| `jukeboxweb.admin` | Access to web dashboard & give command |

## Configuration

> [!IMPORTANT]
> **Setup the configuration FIRST before creating or adding custom discs!**
> 1. Configure **`web-port`** to an open/allocated port on your server host (this is the internal port the web server binds to).
> 2. Configure **`public-url`** to point to your server's public IP/Domain and port. The plugin uses this URL to compile the resource pack download URL sent to players. If you add songs before setting this up, players will be sent resource pack downloads pointing to `localhost`, which will fail.

> [!WARNING]
> **Do NOT use your Minecraft server port (default `25565`) for `web-port`!**
> The web interface and download server run as a separate service on your host. You must assign/allocate a completely separate port for **`web-port`** (e.g. `8080`, `25566`, etc.). Using the Minecraft port will cause a `BindException` (Address already in use) and the plugin will fail to enable.



```yaml
# config.yml
web-port: 8080
public-url: "http://your-server-ip:8080"
haproxy-support: false

# Region music (managed via commands)
region-music:
  spawn: "my_disc_id"

# Jukebox settings
jukebox:
  volume: 1.0
  pitch: 1.0
  sound-category: "RECORDS"
  range: 64.0
  enable-particles: true
```