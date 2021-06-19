package dev.quozul.OnDemandServer;

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
    private final List<Long> startingTimes;

    private Process process;
    private ServerStatus status;
    private ScheduledTask stopTask;
    private ProxiedPlayer requester;

    public ServerOnDemand(String name, String command, ServerInfo serverInfo) {
        this.name = name;
        this.command = command;
        this.serverInfo = serverInfo;

        this.status = ServerStatus.STOPPED;
        this.address = serverInfo.getSocketAddress();
        this.port = ((InetSocketAddress) this.address).getPort();
        this.startingTimes = new ArrayList<>();
        this.status = ServerStatus.UNKNOWN;
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
     * @param player Move the given player once the server is started
     */
    public char startServer(ProxiedPlayer player, long timeout) {
        // TODO: Check if server can start

        if (status != ServerStatus.STOPPED) return 3;
        if (serverController.maxServers > 0 && serverController.maxServers <= serverController.getRunningServers()) return 1;

        try {
            this.process = Runtime.getRuntime().exec(this.command);
            this.requester = player;

            // Debug
            /*ProxyServer.getInstance().getScheduler().runAsync(Main.plugin, () -> {
                // Debug
                try {
                    String s;
                    BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

                    // read the output from the command
                    while ((s = stdInput.readLine()) != null) {
                        System.out.println(s);
                    }
                    System.out.println("Log ended");
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            });*/

            // Ping server until it is started
            ProxyServer.getInstance().getScheduler().runAsync(Main.plugin, () -> {
                if (timeout > 0) {
                    new Ping(this, timeout);
                } else {
                    new Ping(this);
                }
            });

            this.status = ServerStatus.STARTING;

            return 0;
        } catch (IOException ioException) {
            ioException.printStackTrace();

            return 2;
        }
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
}
