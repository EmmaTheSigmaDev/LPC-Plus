/*
 * LPCPlus - A modern chat formatting plugin for Minecraft servers using LuckPerms
 * Released into the public domain under the Unlicense
 * http://unlicense.org/
 * Author: Emma_TheSigma
 */
package me.wikmor.lpc;

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
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class LPCPlus extends JavaPlugin implements Listener {

	private LuckPerms luckPerms;
	private final MiniMessage miniMessage = MiniMessage.miniMessage();
	private final LegacyComponentSerializer legacySerializer =
			LegacyComponentSerializer.builder()
					.character('&')
					.hexColors()
					.useUnusualXRepeatedCharacterHexFormat()
					.build();

	@Override
	public void onEnable() {
		this.luckPerms = getServer().getServicesManager().load(LuckPerms.class);
		saveDefaultConfig();
		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
		if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
			reloadConfig();
			sender.sendMessage("§aLPC has been reloaded.");
			return true;
		}
		return false;
	}

	@Override
	public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
		if (args.length == 1) return Collections.singletonList("reload");
		return new ArrayList<>();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onChat(final AsyncChatEvent event) {
		event.setCancelled(true);

		Player player = event.getPlayer();

		// ----------------------
		//  FORMAT PLACEHOLDERS
		// ----------------------
		CachedMetaData metaData = luckPerms.getPlayerAdapter(Player.class).getMetaData(player);

		String prefix = Optional.of(metaData).map(CachedMetaData::getPrefix).orElse("");
		String suffix = Optional.of(metaData).map(CachedMetaData::getSuffix).orElse("");
		String prefixes = Optional.of(metaData)
				.map(m -> String.join("", m.getPrefixes().values()))
				.orElse("");
		String suffixes = Optional.of(metaData)
				.map(m -> String.join("", m.getSuffixes().values()))
				.orElse("");
		String world = Optional.of(player.getWorld()).map(WorldInfo::getName).orElse("world");
		String name = Optional.of(player.getName()).orElse("Player");
		String displayName = Optional.of(player.displayName())
				.map(comp -> LegacyComponentSerializer.legacySection().serialize(comp))
				.orElse(name);

		String format = getConfig().getString("group-formats." + metaData.getPrimaryGroup(),
				getConfig().getString("chat-format", "<gray>{name}: {message}"));

		format = format
				.replace("{prefix}", prefix)
				.replace("{suffix}", suffix)
				.replace("{prefixes}", prefixes)
				.replace("{suffixes}", suffixes)
				.replace("{world}", world)
				.replace("{name}", name)
				.replace("{displayname}", displayName);

		// Always expand PlaceholderAPI in formats
		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			format = PlaceholderAPI.setPlaceholders(player, format);
		}

		// ----------------------
		//  MESSAGE PLACEHOLDERS
		// ----------------------
		String rawMessage = LegacyComponentSerializer.legacySection().serialize(event.message());

		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
				&& player.hasPermission("lpc.chat.placeholders")) {
			rawMessage = PlaceholderAPI.setPlaceholders(player, rawMessage);
		}

		Component messageComponent;
		if (player.hasPermission("lpc.minimessage") && rawMessage.contains("<")) {
			messageComponent = miniMessage.deserialize(rawMessage);
		} else if (player.hasPermission("lpc.legacycolor") || player.hasPermission("lpc.hex")) {
			messageComponent = legacySerializer.deserialize(rawMessage);
		} else {
			messageComponent = Component.text(rawMessage);
		}

		// ----------------------
		//  FORMAT PARSING
		// ----------------------
		boolean allowMiniInFormats = getConfig().getBoolean("allow-minimessage-in-formats", true);
		boolean canUseMiniInFormat = player.hasPermission("lpc.format.minimessage");

		Component formatComponent;
		if (allowMiniInFormats && format.contains("<") && canUseMiniInFormat) {
			formatComponent = miniMessage.deserialize(format);
		} else {
			formatComponent = legacySerializer.deserialize(format);
		}

		// Replace {message} in format
		Component finalMessage = formatComponent.replaceText(builder ->
				builder.matchLiteral("{message}").replacement(messageComponent));

		// ----------------------
		//  SEND TO ALL VIEWERS
		// ----------------------
		for (Audience viewer : event.viewers()) {
			viewer.sendMessage(finalMessage);
		}
	}
}