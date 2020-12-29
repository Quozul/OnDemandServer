package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;


public class Events implements Listener {
    private ServerController serverController;

    Events() {
        this.serverController = new ServerController();
    }

    // TODO: Start lobby on ping

    @EventHandler
    public void onServerConnect(ServerConnectEvent e) {
        ServerInfo target = e.getTarget();

        if (this.serverController.isServerStarted(target)) {
            System.out.println("Connecting to " + target.getName() + "...");
        } else {
            System.out.println("Starting server " + target.getName() + "...");
            this.serverController.startServer(target, e.getPlayer());
            e.getRequest().setRetry(false);

            if (e.getPlayer().isConnected()) {
                TextComponent textComponent = new TextComponent(Main.configuration.getString("redirect_message"));
                e.getPlayer().sendMessage(textComponent);
            }
        }
    }

    @EventHandler
    public void onServerDisconnect(ServerDisconnectEvent e) {
        this.serverController.stopServer(e.getTarget());
    }
}
