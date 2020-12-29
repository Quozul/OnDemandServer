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
import java.util.concurrent.TimeUnit;

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

    private HashMap<Integer, String> commands;
    static HashMap<Integer, Process> processes;

    ServerController() {
        this.commands = new HashMap<>();
        this.processes = new HashMap<>();
        // OTG server
        this.commands.put(25564, "cmd /k cd \"C:\\Users\\erwan\\Documents\\Servers\\OTG test\" && run.cmd");
        // Dev server
        this.commands.put(25563, "cmd /k cd \"C:\\Users\\erwan\\Documents\\Servers\\Dev server\" && run.bat");
    }

    /**
     * Verify if a process is running on the target's port and if the process is registered by the plugin
     * @param serverInfo Target
     * @return Server running
     */
    public boolean isServerStarted(ServerInfo serverInfo) {
        int port = ((InetSocketAddress) serverInfo.getSocketAddress()).getPort();
        boolean serverRunningOnPort = !isAvailable(port);
        boolean processRunning = processes.containsKey(port);

        return serverRunningOnPort && processRunning;
    }

    /**
     * Starts the given server
     * @param serverInfo Target
     * @param player Move the given player once the server is started
     */
    public void startServer(ServerInfo serverInfo, ProxiedPlayer player) {
        int port = ((InetSocketAddress) serverInfo.getSocketAddress()).getPort();

        try {
            Process p = Runtime.getRuntime().exec(commands.get(port));
            processes.put(port, p);

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

            // Stop server in 1 minute if server is empty
            ProxyServer.getInstance().getScheduler().schedule(Main.plugin, new Stop(port, serverInfo), 1L, TimeUnit.MINUTES);
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

        int port = ((InetSocketAddress) serverInfo.getSocketAddress()).getPort();
        boolean processRunning = processes.containsKey(port);
        if (!processRunning) return;

        System.out.println("Stopping server in 1 minute");

        // Stop in 1 minute
        ProxyServer.getInstance().getScheduler().schedule(Main.plugin, new Stop(port, serverInfo), 1L, TimeUnit.MINUTES);

    }
}
