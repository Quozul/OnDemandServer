package dev.quozul.OnDemandServer.commands;

import dev.quozul.OnDemandServer.Main;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.io.File;
import java.util.*;

public class CreateServer extends Command implements TabExecutor {
    public CreateServer() {
        super("odscreate");
    }

    @Override
    public void execute(CommandSender sender, String[] strings) {
        if ((sender instanceof ProxiedPlayer)) {
            ProxiedPlayer p = (ProxiedPlayer) sender;
            if (!p.hasPermission("ondemandserver.create")) {
                p.sendMessage(new TextComponent(Main.messages.getString("no_permission")));
                return;
            }

            try {
                File template = new File(Main.config.getString("templates") + File.separator + strings[0]);

                Main.serverController.createServer(p, template);
                p.sendMessage(new TextComponent("Server created!"));
            } catch (RuntimeException e) {
                p.sendMessage(new TextComponent(e.getMessage()));
            }
        }
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender commandSender, String[] strings) {
        Set<String> iterable = new HashSet<>();

        if (strings.length == 1) {
            iterable = Main.serverController.getTemplates().keySet();
        }

        return iterable;
    }
}
