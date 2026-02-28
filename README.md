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
- **Developer API** — Play, stop, and query discs programmatically
- **Geyser Support** — Bedrock players see separate holograms at correct heights

## Requirements

| Dependency | Version |
|---|---|
| Paper | 1.21+ |
| Java | 21+ |
| FFmpeg | Required for non-OGG uploads |
| WorldGuard | Optional — enables region music |
| Geyser-Spigot | Optional — Bedrock hologram support |

## Commands

| Command | Description |
|---|---|
| `/disc give <player> <id>` | Give a custom disc |
| `/disc list` | List all discs |
| `/disc play <id>` | Play a disc (portable) |
| `/disc stop` | Stop all disc music |
| `/disc catalog` | Open the disc catalog GUI |
| `/disc region set <region> <disc>` | Assign music to a WorldGuard region |
| `/disc region remove <region>` | Remove region music |
| `/disc region list` | List region mappings |
| `/disc web` | Generate a one-time admin login link |
| `/disc reload` | Reload config & restart web server |

## Permissions

| Permission | Description |
|---|---|
| `pjcustomdisc.admin` | Access to all commands |
| `jukeboxweb.admin` | Access to web dashboard & give command |

## Configuration

```yaml
# config.yml
web-port: 8080
public-url: "http://your-server-ip:8080"
haproxy-support: false

# Region music (managed via commands)
region-music:
  spawn: "my_disc_id"
```

## API

Access the API from other plugins:

```kotlin
val api = Bukkit.getServicesManager()
    .getRegistration(PeyajDiscAPI::class.java)?.provider

api?.playDisc(player, "disc_id")
api?.stopDisc(player, "disc_id")
api?.createDiscItem("disc_id")
```

```java
PeyajDiscAPI api = Bukkit.getServicesManager()
    .getRegistration(PeyajDiscAPI.class).getProvider();

api.playDisc(player, "disc_id");
api.stopDisc(player, "disc_id");
api.createDiscItem("disc_id");
```

## License

All rights reserved.
