package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class Events implements Listener {
    private ServerController serverController;

    Events() {
        this.serverController = new ServerController();
    }

    @EventHandler
    public void onServerConnect(ServerConnectEvent e) {
        ServerInfo target = e.getTarget();

        if (this.serverController.isServerStarted(target)) {
            System.out.println("Connecting to " + target.getName() + "...");
        } else {
            System.out.println("Starting server " + target.getName() + "...");
            this.serverController.startServer(target, e.getPlayer());
            e.getRequest().setRetry(false);
        }
    }

    @EventHandler
    public void onServerDisconnect(ServerDisconnectEvent e) {
        this.serverController.stopServer(e.getTarget());
    }
}
