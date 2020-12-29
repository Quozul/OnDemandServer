package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class Events implements Listener {
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

    Events() {
        this.commands = new HashMap<>();
        Events.processes = new HashMap<>();
        // OTG server
        this.commands.put(25564, "cmd /k cd \"C:\\Users\\erwan\\Documents\\Servers\\OTG test\" && run.cmd");
        // Dev server
        this.commands.put(25563, "cmd /k cd \"C:\\Users\\erwan\\Documents\\Servers\\Dev server\" && run.bat");
    }

    @EventHandler
    public void onServerConnect(ServerConnectEvent e) {
        int port = ((InetSocketAddress) e.getTarget().getSocketAddress()).getPort();
        boolean serverRunningOnPort = !isAvailable(port);
        boolean processRunning = processes.containsKey(port);

        System.out.println(!serverRunningOnPort && !processRunning);

        if (!serverRunningOnPort && !processRunning) {
            System.out.println("Starting server " + e.getTarget().getName() + "...");

            try {
                Process p = Runtime.getRuntime().exec(commands.get(port));
                Events.processes.put(port, p);

                // Async print process
                ProxyServer.getInstance().getScheduler().runAsync(Main.plugin, () -> {
                    try {
                        String s;
                        BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

                        // read the output from the command
                        while ((s = stdInput.readLine()) != null) {
                            System.out.println(s);
                        }

                        System.out.println("Process done.");
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                });

                // Kick
                TextComponent reason = new TextComponent();
                reason.setText("Server is starting, try again in a few seconds.");
                e.getPlayer().disconnect(reason);
                e.setCancelled(true);

                // Wait for server to start
                /*e.getRequest().setRetry(false);
                e.getRequest().setConnectTimeout(30000);*/

                // Stop server in 1 minute if server is empty
                ProxyServer.getInstance().getScheduler().schedule(Main.plugin, new Stop(port, e.getTarget()), 1L, TimeUnit.MINUTES);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        } else {
            System.out.println("Connecting to " + e.getTarget().getName() + "...");
        }
    }

    @EventHandler
    public void onServerDisconnect(ServerDisconnectEvent e) {
        int players = e.getTarget().getPlayers().size();
        if (players > 0) return;

        int port = ((InetSocketAddress) e.getTarget().getSocketAddress()).getPort();
        boolean processRunning = processes.containsKey(port);
        if (!processRunning) return;

        System.out.println("Stopping server in 1 minute");

        // Stop in 1 minute
        ProxyServer.getInstance().getScheduler().schedule(Main.plugin, new Stop(port, e.getTarget()), 1L, TimeUnit.MINUTES);
    }
}
