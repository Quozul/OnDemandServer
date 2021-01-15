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

    @EventHandler
    public void onServerConnect(ServerConnectEvent e) {
        ServerInfo target = e.getTarget();

        if (serverController.isServerStarted(target)) {
            System.out.println("Connecting to " + target.getName() + "...");
            e.getRequest().setRetry(false);
        } else if (serverController.canBeControlled.apply(target)) {
            char isStarting = serverController.startServer(target, e.getPlayer());
            TextComponent message = new TextComponent();

            switch (isStarting) {
                case 0:
                    System.out.println("Starting server " + target.getName() + "...");

                    // If player is already connected
                    if (e.getPlayer().getServer() != null) {
                        message.setText(Main.configuration.getString("redirect_message"));
                        e.getPlayer().sendMessage(message);
                    } else {
                        // If plays is connecting, kick player
                        message.setText(Main.configuration.getString("kick_message"));
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
            serverController.requestServerStop(e.getTarget());
        }
    }

    @EventHandler
    public void onServerStarted(ServerStartedEvent e) {
        ServerInfo serverInfo = e.getServerInfo();
        SocketAddress address = serverInfo.getSocketAddress();
        long time = e.getPing().getTimeTook();
        ProxiedPlayer player = serverController.getStartedBy().get(address);

        System.out.println("Server " + address.toString() + " requested by " + player.getName() + " started in " + time / 1000 + "s");

        // Move player to started server
        if (player.isConnected()) {
            player.connect(e.getServerInfo());
        }

        serverController.createStopTask(serverInfo);
    }
}
