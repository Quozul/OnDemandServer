package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;

public class Ping {
    private final ServerInfo serverInfo;
    public long start, end;

    Ping(ServerInfo serverInfo) throws StackOverflowError {
        this.serverInfo = serverInfo;
        this.start = System.currentTimeMillis();
        ping();
    }

    private void ping() throws StackOverflowError {
        this.serverInfo.ping((serverPing, throwable) -> {
            this.end = System.currentTimeMillis();

            if (serverPing != null) {
                ProxyServer.getInstance().getPluginManager().callEvent(new ServerStartedEvent(serverInfo, this));
            } else {
                // TODO: Use interval instead of recursion
                this.ping();
            }
        });
    }

    /**
     *
     * @return The time in millis that the server took
     */
    long getTimeTook() {
        return this.end - this.start;
    }
}
