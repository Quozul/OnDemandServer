package dev.quozul.OnDemandServer;

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

            StringBuilder builder = new StringBuilder();
            Map<String, ServerInfo> servers = ProxyServer.getInstance().getServers();

            AtomicBoolean somethingIsWrong = new AtomicBoolean(false);

            servers.forEach((name, serverInfo) -> {
                boolean isControlled = serverController.isControlledByProxy(serverInfo);
                boolean canBeControlled = serverController.canBeControlled.apply(serverInfo);
                boolean isStarted = serverController.isServerStarted(serverInfo);

                boolean isOrphan = isStarted && canBeControlled && !isControlled;
                boolean isReady = !isStarted && !isControlled && canBeControlled;
                boolean isLost = !isStarted && isControlled;

                builder.append("§7§lServer ").append(name).append("§r§7\n");

                if (isReady) {
                    builder.append("§8  + §7Can be controlled by proxy\n");
                }
                if (isOrphan) {
                    builder.append("  §eWARNING\n");
                    builder.append("§8  + §eIs running but not attached\n");
                    somethingIsWrong.set(true);
                }
                if (isLost) {
                    builder.append("  §cERROR\n");
                    builder.append("§8  + §cIs attached but not running\n");
                    somethingIsWrong.set(true);
                }
                if (!canBeControlled) {
                    builder.append("§8  + §7Is a standalone server\n");
                }
                if (isControlled && isStarted) {
                    builder.append("§8  + §7Is controlled by proxy\n");
                }

                if (isStarted) {
                    builder.append("§8  + §2Is running\n");
                } else {
                    builder.append("§8  + §cIs offline\n");
                }
            });

            if (somethingIsWrong.get())
                builder.append("§cSomething is wrong!");
            else
                builder.append("§2Everything is fine.");

            TextComponent textComponent = new TextComponent(builder.toString());
            p.sendMessage(textComponent);
        }
    }
}
