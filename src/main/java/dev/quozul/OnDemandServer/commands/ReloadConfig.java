package dev.quozul.OnDemandServer.commands;

import dev.quozul.OnDemandServer.Main;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;


public class ReloadConfig extends Command {
    public ReloadConfig() {
        super("odsreload");
    }

    @Override
    public void execute(CommandSender sender, String[] strings) {
        if ((sender instanceof ProxiedPlayer)) {
            // Load configuration
            try {
                Main.configuration = ConfigurationProvider.getProvider(YamlConfiguration.class)
                        .load(new File(Main.plugin.getDataFolder(), "config.yml"));

                Main.serverController.reloadConfig();

                System.out.println("Config reloaded!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
