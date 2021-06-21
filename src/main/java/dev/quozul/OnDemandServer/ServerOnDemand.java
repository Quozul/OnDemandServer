package dev.quozul.OnDemandServer;

import dev.quozul.OnDemandServer.enums.ServerStatus;
import dev.quozul.OnDemandServer.enums.StartingStatus;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.config.Configuration;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static dev.quozul.OnDemandServer.Main.serverController;

/**
 * Represents a server that can be controlled by the plugin
 */
public class ServerOnDemand {
    protected final String name;
    protected final ServerInfo serverInfo;
    protected final SocketAddress address;

    protected int port;
    protected long lastStop;
    protected Process process;
    protected long lastStartup;
    protected ServerStatus status;
    protected Configuration config;
    protected ScheduledTask stopTask;
    protected ProcessBuilder builder;
    protected ProxiedPlayer requester;
    protected List<Long> startingTimes;

    public ServerOnDemand(String name, ServerInfo serverInfo) {
        this.name = name;
        this.serverInfo = serverInfo;

        this.status = ServerStatus.STOPPED;
        this.address = serverInfo.getSocketAddress();
        this.port = ((InetSocketAddress) this.address).getPort();
        this.startingTimes = new ArrayList<>();
        this.status = ServerStatus.UNKNOWN;

        this.lastStartup = -1;
        this.lastStop = -1;
    }

    public ServerOnDemand(String name, Configuration config, ServerInfo serverInfo) {
        this(name, serverInfo);

        this.config = config;

        if (!config.contains("directory")) {
            throw new RuntimeException("Invalid configuration, missing directory path");
        } else if (!config.contains("jar_file")) {
            throw new RuntimeException("Invalid configuration, missing jar file name");
        }

        this.builder = new ProcessBuilder();
        File directory = new File(config.getString("directory"));
        this.builder.directory(directory);

        // Build command
        List<String> arguments = buildCommand(config);

        builder.command(arguments);
    }

    protected List<String> buildCommand(Configuration config) {
        List<String> arguments = new ArrayList<>();
        // java -Xmx8G -Xms2G -jar -DIReallyKnowWhatIAmDoingISwear paper.jar nogui
        arguments.add("java");

        // JVM arguments
        if (config.contains("maximum_memory"))
            arguments.add("-Xmx" + config.getInt("maximum_memory") + "M");
        if (config.contains("minimum_memory"))
            arguments.add("-Xms" + config.getInt("minimum_memory") + "M");
        if (config.contains("jvm_arguments"))
            arguments.add(config.getString("jvm_arguments"));

        arguments.add("-jar");

        if (config.contains("IReallyKnowWhatIAmDoingISwear") && config.getBoolean("IReallyKnowWhatIAmDoingISwear"))
            arguments.add("-DIReallyKnowWhatIAmDoingISwear");

        arguments.add(config.getString("jar_file")); // Jar filename

        if (this.port >= 0) {
            arguments.add("--port");
            arguments.add(String.valueOf(this.port));
        }

        arguments.add("nogui");

        return arguments;
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

        if (status == ServerStatus.NOT_CONFIGURED) return StartingStatus.NOT_CONFIGURED;
        if (status == ServerStatus.STARTED) return StartingStatus.ALREADY_STARTED;
        if (status != ServerStatus.STOPPED) return StartingStatus.ALREADY_STARTING;
        if (serverController.maxServers > 0 && serverController.maxServers <= serverController.getRunningServers()) return StartingStatus.TOO_MUCH_RUNNING;

        try {
            this.process = builder.start();

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
        System.out.println("Stopping server " + this.address.toString() + " in " + delay + " minute");

        this.stopTask = ProxyServer.getInstance().getScheduler()
                .schedule(Main.plugin, new Stop(this), delay, TimeUnit.MINUTES);
    }

    public boolean safelyRemove() {
        int port = ((InetSocketAddress) serverInfo.getSocketAddress()).getPort();
        boolean portAvailable = ServerController.isAvailable(port);

        // Remove process if not alive
        if (process != null && portAvailable) {
            process.destroy();
            removeProcess();
            Main.plugin.getLogger().log(Level.WARNING, "Server not responding, removing it from list");
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

    public Configuration getConfiguration() {
        return this.config;
    }
}
