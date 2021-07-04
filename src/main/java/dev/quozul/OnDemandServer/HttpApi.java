package dev.quozul.OnDemandServer;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.quozul.OnDemandServer.enums.ServerStatus;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.config.Configuration;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpApi {
    public HttpApi(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new Index()); // Index page
        server.createContext("/status", new Status()); // Get status for all servers
        server.createContext("/start", new StartServer()); // Starts a server
        server.createContext("/stop", new StopServer()); // Stops a server
        server.createContext("/user", new OnTheFly()); // Get status for a user created servers
        server.createContext("/logs", new Logs()); // Run a command on the given server

        // Multi threaded HttpServer https://stackoverflow.com/a/14729886/8445676
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("HTTP server started, access it using: http://localhost:" + port);
    }

    private static JsonObject getServerJson(ServerOnDemand server) {
        JsonObject jsonObject = new JsonObject();

        jsonObject.addProperty("status", server.getStatus().toString());
        jsonObject.addProperty("name", server.getName());
        jsonObject.addProperty("average_starting_time", server.getAverageStartingTime());

        if (server.getRequester() != null) {
            jsonObject.addProperty("requester", server.getRequester().getUniqueId().toString());
        } else {
            jsonObject.add("requester", JsonNull.INSTANCE);
        }

        jsonObject.addProperty("started_since", server.getLastStartup());
        jsonObject.addProperty("closed_since", server.getLastStop());

        // Base64 encoded motd
        String motd = server.getServerInfo().getMotd();
        byte[] base64 = Base64.getEncoder().encode(motd.getBytes(StandardCharsets.UTF_8));
        jsonObject.addProperty("motd", new String(base64, StandardCharsets.UTF_8));

        jsonObject.addProperty("players", server.getServerInfo().getPlayers().size());
        jsonObject.addProperty("address", server.getAddress().toString());

        boolean type = server instanceof OnTheFlyServer;
        if (type) {
            jsonObject.addProperty("type", "onthefly");
            jsonObject.addProperty("owner", ((OnTheFlyServer) server).getOwner());
        } else {
            jsonObject.addProperty("type", "normal");
        }

        Configuration serverConfig = server.getConfiguration();
        if (serverConfig != null) {
            JsonObject configuration = new JsonObject();

            for (String key : serverConfig.getKeys()) {
                configuration.addProperty(key, String.valueOf(serverConfig.get(key)));
            }

            jsonObject.add("configuration", configuration);
        }

        return jsonObject;
    }

    static class Index implements HttpHandler {
        private String response;

        Index() {
            try {
                byte[] bytes = Main.plugin.getResourceAsStream("index.html").readAllBytes();
                response = "";
                for (byte b : bytes) {
                    response = response.concat(Character.toString(b));
                }
            } catch (IOException e) {
                e.printStackTrace();
                response = "Sorry, an error occurred while loading the main page.";
            }
        }

        @Override
        public void handle(HttpExchange t) throws IOException {
            if (!t.getRequestURI().getPath().equals("/")) {
                t.sendResponseHeaders(404, -1);
                return;
            }

            t.getResponseHeaders().set("Content-Type", "text/html;charset=utf-8");

            t.sendResponseHeaders(200, response.length());

            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    static class Status implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            Matcher matcher = Pattern.compile("/status/([^/]*)").matcher(t.getRequestURI().getPath());

            String response;

            t.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");

            if (!matcher.find()) {
                JsonObject jsonObject = new JsonObject();

                for (Map.Entry<ServerInfo, ServerOnDemand> entry : Main.serverController.getServers().entrySet()) {
                    ServerOnDemand server = entry.getValue();
                    jsonObject.add(server.getName(), getServerJson(server));
                }

                response = jsonObject.toString();
                t.sendResponseHeaders(200, response.length());

                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                String serverName = matcher.group(1);
                ServerOnDemand server = Main.serverController.findServerByName(serverName);

                if (server == null) {
                    t.sendResponseHeaders(404, -1);
                } else {
                    response = getServerJson(server).toString();
                    t.sendResponseHeaders(200, response.length());

                    OutputStream os = t.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                }
            }
        }
    }

    static class StartServer implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            Matcher matcher = Pattern.compile("/start/([^/]*)").matcher(t.getRequestURI().getPath());
            t.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");

            String response = "";

            if (!matcher.find()) {
                t.sendResponseHeaders(400, -1);
            } else {
                String serverName = matcher.group(1);
                ServerOnDemand server = Main.serverController.findServerByName(serverName);

                response = server.startServer(server.getAverageStartingTime() * 2).toString();

                t.sendResponseHeaders(200, response.length());
            }

            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    static class StopServer implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            Matcher matcher = Pattern.compile("/stop/([^/]*)").matcher(t.getRequestURI().getPath());
            t.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");

            String response = "";

            if (!matcher.find()) {
                t.sendResponseHeaders(400, -1);
            } else {
                String serverName = matcher.group(1);
                ServerOnDemand server = Main.serverController.findServerByName(serverName);

                if (server.getStatus() == ServerStatus.STARTED || server.getStatus() == ServerStatus.EMPTY) {
                    server.createStopTask(0);
                    response = "STOPPING";
                    t.sendResponseHeaders(200, response.length());
                } else {
                    response = "NOT_RUNNING";
                    t.sendResponseHeaders(400, response.length());
                }

                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }

    static class OnTheFly implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            Matcher matcher = Pattern.compile("/user/([^/]*)").matcher(t.getRequestURI().getPath());
            t.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");

            String response = "";
            if (!matcher.find()) {
                t.sendResponseHeaders(400, -1);
            } else {
                String ownerUUID = matcher.group(1);

                List<OnTheFlyServer> servers = Main.serverController.findServersByOwner(ownerUUID);
                if (servers.size() == 0) {
                    t.sendResponseHeaders(404, -1);
                    return;
                }

                JsonObject jsonObject = new JsonObject();
                for (OnTheFlyServer server : servers) {
                    jsonObject.add(server.getName(), getServerJson(server));
                }

                response = jsonObject.toString();
                t.sendResponseHeaders(200, response.length());

                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }

    static class Logs implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            Matcher matcher = Pattern.compile("/logs/([^/]*)").matcher(t.getRequestURI().getPath());
            t.getResponseHeaders().set("Content-Type", "text/event-stream;charset=utf-8");
            t.getResponseHeaders().set("Cache-Control", "no-cache");

            if (!matcher.find()) {
                t.sendResponseHeaders(400, -1);
            } else {
                String serverName = matcher.group(1);

                ServerOnDemand server = Main.serverController.findServerByName(serverName);
                if (server == null) {
                    t.sendResponseHeaders(404, -1);
                    return;
                }

                Process process = server.getProcess();
                /*OutputStream stdin = process.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));

                String command = new String(t.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                System.out.println(command);

                writer.write(command);
                writer.flush();
                writer.close();*/

                if (process == null || !process.isAlive()) {
                    t.sendResponseHeaders(503, -1);
                    return;
                }

                t.sendResponseHeaders(200, 0);

                OutputStream os = t.getResponseBody();

                InputStream inputStream = process.getInputStream();
                inputStream.mark(Integer.MAX_VALUE);
                inputStream.reset();

                BufferedReader stdin = new BufferedReader(new InputStreamReader(inputStream));
                String s;
                int i = 0;

                while ((s = stdin.readLine()) != null) {
                    String response = "id: " + i++ + "\n" + "event: log\ndata: " + s + "\n\n";
                    os.write(response.getBytes());
                    os.flush();
                }
            }
        }
    }
}
