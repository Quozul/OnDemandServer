package dev.quozul.OnDemandServer;

import dev.quozul.OnDemandServer.enums.ServerStatus;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.config.Configuration;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
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

    private final HashMap<ServerInfo, ServerOnDemand> servers;

    public int stopDelay;
    public int maxServers;

    ServerController() {
        this.stopDelay = Main.config.getInt("stop_delay");
        this.maxServers = Main.config.getInt("max_servers");

        File file = new File(Main.plugin.getDataFolder(), "startingTime.ser");
        HashMap<String, List<Long>> startingTime;

        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
            startingTime = (HashMap<String, List<Long>>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            startingTime = new HashMap<>();
        }

        // Load server information
        Map<String, ServerInfo> servers = Main.plugin.getProxy().getServers();
        Configuration serverConfig = Main.config.getSection("servers");
        this.servers = new HashMap<>();

        for (Map.Entry<String, ServerInfo> entry : servers.entrySet()) {
            String name = entry.getKey();
            String command = serverConfig.getString(name);
            ServerInfo serverInfo = entry.getValue();

            ServerOnDemand server = new ServerOnDemand(name, command, serverInfo);
            this.servers.put(serverInfo, server);

            // Load starting times
            if (startingTime.containsKey(name)) {
                server.setStartingTimes(startingTime.get(name));
            }

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

    /**
     * Get a server
     * @param serverInfo Target server
     * @return The server or null if not found
     */
    public ServerOnDemand getServer(ServerInfo serverInfo) {
        if (!this.servers.containsKey(serverInfo)) return null;
        return this.servers.get(serverInfo);
    }

    /**
     * Adds starting time to the history
     */
    public void saveStartingTimes() {
        HashMap<String, List<Long>> startingTime = new HashMap<>();

        for (Map.Entry<ServerInfo, ServerOnDemand> entry : servers.entrySet()) {
            startingTime.put(entry.getValue().getName(), entry.getValue().getStartingTimes());
        }

        File file = new File(Main.plugin.getDataFolder(), "startingTime.ser");

        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
            oos.writeObject(startingTime);
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    public HashMap<ServerInfo, ServerOnDemand> getServers() {
        return this.servers;
    }
}
