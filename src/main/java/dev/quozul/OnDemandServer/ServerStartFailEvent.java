package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.plugin.Event;

public class ServerStartFailEvent extends Event {
    private final ServerOnDemand server;
    private final Ping ping;

    ServerStartFailEvent(ServerOnDemand server, Ping ping) {
        this.server = server;
        this.ping = ping;
    }

    public ServerOnDemand getServer() {
        return this.server;
    }

    public Ping getPing() {
        return this.ping;
    }
}
