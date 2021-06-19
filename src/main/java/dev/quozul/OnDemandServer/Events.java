package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.ServerConnectRequest;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import static dev.quozul.OnDemandServer.Main.serverController;


public class Events implements Listener {
    // TODO: Start default server on ping
    // TODO: Display time remaining for server to start in MOTD per player

    @EventHandler
    public void onServerConnect(ServerConnectEvent e) {
        ServerInfo target = e.getTarget();

        ServerOnDemand server = serverController.getServer(target);

        if (server.getStatus() == ServerStatus.RUNNING || server.getStatus() == ServerStatus.EMPTY) {
            System.out.println("Connecting to " + target.getName() + "...");

            // If the server is not responding (ie. it crashed), fix it (remove it from list and restart it)
            // TODO: Handle JOIN_PROXY
            if (e.getReason() == ServerConnectEvent.Reason.COMMAND) {
                e.setCancelled(true);

                ServerConnectRequest.Builder builder = ServerConnectRequest.builder()
                        .retry(false)
                        .reason(ServerConnectEvent.Reason.PLUGIN)
                        .target(e.getTarget())
                        .connectTimeout(e.getRequest().getConnectTimeout())
                        .callback(new Callback<ServerConnectRequest.Result>() {
                            @Override
                            public void done(ServerConnectRequest.Result result, Throwable error) {
                                if (result == ServerConnectRequest.Result.FAIL) {
                                    boolean serverRemoved = serverController.getServer(e.getTarget()).safelyRemove();
                                    if (serverRemoved) {
                                        e.getPlayer().connect(e.getTarget()); // Reconnecting to start the server
                                    }
                                }
                            }
                        });
                e.getPlayer().connect(builder.build());
            }

            e.getRequest().setRetry(false);
        } else if (serverController.canBeControlled(target)) {
            long time = serverController.getServer(target).getAverageStartingTime();

            // Timeout is 2 times the expected time to start
            char isStarting = serverController.getServer(target).startServer(e.getPlayer(), time * 2);
            TextComponent message = new TextComponent();

            switch (isStarting) {
                case 0:
                    System.out.println("Starting server " + target.getName() + "...");

                    // If player is already connected
                    if (e.getPlayer().getServer() != null) {
                        // Display an estimated time for the server's startup time
                        message.setText(Main.configuration.getString("redirect_message"));
                        e.getPlayer().sendMessage(message);

                        TextComponent text = new TextComponent(String.format(Main.configuration.getString("estimated_startup"), time / 1000.));
                        e.getPlayer().sendMessage(ChatMessageType.CHAT, text);
                    } else {
                        // If plays is connecting, kick player
                        message.setText(Main.configuration.getString("kick_message"));
                        // TODO: Handle when the network has fallback servers
                        e.getPlayer().disconnect(message);
                        e.setCancelled(true);
                    }

                    break;

                case 1:
                    System.out.println("Too much servers are already running!");
                    message.setText(Main.configuration.getString("too_many_running"));

                    if (e.getPlayer().isConnected()) e.getPlayer().sendMessage(message);
                    else e.getPlayer().disconnect(message);

                    break;

                case 2:
                    System.out.println("Something went wrong when starting the server!");
                    message.setText("Something went wrong when starting the server!");

                    if (e.getPlayer().isConnected()) e.getPlayer().sendMessage(message);
                    else e.getPlayer().disconnect(message);

                    break;

            }

            e.getRequest().setRetry(false);
            e.setCancelled(true);
        } else {
            System.out.println("Plugin is not handling connection.");
        }
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent e) {
        ServerOnDemand server = serverController.getServer(e.getServer().getInfo());

        // Clear server shutdown tasks when player connects to server
        server.clearStopTask();

        if (server.getStatus() == ServerStatus.STANDALONE) {
            TextComponent textComponent = new TextComponent("This server is not controlled by the proxy, please inform the server administrator.");
            e.getPlayer().sendMessage(textComponent);
        }
    }

    @EventHandler
    public void onServerDisconnect(ServerDisconnectEvent e) {
        ServerInfo target = e.getTarget();

        // If server was started by the proxy
        if (serverController.canBeControlled(target)) {
            // TODO: Check if server is still responding, if not, remove it from list (ie. admin stopped the server)
            serverController.getServer(target).requestServerStop();
        }
    }

    @EventHandler
    public void onServerStarted(ServerStartedEvent e) {
        long time = e.getPing().getTimeTook();
        // TODO: Handle when several players try to start the same server
        ProxiedPlayer player = e.getServer().getRequester();

        // Save time took for the server to start
        e.getServer().addStartingTime(time);

        System.out.println("Server " + e.getServer().getName() + " requested by " + player.getName() + " started in " + time / 1000 + "s");
        player.sendMessage(new TextComponent(String.format(Main.configuration.getString("startup_time"), time / 1000.)));

        e.getServer().setStatus(ServerStatus.RUNNING);

        // Move player to started server
        // Handle when player is no longer connected (ie. stop the server)
        if (player.isConnected()) {
            player.connect(e.getServer().getServerInfo());
        }

        e.getServer().createStopTask();
    }

    @EventHandler
    public void onServerStartFailed(ServerStartFailEvent e) {
        long time = e.getPing().getTimeTook();
        ProxiedPlayer player = e.getServer().getRequester();

        e.getServer().setStatus(ServerStatus.STOPPED);

        System.out.println("Server " + e.getServer().getName() + " requested by " + player.getName() + " failed in " + time / 1000 + "s");
        player.sendMessage(new TextComponent(Main.configuration.getString("start_failed")));

        // FIXME: serverController.safelyRemoveFromList(serverInfo);
    }
}
