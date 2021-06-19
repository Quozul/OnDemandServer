package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static dev.quozul.OnDemandServer.Main.serverController;

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
    private final HashMap<SocketAddress, ProxiedPlayer> startedBy;
    // TODO: Store the time it takes for each server to start
    // TODO: Display the remaining time for the starting server to start
    // TODO: Do something with the time stored
    private final HashMap<SocketAddress, Process> processes;
    private final HashMap<SocketAddress, ScheduledTask> stopTasks;
    private HashMap<SocketAddress, List<Long>> startingTime;

    private int stopDelay;
    private int maxServers;

    ServerController() {
        this.stopDelay = Main.configuration.getInt("stop_delay");
        this.maxServers = Main.configuration.getInt("max_servers");

        this.processes = new HashMap<>();
        this.startedBy = new HashMap<>();
        this.stopTasks = new HashMap<>();

        File file = new File(Main.plugin.getDataFolder(), "startingTime.ser");

        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
            this.startingTime = (HashMap<SocketAddress, List<Long>>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            this.startingTime = new HashMap<>();
        }
    }

    /**
     * Adds starting time to the history
     * @param serverInfo Target server
     * @param time Time took for the target server to start
     */
    public void addStartingTime(ServerInfo serverInfo, long time) {
        SocketAddress address = serverInfo.getSocketAddress();
        List<Long> times;

        if (this.startingTime.containsKey(address)) {
            times = this.startingTime.get(address);
        } else {
            times = new ArrayList<>();
        }

        times.add(time);

        this.startingTime.put(address, times);

        File file = new File(Main.plugin.getDataFolder(), "startingTime.ser");

        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
            oos.writeObject(this.startingTime);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get an average of all starting times
     * @param serverInfo Target server
     * @return Starting time average
     */
    public long getAverageStartingTime(ServerInfo serverInfo) {
        SocketAddress address = serverInfo.getSocketAddress();

        if (this.startingTime.containsKey(address)) {
            List<Long> times = this.startingTime.get(address);

            long average = 0;
            for (long time : times) average += time;

            return average / times.size();
        } else {
            return 0;
        }
    }

    /**
     * Reload the configuration variables
     */
    public void reloadConfig() {
        this.stopDelay = Main.configuration.getInt("stop_delay");
        this.maxServers = Main.configuration.getInt("max_servers");
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
     * Verify the server is not responding/running and removes it from list
     * @param serverInfo Target server
     * @return Server has been removed
     */
    public boolean safelyRemoveFromList(ServerInfo serverInfo) {
        int port = ((InetSocketAddress) serverInfo.getSocketAddress()).getPort();
        boolean portAvailable = isAvailable(port);

        SocketAddress address = serverInfo.getSocketAddress();
        boolean processRunning = processes.containsKey(address);

        Process process = serverController.getProcesses().get(address);

        // Remove process if not alive
        if (process != null && portAvailable && processRunning) {
            process.destroy();
            serverController.getProcesses().remove(address);
            System.out.println("Server not responding, removing it from list");
            return true;
        }

        return false;
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
    private LinkedHashMap<String, String> _getServerConfig(SocketAddress address) {
        List<?> servers = Main.configuration.getList("servers").stream()
                .filter((Predicate<Object>) o -> ((LinkedHashMap<String, String>) o).get("address").equals(address.toString()))
                .collect(Collectors.toList());

        if (servers.size() == 0) return null;

        return (LinkedHashMap<String, String>) servers.get(0);
    }
    public Function<SocketAddress, LinkedHashMap<String, String>> getServerConfig = Memoizer.memoize(this::_getServerConfig);

    /**
     * Tell if the server can be controlled by the proxy
     * @param serverInfo Target
     * @return The server can be controlled by the proxy
     */
    private boolean _canBeControlled(ServerInfo serverInfo) {
        return this.getServerConfig.apply(serverInfo.getSocketAddress()) != null;
    }
    public Function<ServerInfo, Boolean> canBeControlled = Memoizer.memoize(this::_canBeControlled);

    /**
     * Starts the given server
     * @param serverInfo Target
     * @param player Move the given player once the server is started
     */
    public char startServer(ServerInfo serverInfo, ProxiedPlayer player) {
        if (this.maxServers > 0 && this.maxServers <= this.processes.size()) {
            return 1;
        }

        SocketAddress address = serverInfo.getSocketAddress();
        LinkedHashMap<String, String> server = this.getServerConfig.apply(address);

        String command = server.get("command");

        try {
            Process p = Runtime.getRuntime().exec(command);
            this.processes.put(address, p);
            this.startedBy.put(address, player);

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
                try {
                    new Ping(serverInfo);
                } catch (StackOverflowError e) {
                    System.out.println("Server took too much time to start, stackoverflow error!");
                }
            });

            return 0;
        } catch (IOException ioException) {
            ioException.printStackTrace();

            return 2;
        }
    }

    /**
     * If the server is empty and there are no players
     * Create a Runnable to close it a minute later
     * @param serverInfo Target
     */
    public void requestServerStop(ServerInfo serverInfo) {
        // Check if players are on server
        int players = serverInfo.getPlayers().size();
        if (players > 0) return;

        SocketAddress address = serverInfo.getSocketAddress();
        boolean processRunning = this.processes.containsKey(address);
        if (!processRunning) return;

        this.createStopTask(serverInfo);
    }

    /**
     * Remove the stop task for a given server
     * @param serverInfo Target
     */
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
     * @deprecated Use createStopTask with explicit delay
     * @param serverInfo Target
     */
    public void createStopTask(ServerInfo serverInfo) {
        createStopTask(serverInfo, this.stopDelay);
    }

    /**
     * Remove the previous task and create a new one to stop the server after the given delay
     * @param serverInfo Target
     * @param delay Delay in minutes to stop the server
     */
    public void createStopTask(ServerInfo serverInfo, long delay) {
        SocketAddress address = serverInfo.getSocketAddress();

        // Stop after delay
        if (!this.stopTasks.containsKey(address)) {
            System.out.println("Stopping server in " + this.stopDelay + " minute");
            ScheduledTask task = ProxyServer.getInstance().getScheduler()
                    .schedule(Main.plugin, new Stop(serverInfo), delay, TimeUnit.MINUTES);

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
}
