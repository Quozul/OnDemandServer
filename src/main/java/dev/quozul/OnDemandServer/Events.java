package dev.quozul.OnDemandServer;

import dev.quozul.OnDemandServer.enums.ServerStatus;
import dev.quozul.OnDemandServer.enums.StartingStatus;
import dev.quozul.OnDemandServer.events.ServerStartFailEvent;
import dev.quozul.OnDemandServer.events.ServerStartedEvent;
import dev.quozul.OnDemandServer.events.ServerStoppedEvent;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.ServerConnectRequest;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.logging.Level;

import static dev.quozul.OnDemandServer.Main.serverController;


public class Events implements Listener {
    // TODO: Start default server on ping
    // TODO: Display time remaining for server to start in MOTD per player

    @EventHandler
    public void onServerConnect(ServerConnectEvent e) {
        ServerInfo target = e.getTarget();
        ServerOnDemand server = serverController.getServer(target);

        if (server.getStatus() == ServerStatus.STARTED || server.getStatus() == ServerStatus.EMPTY) {
            // Server is currently running
            System.out.println("Connecting to " + server.getName() + "...");

            // If the server is not responding (ie. it crashed), fix it (remove it from list and restart it)
            if (e.getReason() == ServerConnectEvent.Reason.COMMAND || // When using /server <server name>
                    e.getReason() == ServerConnectEvent.Reason.JOIN_PROXY || // When joining the proxy
                    e.getReason() == ServerConnectEvent.Reason.PLUGIN_MESSAGE) { // Reason used by ChestCommands
                // FIXME: Cancelled ServerConnectEvent with no server or disconnect. Happens on JOIN_PROXY.
                e.setCancelled(true);

                ServerConnectRequest.Builder builder = ServerConnectRequest.builder()
                        .retry(false)
                        .reason(ServerConnectEvent.Reason.PLUGIN)
                        .target(target)
                        .connectTimeout(e.getRequest().getConnectTimeout())
                        .callback((result, error) -> {
                            if (result == ServerConnectRequest.Result.FAIL) {
                                boolean serverRemoved = server.safelyRemove();
                                if (serverRemoved) {
                                    e.getPlayer().connect(target); // Reconnecting to start the server
                                }
                            }
                        });
                e.getPlayer().connect(builder.build());
            }

            e.getRequest().setRetry(false);
        } else if (server.getStatus() != ServerStatus.STANDALONE && server.getStatus() != ServerStatus.UNKNOWN) {
            // Server can be controlled by plugin
            long time = server.getAverageStartingTime();

            // Timeout is 2 times the expected time to start
            StartingStatus isStarting = server.startServer(e.getPlayer(), time * 2);
            TextComponent message = new TextComponent();

            boolean isConnected = e.getPlayer().getServer() != null;

            switch (isStarting) {
                case STARTING:
                    System.out.println("Starting server " + server.getName() + "...");

                    // If player is already connected
                    if (isConnected) {
                        // Display an estimated time for the server's startup time
                        message.setText(Main.messages.getString("redirect_message"));
                        e.getPlayer().sendMessage(message);

                        // Print estimated startup time
                        TextComponent text = new TextComponent(String.format(Main.messages.getString("estimated_startup"), time / 1000.));
                        e.getPlayer().sendMessage(ChatMessageType.CHAT, text);
                    } else {
                        // If player is connecting, kick player
                        message.setText(Main.messages.getString("kick_message"));
                    }

                    break;

                case TOO_MUCH_RUNNING:
                    System.out.println("Too much servers are already running!");
                    message.setText(Main.messages.getString("too_many_running"));

                    if (isConnected) e.getPlayer().sendMessage(message);

                    break;

                case ALREADY_STARTING:
                    message.setText(Main.messages.getString("already_starting"));

                    if (isConnected) e.getPlayer().sendMessage(message);

                    break;

                case UNKNOWN:
                    Main.plugin.getLogger().log(Level.SEVERE, "Something went wrong when starting the server!");
                    message.setText("Something went wrong when starting the server!");

                    if (isConnected) e.getPlayer().sendMessage(message);

                    break;

            }

            // TODO: Handle when the network has fallback servers
            if (!isConnected) {
                e.getPlayer().disconnect(message);
            }
            e.getRequest().setRetry(false);
            e.setCancelled(true);
        } else if (server.getStatus() == ServerStatus.UNKNOWN) {
            ServerStatus status = server.updateStatus();
            if (status == ServerStatus.UNKNOWN) {
                TextComponent message = new TextComponent("Sorry, something weird happened.");

                e.getRequest().setRetry(false);

                if (e.getPlayer().isConnected()) {
                    e.getPlayer().sendMessage(message);
                } else {
                    e.getPlayer().disconnect(message);
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent e) {
        ServerOnDemand server = serverController.getServer(e.getServer().getInfo());

        if (server.getStatus() == ServerStatus.DETACHED) {

            TextComponent textComponent = new TextComponent("This server is not controlled by the proxy, please inform the server administrator.");
            e.getPlayer().sendMessage(textComponent);

        } else if (server.getStatus() != ServerStatus.STANDALONE) {

            // Clear server shutdown tasks when player connects to server
            server.clearStopTask();
            server.setStatus(ServerStatus.STARTED);

        }
    }

    @EventHandler
    public void onServerDisconnect(ServerDisconnectEvent e) {
        ServerOnDemand server = serverController.getServer(e.getTarget());

        // If server was started by the proxy
        if (server != null && server.getStatus() != ServerStatus.STANDALONE && server.getStatus() != ServerStatus.DETACHED) {
            // TODO: Check if server is still responding, if not, remove it from list (ie. admin stopped the server)
            server.requestServerStop();
        }
    }

    @EventHandler
    public void onServerStarted(ServerStartedEvent e) {
        long time = e.getPing().getTimeTook();

        e.getServer().setLastStartup(System.currentTimeMillis());

        // Save time took for the server to start
        e.getServer().addStartingTime(time);

        e.getServer().setStatus(ServerStatus.STARTED);

        ProxiedPlayer player = e.getServer().getRequester();

        if (player != null && player.isConnected()) {
            System.out.println("Server " + e.getServer().getName() + " requested by " + player.getName() + " started in " + time / 1000 + "s");
            player.sendMessage(new TextComponent(String.format(Main.messages.getString("startup_time"), time / 1000.)));

            // Move player to started server
            // Handle when player is no longer connected (ie. stop the server)
            player.connect(e.getServer().getServerInfo());
        }

        e.getServer().createStopTask();

        serverController.saveStartingTimes();
    }

    @EventHandler
    public void onServerStartFailed(ServerStartFailEvent e) {
        long time = e.getPing().getTimeTook();
        ProxiedPlayer player = e.getServer().getRequester();

        e.getServer().setStatus(ServerStatus.STOPPED);

        Main.plugin.getLogger().log(Level.WARNING, "Server " + e.getServer().getName() + " requested by " + player.getName() + " failed in " + time / 1000 + "s");
        player.sendMessage(new TextComponent(Main.messages.getString("start_failed")));

        e.getServer().safelyRemove();
    }

    @EventHandler
    public void onServerStop(ServerStoppedEvent e) {
        e.getServer().setLastStop(System.currentTimeMillis());
    }
}
