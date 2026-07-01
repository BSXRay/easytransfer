# EasyTransfer

Velocity-Plugin zum automatisierten Verschieben von Pterodactyl-Game-Servern zwischen Nodes.

## Features

- **Server-Transfer** zwischen Pterodactyl-Nodes per Command
- **Spieler-Evakuierung** – alle Spieler werden vor dem Transfer auf einen Warteserver verschoben
- **Automatische Rückverbindung** – sobald der Server auf der Ziel-Node läuft, werden die Spieler reconnectet
- **Adress-Update** – die Velocity-Server-Registrierung wird dynamisch an die neue IP/Port angepasst
- **Panel-Status-Prüfung** – wartet bis das Panel den Server als `running` meldet
- **Config-gesteuert** – alle Einstellungen über `easytransfer.conf`
- **Tab-Completion** für Befehle und Transfer-Namen
- **Mehrere Profile** – beliebig viele Transfer-Einträge in der Config

## Voraussetzungen

- **Velocity 3.x** (Proxy)
- **Java 21**
- **Pterodactyl Panel** mit Application API (`ptlc_`-Key, Admin-Rechte)
- Optional: Client API (`ptla_`-Key) für zuverlässigere Status-Prüfung

## Installation

1. [JAR herunterladen](https://github.com/BSXRay/easytransfer/releases) oder selbst bauen
2. JAR in das `plugins/`-Verzeichnis von Velocity legen
3. Proxy neustarten
4. `plugins/EasyTransfer/easytransfer.conf` konfigurieren

## Konfiguration

```ini
panel_domain = "panel.test.net"
ptlc_api_key = "ptlc_abc123..."
waiting_server = "limbo"
alias = "easytransfer"

# Nodes (Alias = Pterodactyl-Node-ID)
Node1 = 1
Node2 = 2

# Transfer-Profile
creative-node2
server_id = "xxxxxxxx"
from = Node1
to = Node2
allocation_port = 25566

survival-node1
server_id = "yyyyyyyy"
from = Node2
to = Node1
allocation_port = 25566
```

### Optionen

| Feld | Beschreibung |
|------|--------------|
| `panel_domain` | Domain des Pterodactyl-Panels (ohne `https://`) |
| `ptlc_api_key` | Application API-Key (`ptlc_…`, Admin-Rechte erforderlich) |
| `waiting_server` | Velocity-Server-Name für Spieler während des Transfers (leer = deaktiviert) |
| `alias` | Hauptcommand-Alias (default: `easytransfer`) |
| `NodeX` | Node-Alias = Pterodactyl-Node-ID |
| Transfer-Name | Beliebiger Name für den Command |
| `server_id` | Pterodactyl-Server-ID |
| `from` | Quell-Node (Alias) |
| `to` | Ziel-Node (Alias) |
| `allocation_port` | Port auf der Ziel-Node |

## Befehle

| Befehl | Beschreibung | Permission |
|--------|--------------|------------|
| `/<alias> transfer <name>` | Transfer starten | `easytransfer.admin` |
| `/<alias> reload` | Config neuladen | `easytransfer.admin` |

## Ablauf eines Transfers

1. Spieler des Zielservers werden erfasst und auf `waiting_server` verschoben
2. Server wird über die Pterodactyl-API gestoppt
3. Warten bis der Server offline ist (Velocity-Ping)
4. Freie Allocation mit dem konfigurierten Port auf der Ziel-Node suchen
5. Server wird auf die Ziel-Node transferiert
6. Warten bis der Transfer abgeschlossen ist (API-Status-Polling)
7. Velocity-Server-Registrierung mit neuer IP/Port aktualisieren
8. Server wird gestartet
9. Warten bis Server online ist (Ping + Panel-Status)
10. Alle zuvor evakuierten Spieler werden reconnectet

## Bauen

```bash
mvn clean package
```

Die fertige JAR liegt in `target/easytransfer-1.0.jar`.

## Lizenz

MIT
