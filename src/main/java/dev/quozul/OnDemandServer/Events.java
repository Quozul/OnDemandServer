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
            TextComponent reason = new TextComponent();
            reason.setText("Server is starting, try again in a minute.");

            try {
                Process p = Runtime.getRuntime().exec(commands.get(port));
                Events.processes.put(port, p);

                // Async print process
                ProxyServer.getInstance().getScheduler().runAsync(Main.plugin, () -> {
                    try {
                        String s = null;
                        BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
                        BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

                        // read the output from the command
                        while ((s = stdInput.readLine()) != null) {
                            System.out.println(s);
                        }

                        System.out.println("Process done.");
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                });

                /*e.getPlayer().disconnect(reason);
                e.setCancelled(true);*/
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        } else {
            System.out.println("Connecting to " + e.getTarget().getName() + "...");
        }

        if (processRunning) {
            e.getRequest().setRetry(false);
            e.getRequest().setConnectTimeout(30000);
        }
    }

    @EventHandler
    public void onServerDisconnect(ServerDisconnectEvent e) {
        int players = e.getTarget().getPlayers().size();
        if (players > 0) return;

        int port = ((InetSocketAddress) e.getTarget().getSocketAddress()).getPort();
        boolean processRunning = processes.containsKey(port);
        if (!processRunning) return;

        System.out.println("Stopping server in 5 seconds");

        // Stop in 5 seconds
        ProxyServer.getInstance().getScheduler().schedule(Main.plugin, new Runnable() {
            @Override
            public void run() {
                int players = e.getTarget().getPlayers().size();
                if (players > 0) {
                    System.out.println("Players on server, not stopping it");
                    return;
                }

                Process process = processes.get(port);

                try {
                    System.out.println("Stopping server...");
                    stopServer(process);
                    process.waitFor();
                    System.out.println("Server stopped!");
                    process.destroy();
                    Events.processes.remove(port);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, 5L, TimeUnit.SECONDS);
    }

    static public void stopServer(Process p) throws IOException {
        OutputStream stdin = p.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));
        InputStream stdout = p.getInputStream();

        writer.write("stop\n");
        writer.flush();
        writer.close();
    }
}
