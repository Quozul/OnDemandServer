package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;

import java.util.concurrent.atomic.AtomicBoolean;

public class Ping {
    public AtomicBoolean isStarted;
    private ServerInfo serverInfo;
    public long start, end;

    Ping(ServerInfo serverInfo) throws StackOverflowError {
        this.isStarted = new AtomicBoolean(false);
        this.serverInfo = serverInfo;
        this.start = System.currentTimeMillis();
        ping();
    }

    private void ping() throws StackOverflowError {
        this.serverInfo.ping((serverPing, throwable) -> {
            this.end = System.currentTimeMillis();

            if (serverPing != null) {
                this.isStarted.set(true);
                ProxyServer.getInstance().getPluginManager().callEvent(new ServerStartedEvent(serverInfo, this));
            } else {
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
