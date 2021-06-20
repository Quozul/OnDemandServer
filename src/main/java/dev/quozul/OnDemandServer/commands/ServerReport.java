package dev.quozul.OnDemandServer.commands;

import dev.quozul.OnDemandServer.Main;
import dev.quozul.OnDemandServer.ServerOnDemand;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.quozul.OnDemandServer.Main.serverController;


public class ServerReport extends Command {
    public ServerReport() {
        super("serverreport");
    }

    @Override
    public void execute(CommandSender sender, String[] strings) {
        if ((sender instanceof ProxiedPlayer)) {
            ProxiedPlayer p = (ProxiedPlayer)sender;
            if (!p.hasPermission("ondemandserver.report")) {
                p.sendMessage(new TextComponent(Main.messages.getString("no_permission")));
                return;
            }

            StringBuilder builder = new StringBuilder();
            Map<String, ServerInfo> servers = ProxyServer.getInstance().getServers();

            AtomicBoolean somethingIsWrong = new AtomicBoolean(false);

            for (Map.Entry<ServerInfo, ServerOnDemand> entry : serverController.getServers().entrySet()) {
                builder.append(entry.getValue().getName())
                        .append(" : ")
                        .append(entry.getValue().getStatus())
                        .append("\n");
            }

            TextComponent textComponent = new TextComponent(builder.toString());
            p.sendMessage(textComponent);
        }
    }
}
