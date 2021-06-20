package dev.quozul.OnDemandServer;

import dev.quozul.OnDemandServer.commands.ReloadConfig;
import dev.quozul.OnDemandServer.commands.ServerReport;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class Main extends Plugin {
    public static Plugin plugin;
    public static Configuration config;
    public static Configuration messages;
    public static ServerController serverController;

    @Override
    public void onEnable() {
        Main.plugin = this;

        // Write default configuration folder
        if (!getDataFolder().exists())
            getDataFolder().mkdir();

        // Read configuration files
        Main.config = loadConfigFile("config.yml");
        Main.messages = loadConfigFile("messages.yml");

        Main.serverController = new ServerController();

        // Register events
        getProxy().getPluginManager().registerListener(this, new Events());

        // Register commands
        getProxy().getPluginManager().registerCommand(this, new ServerReport());

        // Reload config command
        getProxy().getPluginManager().registerCommand(this, new ReloadConfig());

        try {
            new HttpApi();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Configuration loadConfigFile(String filename) {
        // Read configuration
        File messagesFile = new File(Main.plugin.getDataFolder(), filename);

        if (!messagesFile.exists()) {
            try (InputStream in = Main.plugin.getResourceAsStream(filename)) {
                Files.copy(in, messagesFile.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Load configuration
        try {
            return ConfigurationProvider.getProvider(YamlConfiguration.class)
                    .load(new File(Main.plugin.getDataFolder(), filename));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Reload the configuration variables
     */
    public static void reloadConfig() {
        serverController.stopDelay = Main.config.getInt("stop_delay");
        serverController.maxServers = Main.config.getInt("max_servers");
    }

    // TODO: Close Minecraft server on Proxy stop
    @Override
    public void onDisable() {
        /*Events.processes.forEach((port, p) -> {
            OutputStream stdin = p.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));
            InputStream stdout = p.getInputStream();

            try {
                writer.write("stop\n");
                writer.flush();
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            Scanner scanner = new Scanner(stdout);
            while (scanner.hasNextLine()) {
                System.out.println(scanner.nextLine());
            }
        });*/
    }
}
