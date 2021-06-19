package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Event;

public class ServerStartFailEvent extends Event {
    private final ServerInfo serverInfo;
    private final Ping ping;

    ServerStartFailEvent(ServerInfo serverInfo, Ping ping) {
        this.serverInfo = serverInfo;
        this.ping = ping;
    }

    public ServerInfo getServerInfo() {
        return this.serverInfo;
    }

    public Ping getPing() {
        return this.ping;
    }
}
