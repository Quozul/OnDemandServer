package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
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
    public static boolean isAvailable(int portNr) {
        boolean portFree;
        try (ServerSocket ignored = new ServerSocket(portNr)) {
            portFree = true;
        } catch (IOException e) {
            portFree = false;
        }
        return portFree;
    }

    static HashMap<String, Process> processes;
    private final int stop_delay;
    private final String stop_command;

    ServerController() {
        this.stop_delay = Main.configuration.getInt("stop_delay");
        this.stop_command = Main.configuration.getString("stop_command");
        processes = new HashMap<>();
    }

    /**
     * Verify if a process is running on the target's port and if the process is registered by the plugin
     * @param serverInfo Target
     * @return Server running
     */
    public boolean isServerStarted(ServerInfo serverInfo) {
        int port = ((InetSocketAddress) serverInfo.getSocketAddress()).getPort();
        boolean serverRunningOnPort = !isAvailable(port);

        String address = serverInfo.getSocketAddress().toString();
        boolean processRunning = processes.containsKey(address);

        return serverRunningOnPort || processRunning;
    }

    /**
     * Check if the given server is controlled by the proxy server
     * @param serverInfo Target
     * @return Is controlled by proxy
     */
    public boolean isControlledByProxy(ServerInfo serverInfo) {
        String address = serverInfo.getSocketAddress().toString();
        return processes.containsKey(address);
    }

    /**
     * Return the LinkedHashMap of the server from the config file
     * @param address Target's address
     * @return Server's information
     */
    public LinkedHashMap<String, String> getServerFromConfig(String address) {
        List<?> servers = Main.configuration.getList("servers").stream()
                .filter((Predicate<Object>) o -> ((LinkedHashMap<String, String>) o).get("address").equals(address))
                .collect(Collectors.toList());

        if (servers.size() == 0) return null;

        return (LinkedHashMap<String, String>) servers.get(0);
    }

    /**
     * Return the LinkedHashMap of the server from the config file
     * @param serverInfo Target
     * @return Server's information
     */
    public LinkedHashMap<String, String> getServerFromConfig(ServerInfo serverInfo) {
        String address = serverInfo.getSocketAddress().toString();
        return getServerFromConfig(address);
    }

    /**
     * Starts the given server
     * @param serverInfo Target
     * @param player Move the given player once the server is started
     */
    public void startServer(ServerInfo serverInfo, ProxiedPlayer player) {
        String address = serverInfo.getSocketAddress().toString();
        LinkedHashMap<String, String> server = getServerFromConfig(address);

        if (server == null) {
            System.out.println("Server can't be started, not in config file!");
            return;
        }

        String command = server.get("command");

        try {
            Process p = Runtime.getRuntime().exec(command);
            processes.put(address, p);

            // Async print process
            ProxyServer.getInstance().getScheduler().runAsync(Main.plugin, () -> {
                try {
                    String s;
                    BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

                    // read the output from the command
                    while ((s = stdInput.readLine()) != null) {
                        if (s.contains("Done")) {
                            System.out.println("Server started!");

                            if (player.isConnected()) {
                                player.connect(serverInfo);
                            }

                            break;
                        }
                    }
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            });

            // Stop server after the delay is over if server is empty
            ProxyServer.getInstance().getScheduler().schedule(Main.plugin, new Stop(address, serverInfo), this.stop_delay, TimeUnit.MINUTES);
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

        String address = serverInfo.getSocketAddress().toString();
        boolean processRunning = processes.containsKey(address);
        if (!processRunning) return;

        System.out.println("Stopping server in " + this.stop_delay + " minute");

        // Stop in 1 minute
        ProxyServer.getInstance().getScheduler().schedule(Main.plugin, new Stop(address, serverInfo), this.stop_delay, TimeUnit.MINUTES);
    }

    public static void stopAllServers() {
        processes.forEach((address, process) -> {
            try {
                System.out.println("Stopping server...");
                Stop.stopServer(process);
                process.waitFor();
                System.out.println("Server stopped!");
                process.destroy();
                processes.remove(address);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });
    }
}
