package dev.quozul.OnDemandServer;

import dev.quozul.OnDemandServer.enums.ServerStatus;
import dev.quozul.OnDemandServer.enums.StartingStatus;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static dev.quozul.OnDemandServer.Main.serverController;

/**
 * Represents a server that can be controlled by the plugin
 */
public class ServerOnDemand {
    private final int port;
    private final String name;
    private final String command;
    private final ServerInfo serverInfo;
    private final SocketAddress address;

    private long lastStop;
    private Process process;
    private long lastStartup;
    private ServerStatus status;
    private ScheduledTask stopTask;
    private ProxiedPlayer requester;
    private List<Long> startingTimes;

    public ServerOnDemand(String name, String command, ServerInfo serverInfo) {
        this.name = name;
        this.command = command;
        this.serverInfo = serverInfo;

        this.status = ServerStatus.STOPPED;
        this.address = serverInfo.getSocketAddress();
        this.port = ((InetSocketAddress) this.address).getPort();
        this.startingTimes = new ArrayList<>();
        this.status = ServerStatus.UNKNOWN;

        this.lastStartup = -1;
        this.lastStop = -1;
    }

    /**
     * Adds starting time to the history
     * @param time Time took for the target server to start
     */
    public void addStartingTime(long time) {
        startingTimes.add(time);
    }

    /**
     * Get an average of all starting times
     * @return Starting time average, or -1 if no time
     */
    public long getAverageStartingTime() {
        int amount = this.startingTimes.size();
        if (amount == 0) return -1;

        long average = 0;
        for (long time : this.startingTimes) average += time;

        return average / amount;
    }

    /**
     * Starts the given server
     * @param timeout Time to wait for the server to start in milliseconds
     */
    public StartingStatus startServer(long timeout) {
        // TODO: Check if server can start

        if (status == ServerStatus.STARTED) return StartingStatus.ALREADY_STARTED;
        if (status != ServerStatus.STOPPED) return StartingStatus.ALREADY_STARTING;
        if (serverController.maxServers > 0 && serverController.maxServers <= serverController.getRunningServers()) return StartingStatus.TOO_MUCH_RUNNING;

        try {
            this.process = Runtime.getRuntime().exec(this.command);

            this.status = ServerStatus.STARTING;

            // Ping server until it is started
            ProxyServer.getInstance().getScheduler().runAsync(Main.plugin, () -> {
                if (timeout > 0) {
                    new Ping(this, timeout);
                } else {
                    new Ping(this);
                }
            });

            return StartingStatus.STARTING;
        } catch (IOException ioException) {
            ioException.printStackTrace();

            return StartingStatus.UNKNOWN;
        }
    }

    /**
     * Starts the given server
     * @param player Move the given player once the server is started
     * @param timeout Time to wait for the server to start in milliseconds
     */
    public StartingStatus startServer(ProxiedPlayer player, long timeout) {
        StartingStatus status = startServer(timeout);

        if (status == StartingStatus.STARTING) {
            this.requester = player;
        }

        return status;
    }

    /**
     * If the server is empty and there are no players
     * Create a Runnable to close it a minute later
     */
    public void requestServerStop() {
        // Check if players are on server
        int players = serverInfo.getPlayers().size();
        if (players > 0) return;

        this.status = ServerStatus.EMPTY;

        if (this.process == null) return;

        this.createStopTask();
    }

    /**
     * Remove the stop task for a given server
     */
    public void clearStopTask() {
        if (this.stopTask != null) {
            ProxyServer.getInstance().getScheduler().cancel(this.stopTask);
            System.out.println("Removed stop task " + this.stopTask.getId() + " for server " + this.name);
            this.stopTask = null;
        }
    }

    /**
     * Remove the previous task and create a new one to stop the server after the delay from config
     * @deprecated Use createStopTask with explicit delay
     */
    public void createStopTask() {
        this.createStopTask(serverController.stopDelay);
    }

    /**
     * Remove the previous task and create a new one to stop the server after the given delay
     * @param delay Delay in minutes to stop the server
     */
    public void createStopTask(long delay) {
        // Stop after delay
        System.out.println("Stopping server in " + Main.serverController.stopDelay + " minute");
        this.stopTask = ProxyServer.getInstance().getScheduler()
                .schedule(Main.plugin, new Stop(this), delay, TimeUnit.MINUTES);

        System.out.println("Created stop task " + this.stopTask.getId() + " for server " + this.address.toString());
    }

    public boolean safelyRemove() {
        int port = ((InetSocketAddress) serverInfo.getSocketAddress()).getPort();
        boolean portAvailable = ServerController.isAvailable(port);

        // Remove process if not alive
        if (process != null && portAvailable) {
            process.destroy();
            removeProcess();
            System.out.println("Server not responding, removing it from list");
            status = ServerStatus.STOPPED;
            return true;
        }

        return false;
    }

    // ACCESSORS

    public ProxiedPlayer getRequester() {
        return requester;
    }

    public Process getProcess() {
        return process;
    }

    public void removeProcess() {
        this.process = null;
    }

    public ServerStatus getStatus() {
        return status;
    }

    public void setStatus(ServerStatus status) {
        this.status = status;
    }

    public ServerInfo getServerInfo() {
        return serverInfo;
    }

    public String getName() {
        return name;
    }

    public SocketAddress getAddress() {
        return address;
    }

    public void setStartingTimes(List<Long> startingTimes) {
        this.startingTimes = startingTimes;
    }

    public List<Long> getStartingTimes() {
        return this.startingTimes;
    }

    public long getLastStop() {
        return lastStop;
    }

    public void setLastStop(long lastStop) {
        this.lastStop = lastStop;
    }

    public long getLastStartup() {
        return lastStartup;
    }

    public void setLastStartup(long lastStartup) {
        this.lastStartup = lastStartup;
    }
}
