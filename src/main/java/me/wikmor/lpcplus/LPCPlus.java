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

import java.util.*;

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
        // Hook LuckPerms
        luckPerms = getServer().getServicesManager().load(LuckPerms.class);
        hasLuckPerms = luckPerms != null;

        // Check PlaceholderAPI
        hasPAPI = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        if (!hasLuckPerms) {
            getLogger().warning("LuckPerms is not installed! LPCPlus will use Bukkit fallback for prefixes/suffixes.");
            Component warn = miniMessage.deserialize("<red>[WARNING] LuckPerms is not detected! Using Bukkit fallback prefixes/suffixes.");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("lpc.adminnotify")) {
                    p.sendMessage(warn);
                }
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(miniMessage.deserialize("<green>LPCPlus v"
                    + getPluginMeta().getVersion()
                    + " <gray>by "
                    + String.join(", ", getPluginMeta().getAuthors())));
            sender.sendMessage(miniMessage.deserialize("<gray>Commands: <yellow>/lpcplus reload | /lpcplus about | /lpcplus version"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                reloadConfig();
                sender.sendMessage(miniMessage.deserialize("<green>LPCPlus configuration reloaded."));
                return true;
            }
            case "about" -> {
                sender.sendMessage(miniMessage.deserialize("<green>LPCPlus <gray>- Modern Chat Formatter"));
                String website = getPluginMeta().getWebsite();
                if (website != null) {
                    sender.sendMessage(miniMessage.deserialize("<gray>Website: <aqua>" + website));
                }
                return true;
            }
            case "version" -> {
                sender.sendMessage(miniMessage.deserialize("<yellow>LPCPlus <white>version <aqua>"
                        + getPluginMeta().getVersion() + " <white>by <green>" + String.join(", ", getPluginMeta().getAuthors())));
                return true;
            }
        }

        sender.sendMessage(miniMessage.deserialize("<red>Usage: /lpcplus reload | /lpcplus about | /lpcplus version"));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission("lpc.reload")) subs.add("reload");
            subs.add("about");
            subs.add("version");
            return subs;
        }
        return Collections.emptyList();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(final AsyncChatEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();

        // ----------------------
        // FETCH PREFIX / SUFFIX
        // ----------------------
        String prefix;
        String suffix;
        String primaryGroup = "default";

        if (hasLuckPerms) {
            CachedMetaData metaData = luckPerms.getPlayerAdapter(Player.class).getMetaData(player);
            prefix = Optional.ofNullable(metaData.getPrefix()).orElse("");
            suffix = Optional.ofNullable(metaData.getSuffix()).orElse("");
            primaryGroup = metaData.getPrimaryGroup();
        } else {
            prefix = getConfig().getString("vanilla-groups." + getPlayerGroup(player) + ".prefix", "&7[Player]&r ");
            suffix = getConfig().getString("vanilla-groups." + getPlayerGroup(player) + ".suffix", "");
        }

        Component prefixComponent = parseFormatted(prefix);
        Component suffixComponent = parseFormatted(suffix);

        // ----------------------
        // FORMAT
        // ----------------------
        String world = player.getWorld().getName();
        String name = player.getName();
        String displayName = legacySerializer.serialize(player.displayName());

        String format = getConfig().getString("group-formats." + primaryGroup,
                getConfig().getString("chat-format", "{prefix}{displayname}{suffix}: {message}"));

        if (hasPAPI) {
            format = PlaceholderAPI.setPlaceholders(player, format);
        }

        Component formatComponent = parseFormatted(format)
                .replaceText(builder -> builder.matchLiteral("{prefix}").replacement(prefixComponent))
                .replaceText(builder -> builder.matchLiteral("{suffix}").replacement(suffixComponent))
                .replaceText(builder -> builder.matchLiteral("{name}").replacement(Component.text(name)))
                .replaceText(builder -> builder.matchLiteral("{displayname}").replacement(Component.text(displayName)))
                .replaceText(builder -> builder.matchLiteral("{world}").replacement(Component.text(world)));

        // ----------------------
        // MESSAGE
        // ----------------------
        Component messageComponent = event.message(); // Paper already provides Component

        if (hasPAPI && player.hasPermission("lpc.chat.placeholders")) {
            String raw = LegacyComponentSerializer.legacySection().serialize(messageComponent);
            raw = PlaceholderAPI.setPlaceholders(player, raw);
            messageComponent = parseMessage(player, raw);
        }

        // Replace {message} placeholder
        Component finalMessageComponent = messageComponent;
        Component finalMessage = formatComponent.replaceText(builder ->
                builder.matchLiteral("{message}").replacement(finalMessageComponent));

        // ----------------------
        // SEND
        // ----------------------
        for (Audience viewer : event.viewers()) {
            viewer.sendMessage(finalMessage);
        }
    }

    private Component parseFormatted(String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        try {
            return miniMessage.deserialize(input);
        } catch (Exception ignored) {
            return legacySerializer.deserialize(input);
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

    private String getPlayerGroup(Player player) {
        if (player.hasPermission("group.admin")) return "admin";
        if (player.hasPermission("group.mod")) return "mod";
        return "default";
    }
}