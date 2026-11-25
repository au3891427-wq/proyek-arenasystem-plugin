package id.hadziq.arenasystem;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

// $VF: Compiled from SkillInputListener.java
final class SkillInputListener implements PluginMessageListener {
    private final ArenaSystemPlugin plugin;

    SkillInputListener(ArenaSystemPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "arenasystem:skill_input", this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "arenasystem:skill_input");
    }

    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if ("arenasystem:skill_input".equals(channel) && player != null && player.isOnline()) {
            byte action = SkillInputProtocol.decodeAction(message);
            if (action != 0) {
                ClassAbilityListener abilities = this.plugin.classAbilityListener;
                if (abilities != null) {
                    abilities.handleSkillInput(player, action);
                }
            }
        }
    }

    void close() {
        this.plugin
            .getServer()
            .getMessenger()
            .unregisterIncomingPluginChannel(this.plugin, "arenasystem:skill_input", this);
        this.plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(this.plugin, "arenasystem:skill_input");
    }
}
