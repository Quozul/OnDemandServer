# On demand server
This is a BungeeCord plugin that can execute a command when a player tries to connect to a server that isn't running.

## Use case
You have a dedicated server or a VPS and you want to run multiple Minecraft servers on it while keeping the resource usage as low as possible.

## Usage
1. Drop this plugin into your BungeeCord plugin's folder.
2. Start then stop the proxy once.
3. Open the plugin's config file and enter your servers address with their commands.
- Do not put a command that requires user interaction, the process will wait forever!

## Current features/functioning
- Detect if a server is running on the server's port on the local machine.
- Start the servers if not running.
- Stops the servers if no players are on after a given delay.

## Defects
- If the proxy crashes, the Minecraft servers are still running but can't be controlled by the user or the proxy.
- Not tested on distant servers.

## TODO
- Fix orphan servers. (not happening on Linux)
- Test distant servers.
- Add load balancing feature.
- Commands:
    - create a server on-the-fly and add it to config file.
        - set an automatic deletion.
        - Permissions:
            - maximum servers per player.
            - maximum auto deletion delay per player.
            - maximum ram per server per player.
    - remove a server from config file (only the servers created on-the-fly).
    - start a server.
    - stop a running server.
- Web interface to manage servers (plugins, worlds, start/stop, create, monitor, ...).
- Handle when a player disconnects after trying to join a server.
