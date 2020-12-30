package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.config.ServerInfo;

import java.io.*;


public class Stop implements Runnable {
    private final ServerInfo serverInfo;
    private final String address;

    Stop(ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
        this.address = serverInfo.getSocketAddress().toString();
    }

    @Override
    public void run() {
        int players = this.serverInfo.getPlayers().size();
        if (players > 0) {
            System.out.println("Players on server, not stopping it");
            return;
        }

        Process process = ServerController.processes.get(this.address);
        if (process == null) return;

        // Remove process if not alive
        if (!process.isAlive()) {
            process.destroy();
            ServerController.processes.remove(this.address);
            System.out.println("Server not found, removing it from list");
            return;
        }

        // Stop server then remove process
        try {
            System.out.println("Stopping server...");
            stopServer(process);
            process.waitFor();
            System.out.println("Server stopped!");
            process.destroy();
            ServerController.processes.remove(this.address);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    static public void stopServer(Process p) throws IOException {
        OutputStream stdin = p.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));

        writer.write(Main.configuration.getString("stop_command"));
        writer.flush();
        writer.close();
    }
}
