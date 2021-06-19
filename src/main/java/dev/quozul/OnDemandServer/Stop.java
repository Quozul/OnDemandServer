package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.config.ServerInfo;

import java.io.*;
import java.net.SocketAddress;

import static dev.quozul.OnDemandServer.Main.serverController;


public class Stop implements Runnable {
    private final ServerOnDemand server;

    Stop(ServerOnDemand server) {
        this.server = server;
    }

    @Override
    public void run() {
        // Ensure no player are on the server
        int players = server.getServerInfo().getPlayers().size();
        if (players > 0) {
            System.out.println("Players on " + server.getName() + ", not stopping it");
            return;
        }

        Process process = server.getProcess();
        if (process == null) return;

        // Remove process if not alive
        if (!process.isAlive()) {
            process.destroy();
            server.removeProcess();
            System.out.println("Process not found");
            return;
        }

        server.setStatus(ServerStatus.STOPPING);

        // Stop server then remove process
        try {
            System.out.println("Stopping server...");

            stopServer(process);
            process.waitFor();
            process.destroy();
            server.removeProcess();

            System.out.println("Server " + server.getName() + " stopped!");
            server.setStatus(ServerStatus.STOPPED);
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
        /*try {
            String s;
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

            // read the output from the command
            while ((s = stdInput.readLine()) != null) {
                System.out.println(s);
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }*/
    }
}
