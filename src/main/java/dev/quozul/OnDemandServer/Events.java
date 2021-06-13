package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;


import java.net.SocketAddress;

import static dev.quozul.OnDemandServer.Main.serverController;


public class Events implements Listener {
    // TODO: Start default server on ping
    // TODO: Display time remaining for server to start in MOTD per player

    private final Main plugin;

    Events(Main pl) {
        this.plugin = pl;
    }

    @EventHandler
    public void onServerConnect(ServerConnectEvent e) {
        ServerInfo target = e.getTarget();


        if (serverController.isServerStarted(target)) {
            System.out.println("Connecting to " + target.getName() + "...");
            e.getRequest().setRetry(false);
            // TODO: If the server is not responding (ie. it crashed), fix it (remove it from list and restart it)
        } else if (serverController.canBeControlled.apply(target)) {
            char isStarting = serverController.startServer(target, e.getPlayer());
            TextComponent message = new TextComponent();

            switch (isStarting) {
                case 0:
                    System.out.println("Starting server " + target.getName() + "...");

                    // TODO: Display an estimated time for the server's startup time

                    // If player is already connected
                    if (e.getPlayer().getServer() != null) {
                        message.setText(Main.configuration.getString("redirect_message"));
                        e.getPlayer().sendMessage(message);
                    } else {
                        // If plays is connecting, kick player
                        message.setText(Main.configuration.getString("kick_message"));
                        // TODO: Handle when the network has fallback servers
                        e.getPlayer().disconnect(message);
                        e.setCancelled(true);
                    }

                    break;

                case 1:
                    System.out.println("Too much servers are already running!");
                    message.setText(Main.configuration.getString("too_many_running"));

                    if (e.getPlayer().isConnected()) e.getPlayer().sendMessage(message);
                    else e.getPlayer().disconnect(message);

                    break;

                default:
                    System.out.println("Something went wrong when starting the server!");
                    message.setText("Something went wrong when starting the server!");

                    if (e.getPlayer().isConnected()) e.getPlayer().sendMessage(message);
                    else e.getPlayer().disconnect(message);

                    break;

            }

            e.getRequest().setRetry(false);
            e.setCancelled(true);
        } else {
            System.out.println("Plugin is not handling connection.");
        }
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent e) {
        ServerInfo target = e.getServer().getInfo();
        // Clear server shutdown tasks when player connects to server
        serverController.clearStopTask(target);

        if (serverController.canBeControlled.apply(target) && !serverController.isControlledByProxy(target)) {
            TextComponent textComponent = new TextComponent("This server is not controlled by the proxy, please inform the server administrator.");
            e.getPlayer().sendMessage(textComponent);
        }
    }

    @EventHandler
    public void onServerDisconnect(ServerDisconnectEvent e) {
        // If server was started by the proxy
        if (serverController.canBeControlled.apply(e.getTarget())) {
            // TODO: Check if server is still responding, if not, remove it from list (ie. admin stopped the server)
            serverController.requestServerStop(e.getTarget());
        }
    }

    @EventHandler
    public void onServerStarted(ServerStartedEvent e) {
        ServerInfo serverInfo = e.getServerInfo();
        SocketAddress address = serverInfo.getSocketAddress();
        long time = e.getPing().getTimeTook();
        ProxiedPlayer player = serverController.getStartedBy().get(address);

        // TODO: Save time took for the server to start

        System.out.println("Server " + address.toString() + " requested by " + player.getName() + " started in " + time / 1000 + "s");
        player.sendMessage(new TextComponent(String.format("Server started in %.2fs.", time / 1000.)));

        // Move player to started server
        // TODO: Handle when player is no longer connected (ie. stop the server)
        if (player.isConnected()) {
            player.connect(e.getServerInfo());
        }

        serverController.createStopTask(serverInfo);
    }
}
