package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.config.Configuration;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.util.*;

public class ServerController {
    /**
     * Check if the port is in use or not
     * @param portNr Port number
     * @return Port available
     */
    public static boolean isAvailable(int portNr) {
        boolean portFree;
        try (ServerSocket ignored = new ServerSocket(portNr)) {
            portFree = true;
        } catch (IOException e) {
            portFree = false;
        }
        return portFree;
    }

    // Store which player requested the server to start
    // TODO: Display the remaining time for the starting server to start
    private HashMap<SocketAddress, List<Long>> startingTime;

    private final HashMap<ServerInfo, ServerOnDemand> servers;

    public int stopDelay;
    public int maxServers;

    ServerController() {
        this.stopDelay = Main.configuration.getInt("stop_delay");
        this.maxServers = Main.configuration.getInt("max_servers");

        File file = new File(Main.plugin.getDataFolder(), "startingTime.ser");

        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
            this.startingTime = (HashMap<SocketAddress, List<Long>>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            this.startingTime = new HashMap<>();
        }

        // Load server informations
        Map<String, ServerInfo> servers = Main.plugin.getProxy().getServers();
        Configuration serverConfig = Main.configuration.getSection("servers");
        this.servers = new HashMap<>();

        for (Map.Entry<String, ServerInfo> entry : servers.entrySet()) {
            String name = entry.getKey();
            String command = serverConfig.getString(name);
            ServerInfo serverInfo = entry.getValue();

            ServerOnDemand server = new ServerOnDemand(name, command, serverInfo);
            this.servers.put(serverInfo, server);

            if (isServerStarted(serverInfo)) {
                server.setStatus(ServerStatus.STANDALONE);
            } else {
                server.setStatus(ServerStatus.STOPPED);
            }
        }
    }

    public int getRunningServers() {
        int sum = 0;
        for (Map.Entry<ServerInfo, ServerOnDemand> entry : servers.entrySet()) {
            if (entry.getValue().getStatus() == ServerStatus.RUNNING) sum++;
        }
        return sum;
    }

    public ServerOnDemand getServer(ServerInfo serverInfo) {
        return this.servers.get(serverInfo);
    }

    /**
     * Adds starting time to the history
     */
    public void saveStartingTimes() {
        File file = new File(Main.plugin.getDataFolder(), "startingTime.ser");

        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
            oos.writeObject(this.startingTime);
        } catch (IOException e) {
            e.printStackTrace();
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
     * @deprecated Use ServerOnDemand.getStatus
     * @param serverInfo Target
     * @return Server running
     */
    public boolean isServerStarted(ServerInfo serverInfo) {
        int port = ((InetSocketAddress) serverInfo.getSocketAddress()).getPort();
        boolean serverRunningOnPort = !isAvailable(port);

        boolean processRunning = this.getServer(serverInfo).getProcess() != null;

        return serverRunningOnPort || processRunning;
    }

    /**
     * Tell if the server can be controlled by the proxy (aka. does the plugin has a configuration for the server)
     * @param serverInfo Target
     * @return The server can be controlled by the proxy
     */
    public boolean canBeControlled(ServerInfo serverInfo) {
        return this.servers.containsKey(serverInfo) && this.getServer(serverInfo).getStatus() != ServerStatus.STANDALONE;
    }

    /**
     * Check if the given server is controlled by the proxy server
     * @deprecated
     * @param serverInfo Target
     * @return Is controlled by proxy
     */
    public boolean isControlledByProxy(ServerInfo serverInfo) {
        return this.getServer(serverInfo).getStatus() == ServerStatus.RUNNING;
    }
}
