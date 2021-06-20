package dev.quozul.OnDemandServer;

import dev.quozul.OnDemandServer.enums.ServerStatus;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ConfigurationAdapter;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.config.Configuration;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
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

    private final HashMap<ServerInfo, ServerOnDemand> servers;
    private final HashMap<String, File> templates;

    public int stopDelay;
    public int maxServers;
    public int minPort;
    public int maxPort;

    ServerController() {
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
            ServerInfo serverInfo = entry.getValue();

            ServerOnDemand server;

            boolean inConfig = serverConfig.contains(name);

            if (!inConfig) {
                server = new ServerOnDemand(name, serverInfo);
            } else {
                Configuration config = serverConfig.getSection(name);

                try {
                    server = new ServerOnDemand(name, config, serverInfo);
                } catch (RuntimeException e) {
                    server = new ServerOnDemand(name, serverInfo);
                    server.setStatus(ServerStatus.NOT_CONFIGURED);

                    Main.plugin.getLogger().log(Level.SEVERE, "Could not load configuration for server " + name);
                    Main.plugin.getLogger().log(Level.SEVERE, e.getMessage());
                }
            }

            this.servers.put(serverInfo, server);

            // Load starting times
            if (startingTime.containsKey(name)) {
                server.setStartingTimes(startingTime.get(name));
            }

            boolean serverStarted = isServerStarted(serverInfo);

            if (serverStarted && inConfig) {
                server.setStatus(ServerStatus.DETACHED);
            } else if (serverStarted) {
                server.setStatus(ServerStatus.STANDALONE);
            } else if (inConfig) {
                server.setStatus(ServerStatus.STOPPED);
            } else {
                server.setStatus(ServerStatus.UNKNOWN);
            }
        }

        // Load templates
        templates = new HashMap<>();

        if (Main.config.getBoolean("allow_server_on_the_fly") && Main.config.contains("templates")) {
            File templateFolder = new File(Main.config.getString("templates"));

            for (final File template : Objects.requireNonNull(templateFolder.listFiles())) {
                templates.put(template.getName(), template);
            }
        }
    }

    public int getRunningServers() {
        int sum = 0;
        for (Map.Entry<ServerInfo, ServerOnDemand> entry : servers.entrySet()) {
            if (entry.getValue().getStatus() == ServerStatus.STARTED) sum++;
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
     * Get a server by name
     * @param name Server object
     * @return The server
     */
    public ServerOnDemand findServerByName(String name) {
        for (Map.Entry<ServerInfo, ServerOnDemand> entry : servers.entrySet()) {
            ServerOnDemand server = entry.getValue();
            if (server.getName().equals(name)) {
                return server;
            }
        }
        return null;
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

    private static void copyDirectory(File sourceDirectory, File destinationDirectory) throws IOException {
        if (!destinationDirectory.exists()) {
            destinationDirectory.mkdir();
        }
        for (String f : sourceDirectory.list()) {
            copyDirectoryCompatibityMode(new File(sourceDirectory, f), new File(destinationDirectory, f));
        }
    }

    private static void copyDirectoryCompatibityMode(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            copyDirectory(source, destination);
        } else {
            copyFile(source, destination);
        }
    }

    private static void copyFile(File sourceFile, File destinationFile) throws IOException {
        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = new FileOutputStream(destinationFile)) {
            byte[] buf = new byte[1024];
            int length;
            while ((length = in.read(buf)) > 0) {
                out.write(buf, 0, length);
            }
        }
    }

    /**
     * Creates a new server
     * @param name Server's name
     * @param template Template to use for the new server
     */
    public void createServer(String name, File template) {
        int port;
        // Find an available port in range
        for (port = minPort; port < maxPort; port++) if (isAvailable(port)) break;

        if (port <= 0) throw new RuntimeException("No available port found in range");

        InetSocketAddress address = new InetSocketAddress("127.0.0.1", port);
        String motd = name;
        boolean restricted = false;

        ProxyServer proxy = Main.plugin.getProxy();

        // Add server to bungeecord
        ServerInfo serverInfo = proxy.constructServerInfo(name, address, motd, restricted);
        proxy.getServers().put(name, serverInfo);

        // Create configuration
        Configuration configuration = new Configuration();
        configuration.set("directory", Main.config.getString("server_folder") + File.separator + name);
        configuration.set("IReallyKnowWhatIAmDoingISwear", true);
        configuration.set("jar_file", "spigot.jar");
        configuration.set("maximum_memory", 8192);

        // Add server to plugin's configuration
        ServerOnDemand server = new ServerOnDemand(name, configuration, serverInfo, port);
        this.servers.put(serverInfo, server);

        server.setStatus(ServerStatus.STOPPED);

        // Copy template folder
        String destinationDirectoryLocation = Main.config.getString("server_folder") + File.separator + name;
        File destinationDirectory = new File(destinationDirectoryLocation);

        try {
            copyDirectory(template, destinationDirectory);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // TODO: Add forced host
    }

    public HashMap<ServerInfo, ServerOnDemand> getServers() {
        return this.servers;
    }

    public HashMap<String, File> getTemplates() {
        return this.templates;
    }
}
