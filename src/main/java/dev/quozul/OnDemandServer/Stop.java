package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.config.ServerInfo;

import java.io.*;
import java.net.SocketAddress;

import static dev.quozul.OnDemandServer.Main.serverController;


public class Stop implements Runnable {
    private final ServerInfo serverInfo;
    private final SocketAddress address;

    Stop(ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
        this.address = serverInfo.getSocketAddress();
    }

    @Override
    public void run() {
        int players = this.serverInfo.getPlayers().size();
        if (players > 0) {
            System.out.println("Players on server, not stopping it");
            return;
        }

        Process process = serverController.getProcesses().get(this.address);
        if (process == null) return;

        // Remove process if not alive
        if (!process.isAlive()) {
            process.destroy();
            serverController.getProcesses().remove(this.address);
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
            serverController.getProcesses().remove(this.address);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void stopServer(Process p) throws IOException {
        OutputStream stdin = p.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));

        // TODO: Put server to sleep instead of stopping it
        writer.write(Main.configuration.getString("stop_command"));
        writer.flush();
        writer.close();

        // Debug
        try {
            String s;
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

            // read the output from the command
            while ((s = stdInput.readLine()) != null) {
                System.out.println(s);
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }
}
