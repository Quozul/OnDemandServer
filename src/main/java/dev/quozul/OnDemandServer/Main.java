package dev.quozul.OnDemandServer;

import net.md_5.bungee.api.plugin.Plugin;

public class Main extends Plugin {
    static Plugin plugin;

    @Override
    public void onEnable() {
        Main.plugin = this;
        // You should not put an enable message in your plugin.
        // BungeeCord already does so
        getLogger().info("Yay! It loads!");
        getProxy().getPluginManager().registerListener(this, new Events());
    }

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
