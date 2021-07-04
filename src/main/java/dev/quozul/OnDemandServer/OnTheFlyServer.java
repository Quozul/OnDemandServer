package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.config.Configuration;

import java.io.Serializable;
import java.util.List;

public class OnTheFlyServer extends ServerOnDemand implements Serializable {
    private final String owner;

    public OnTheFlyServer(String name, Configuration config, ServerInfo serverInfo, int port, String owner) {
        super(name, config, serverInfo);
        this.port = port;
        this.owner = owner;

        List<String> arguments = buildCommand(config);

        builder.command(arguments);
    }

    /**
     * Get server owner's uuid
     * @return Owner's UUID
     */
    public String getOwner() {
        return owner;
    }
}
