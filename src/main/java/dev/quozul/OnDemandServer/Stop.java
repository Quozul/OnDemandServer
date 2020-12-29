package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.config.ServerInfo;

import java.io.*;


public class Stop implements Runnable {
    private final ServerInfo e;
    private final int port;

    Stop(int port, ServerInfo e) {
        this.e = e;
        this.port = port;
    }

    @Override
    public void run() {
        int players = this.e.getPlayers().size();
        if (players > 0) {
            System.out.println("Players on server, not stopping it");
            return;
        }

        Process process = ServerController.processes.get(this.port);
        if (process == null) return;

        try {
            System.out.println("Stopping server...");
            stopServer(process);
            process.waitFor();
            System.out.println("Server stopped!");
            process.destroy();
            ServerController.processes.remove(this.port);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    static public void stopServer(Process p) throws IOException {
        OutputStream stdin = p.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));

        writer.write("stop\n");
        writer.flush();
        writer.close();
    }
}
