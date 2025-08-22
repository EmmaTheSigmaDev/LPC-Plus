package me.wikmor.lpc;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LPC extends JavaPlugin implements Listener {

	private LuckPerms luckPerms;
	private final MiniMessage miniMessage = MiniMessage.miniMessage();
	private final LegacyComponentSerializer legacySerializer =
			LegacyComponentSerializer.builder()
					.character('&') // & color codes
					.hexColors()    // &#rrggbb
					.useUnusualXRepeatedCharacterHexFormat() // &x&R&R&G&G&B&B
					.build();

	@Override
	public void onEnable() {
		this.luckPerms = getServer().getServicesManager().load(LuckPerms.class);

		saveDefaultConfig();
		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public boolean onCommand(final @NotNull CommandSender sender, final @NotNull Command command, final @NotNull String label, final String[] args) {
		if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
			reloadConfig();
			sender.sendMessage("§aLPC has been reloaded.");
			return true;
		}
		return false;
	}

	@Override
	public List<String> onTabComplete(final @NotNull CommandSender sender, final @NotNull Command command, final @NotNull String alias, final String[] args) {
		if (args.length == 1) {
			return Collections.singletonList("reload");
		}
		return new ArrayList<>();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onChat(final AsyncPlayerChatEvent event) {
		event.setCancelled(true);

		Player player = event.getPlayer();
		String rawMessage = event.getMessage();

		CachedMetaData metaData = this.luckPerms.getPlayerAdapter(Player.class).getMetaData(player);
		String group = metaData.getPrimaryGroup();

		// Pull format from config
		String format = getConfig().getString("group-formats." + group,
				getConfig().getString("chat-format", "<gray><name>: <message>"));

		// Replace placeholders
		format = format
				.replace("{prefix}", metaData.getPrefix() != null ? metaData.getPrefix() : "")
				.replace("{suffix}", metaData.getSuffix() != null ? metaData.getSuffix() : "")
				.replace("{world}", player.getWorld().getName())
				.replace("{name}", player.getName())
				.replace("{displayname}", player.getDisplayName());

		// PlaceholderAPI support
		if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			format = PlaceholderAPI.setPlaceholders(player, format);
		}

		// --- Player message parsing ---
		Component messageComponent;
		if (player.hasPermission("lpc.minimessage") && rawMessage.contains("<")) {
			// Full MiniMessage support
			messageComponent = miniMessage.deserialize(rawMessage);
		} else if (player.hasPermission("lpc.legacycolor") || player.hasPermission("lpc.hex")) {
			// Legacy & + hex codes
			messageComponent = legacySerializer.deserialize(rawMessage);
		} else {
			// Plain text only
			messageComponent = Component.text(rawMessage);
		}

		// --- Format parsing (MiniMessage OR Legacy) ---
		Component formatComponent;
		if (format.contains("<")) {
			formatComponent = miniMessage.deserialize(format);
		} else {
			formatComponent = legacySerializer.deserialize(format);
		}

		// Replace {message} inside format with parsed message component
		Component finalMessage = formatComponent.replaceText(builder ->
				builder.matchLiteral("{message}").replacement(messageComponent));

		// Send to all players
		getServer().sendMessage(finalMessage);
	}
}