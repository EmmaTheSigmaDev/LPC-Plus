/*
 * LPCPlus - A modern chat formatting plugin for Minecraft servers using LuckPerms
 * Released into the public domain under the Unlicense
 * http://unlicense.org/
 * Author: Emma_TheSigma
 */
package me.wikmor.lpcplus;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class LPCPlus extends JavaPlugin implements Listener {

	private LuckPerms luckPerms;
	private boolean hasLuckPerms = false;
	private boolean hasPAPI = false;

	private final MiniMessage miniMessage = MiniMessage.miniMessage();
	private final LegacyComponentSerializer legacySerializer =
			LegacyComponentSerializer.builder()
					.character('&')
					.hexColors()
					.useUnusualXRepeatedCharacterHexFormat()
					.build();

	@Override
	public void onEnable() {
		// Check for LuckPerms
		luckPerms = getServer().getServicesManager().load(LuckPerms.class);
		hasLuckPerms = luckPerms != null;

		// Check for PlaceholderAPI
		hasPAPI = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

		saveDefaultConfig();
		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
		if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
			reloadConfig();
			sender.sendMessage("§aLPCPlus has been reloaded.");
			return true;
		}
		return false;
	}

	@Override
	public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
		if (args.length == 1 && sender.hasPermission("lpc.reload")) {
			return Collections.singletonList("reload");
		}
		return Collections.emptyList();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onChat(final AsyncChatEvent event) {
		event.setCancelled(true);
		Player player = event.getPlayer();

		// ----------------------
		//  FETCH PREFIX / SUFFIX
		// ----------------------
		String prefix = "";
		String suffix = "";
		String primaryGroup = "default";

		if (hasLuckPerms) {
			CachedMetaData metaData = luckPerms.getPlayerAdapter(Player.class).getMetaData(player);
			prefix = metaData.getPrefix() != null ? metaData.getPrefix() : "";
			suffix = metaData.getSuffix() != null ? metaData.getSuffix() : "";
			primaryGroup = metaData.getPrimaryGroup();
		} else {
			// Fallback: check for vanilla permission-based groups
			if (player.hasPermission("group.admin")) {
				prefix = "&c[Admin]&r ";
			} else if (player.hasPermission("group.mod")) {
				prefix = "&2[Mod]&r ";
			} else {
				prefix = "&7[Player]&r ";
			}
		}

		Component prefixComponent = legacySerializer.deserialize(prefix);
		Component suffixComponent = legacySerializer.deserialize(suffix);

		// ----------------------
		//  FORMAT
		// ----------------------
		String world = player.getWorld().getName();
		String name = player.getName();
		String displayName = legacySerializer.serialize(player.displayName());

		String format = getConfig().getString("group-formats." + primaryGroup,
				getConfig().getString("chat-format", "{prefix}{displayname}{suffix}: {message}"));

		if (hasPAPI) {
			format = PlaceholderAPI.setPlaceholders(player, format);
		}

		Component formatComponent = legacySerializer.deserialize(format)
				.replaceText(builder -> builder.matchLiteral("{prefix}").replacement(prefixComponent))
				.replaceText(builder -> builder.matchLiteral("{suffix}").replacement(suffixComponent))
				.replaceText(builder -> builder.matchLiteral("{name}").replacement(name))
				.replaceText(builder -> builder.matchLiteral("{displayname}").replacement(displayName))
				.replaceText(builder -> builder.matchLiteral("{world}").replacement(world));

		// ----------------------
		//  MESSAGE
		// ----------------------
		String rawMessage = legacySerializer.serialize(event.message());
		if (hasPAPI && player.hasPermission("lpc.chat.placeholders")) {
			rawMessage = PlaceholderAPI.setPlaceholders(player, rawMessage);
		}

		Component messageComponent = parseMessage(player, rawMessage);

		// Replace {message} placeholder
		Component finalMessage = formatComponent.replaceText(builder ->
				builder.matchLiteral("{message}").replacement(messageComponent));

		// ----------------------
		//  SEND
		// ----------------------
		for (Audience viewer : event.viewers()) {
			viewer.sendMessage(finalMessage);
		}
	}

	private Component parseMessage(Player player, String rawMessage) {
		rawMessage = rawMessage.replace('§', '&');

		if (player.hasPermission("lpc.minimessage") && rawMessage.contains("<")) {
			return miniMessage.deserialize(rawMessage);
		} else if (player.hasPermission("lpc.legacycolor") || player.hasPermission("lpc.hex")) {
			return legacySerializer.deserialize(rawMessage);
		} else {
			return Component.text(rawMessage);
		}
	}
}
