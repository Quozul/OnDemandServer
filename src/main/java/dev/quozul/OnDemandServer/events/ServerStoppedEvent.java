package dev.quozul.OnDemandServer.events;

import dev.quozul.OnDemandServer.ServerOnDemand;
import net.md_5.bungee.api.plugin.Event;

public class ServerStoppedEvent extends Event {
    private final ServerOnDemand server;

    public ServerStoppedEvent(ServerOnDemand server) {
        this.server = server;
    }

    public ServerOnDemand getServer() {
        return this.server;
    }
}
