package dev.quozul.OnDemandServer;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpApi {
    public HttpApi() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/status", new Status());
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    static class Status implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            Matcher matcher = Pattern.compile("/status/([^/]*)").matcher(t.getRequestURI().getPath());

            String response = "";

            if (!matcher.find()) {
                t.sendResponseHeaders(404, 0);
            } else {
                String serverName = matcher.group(1);
                ServerOnDemand server = Main.serverController.findServerByName(serverName);

                if (server == null) {
                    t.sendResponseHeaders(404, 0);
                } else {
                    t.getResponseHeaders().set("Content-Type", "application/json");
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

                    response = jsonObject.toString();
                }
            }

            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}
