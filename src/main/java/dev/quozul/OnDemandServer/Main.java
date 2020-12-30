package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class Main extends Plugin {
    static Plugin plugin;
    static Configuration configuration;

    @Override
    public void onEnable() {
        Main.plugin = this;

        // Write default configuration file
        if (!getDataFolder().exists())
            getDataFolder().mkdir();

        File file = new File(getDataFolder(), "config.yml");

        if (!file.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                Files.copy(in, file.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Load configuration
        try {
            Main.configuration = ConfigurationProvider.getProvider(YamlConfiguration.class)
                    .load(new File(getDataFolder(), "config.yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Register events
        getProxy().getPluginManager().registerListener(this, new Events());

        // Register commands
        getProxy().getPluginManager().registerCommand(this, new ServerReport());
    }

    // TODO: Close Minecraft server on Proxy stop
    /*@Override
    public void onDisable() {
        Events.processes.forEach((port, p) -> {
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
        });
    }*/
}
