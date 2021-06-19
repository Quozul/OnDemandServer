package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;

import java.util.concurrent.TimeUnit;

public class Ping {
    private final static long MAX_STARTUP_TIME = 60000; // Time during which the server is pinged in ms
    private final static long PING_INTERVAL = 100;

    private final ServerOnDemand server;
    private final long timeout;
    private final long start;
    private long end;

    /**
     * @deprecated Give a timeout
     * @param server Target server
     */
    Ping(ServerOnDemand server) {
        this(server, MAX_STARTUP_TIME);
    }

    Ping(ServerOnDemand server, long timeout) {
        this.timeout = timeout;
        this.server = server;
        this.end = this.start = System.currentTimeMillis();
        ping();
    }

    private void ping() {
        // Ping timeout
        if (this.end - this.start > timeout) {
            System.out.println("Maximum ping tries reached, aborting.");
            ProxyServer.getInstance().getPluginManager().callEvent(new ServerStartFailEvent(server, this));
            return;
        }

        this.server.getServerInfo().ping((serverPing, throwable) -> {
            this.end = System.currentTimeMillis();

            if (serverPing != null) {
                ProxyServer.getInstance().getPluginManager().callEvent(new ServerStartedEvent(server, this));
            } else {
                // TODO: Use interval instead of recursion
                // Reduce ping amount
                Main.plugin.getProxy().getScheduler().schedule(Main.plugin, this::ping, PING_INTERVAL, TimeUnit.MILLISECONDS);
            }
        });
    }

    /**
     *
     * @return The time in millis that the server took
     */
    public long getTimeTook() {
        return this.end - this.start;
    }
}
