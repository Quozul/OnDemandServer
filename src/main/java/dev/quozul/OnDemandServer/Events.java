package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;


public class Events implements Listener {
    public static ServerController serverController;

    Events() {
        serverController = new ServerController();
    }

    // TODO: Start lobby on ping

    @EventHandler
    public void onServerConnect(ServerConnectEvent e) {
        ServerInfo target = e.getTarget();

        if (serverController.isServerStarted(target)) {
            System.out.println("Connecting to " + target.getName() + "...");
            e.getRequest().setRetry(false);

            if (serverController.canBeControlled(target) && !serverController.isControlledByProxy(target)) {
                TextComponent textComponent = new TextComponent("This server is not controlled by the proxy, please inform the server administrator.");
                e.getPlayer().sendMessage(textComponent);
            }
        } else if (serverController.canBeControlled(target)) {
            System.out.println("Starting server " + target.getName() + "...");
            serverController.startServer(target, e.getPlayer());
            e.getRequest().setRetry(false);

            if (e.getPlayer().getServer() != null) {
                TextComponent textComponent = new TextComponent(Main.configuration.getString("redirect_message"));
                e.getPlayer().sendMessage(textComponent);
            } else {
                // Kick player
                TextComponent reason = new TextComponent();
                reason.setText(Main.configuration.getString("kick_message"));
                e.getPlayer().disconnect(reason);
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onServerDisconnect(ServerDisconnectEvent e) {
        // If server was started by the proxy
        if (serverController.canBeControlled(e.getTarget()))
            serverController.stopServer(e.getTarget());
    }
}
