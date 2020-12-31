package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ServerController {
    /**
     * Check if the port is in use or not
     * @param portNr Port number
     * @return Port available
     */
    private boolean isAvailable(int portNr) {
        boolean portFree;
        try (ServerSocket ignored = new ServerSocket(portNr)) {
            portFree = true;
        } catch (IOException e) {
            portFree = false;
        }
        return portFree;
    }

    // Store which player requested the server to start
    private HashMap<SocketAddress, ProxiedPlayer> startedBy;
    // TODO: Store the time it takes for each server to start
    // TODO: Display the remaining time for the starting server to start
    // TODO: Do something with the time stored
    private HashMap<SocketAddress, Process> processes;
    private HashMap<SocketAddress, ScheduledTask> stopTasks;
    private final int stopDelay;
    private final String stop_command;

    ServerController() {
        this.stopDelay = Main.configuration.getInt("stop_delay");
        this.stop_command = Main.configuration.getString("stop_command");
        this.processes = new HashMap<>();
        this.startedBy = new HashMap<>();
        this.stopTasks = new HashMap<>();
    }

    /**
     * Verify if a process is running on the target's port and if the process is registered by the plugin
     * @param serverInfo Target
     * @return Server running
     */
    public boolean isServerStarted(ServerInfo serverInfo) {
        int port = ((InetSocketAddress) serverInfo.getSocketAddress()).getPort();
        boolean serverRunningOnPort = !isAvailable(port);

        SocketAddress address = serverInfo.getSocketAddress();
        boolean processRunning = processes.containsKey(address);

        return serverRunningOnPort || processRunning;
    }

    /**
     * Check if the given server is controlled by the proxy server
     * @param serverInfo Target
     * @return Is controlled by proxy
     */
    public boolean isControlledByProxy(ServerInfo serverInfo) {
        SocketAddress address = serverInfo.getSocketAddress();
        return processes.containsKey(address);
    }

    /**
     * Return the LinkedHashMap of the server from the config file
     * @param address Target's address
     * @return Server's information
     */
    public LinkedHashMap<String, String> getServerConfig(SocketAddress address) {
        List<?> servers = Main.configuration.getList("servers").stream()
                .filter((Predicate<Object>) o -> ((LinkedHashMap<String, String>) o).get("address").equals(address.toString()))
                .collect(Collectors.toList());

        if (servers.size() == 0) return null;

        return (LinkedHashMap<String, String>) servers.get(0);
    }

    /**
     * Return the LinkedHashMap of the server from the config file
     * @param serverInfo Target
     * @return Server's information
     */
    public LinkedHashMap<String, String> getServerConfig(ServerInfo serverInfo) {
        SocketAddress address = serverInfo.getSocketAddress();
        return this.getServerConfig(address);
    }

    /**
     * Tell if the server can be controlled by the proxy
     * @param serverInfo Target
     * @return The server can be controlled by the proxy
     */
    public boolean canBeControlled(ServerInfo serverInfo) {
        return this.getServerConfig(serverInfo) != null;
    }

    /**
     * Starts the given server
     * @param serverInfo Target
     * @param player Move the given player once the server is started
     */
    public void startServer(ServerInfo serverInfo, ProxiedPlayer player) {
        SocketAddress address = serverInfo.getSocketAddress();
        LinkedHashMap<String, String> server = getServerConfig(address);

        String command = server.get("command");

        try {
            Process p = Runtime.getRuntime().exec(command);
            this.processes.put(address, p);
            this.startedBy.put(address, player);

            // Ping server until it is started
            ProxyServer.getInstance().getScheduler().runAsync(Main.plugin, () -> {
                try {
                    Ping ping = new Ping(serverInfo);
                } catch (StackOverflowError e) {
                    System.out.println("Server took too much time to start, stackoverflow error!");
                }
            });
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    /**
     * If the server is empty and there are no players
     * Create a Runnable to close it a minute later
     * @param serverInfo Target
     */
    public void stopServer(ServerInfo serverInfo) {
        // Check if players are on server
        int players = serverInfo.getPlayers().size();
        if (players > 0) return;

        SocketAddress address = serverInfo.getSocketAddress();
        boolean processRunning = this.processes.containsKey(address);
        if (!processRunning) return;

        this.createStopTask(serverInfo);
    }

    public void clearStopTask(ServerInfo serverInfo) {
        SocketAddress address = serverInfo.getSocketAddress();

        ScheduledTask task = this.stopTasks.get(address);
        if (task != null) {
            ProxyServer.getInstance().getScheduler().cancel(task);
            this.stopTasks.remove(address);
            System.out.println("Removed stop task " + task.getId() + " for server " + address.toString());
        }
    }

    /**
     * Remove the previous task and create a new one to stop the server after the delay from config
     * @param serverInfo Target
     */
    public void createStopTask(ServerInfo serverInfo) {
        SocketAddress address = serverInfo.getSocketAddress();

        // Stop after delay
        if (!this.stopTasks.containsKey(address)) {
            System.out.println("Stopping server in " + this.stopDelay + " minute");
            ScheduledTask task = ProxyServer.getInstance().getScheduler()
                    .schedule(Main.plugin, new Stop(serverInfo), this.stopDelay, TimeUnit.MINUTES);

            this.stopTasks.put(address, task);

            System.out.println("Created stop task " + task.getId() + " for server " + address.toString());
        }
    }

    HashMap<SocketAddress, Process> getProcesses() {
        return this.processes;
    }

    public HashMap<SocketAddress, ProxiedPlayer> getStartedBy() {
        return this.startedBy;
    }

    public HashMap<SocketAddress, ScheduledTask> getStopTasks() {
        return stopTasks;
    }

    public int getStopDelay() {
        return stopDelay;
    }
}
