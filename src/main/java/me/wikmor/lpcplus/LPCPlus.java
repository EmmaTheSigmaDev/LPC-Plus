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
                    .hexColors() // supports &#RRGGBB
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

        // Warn in console and notify online admins if LuckPerms isn't installed
        if (!hasLuckPerms) {
            getLogger().warning("LuckPerms is not installed! LPCPlus will use Bukkit fallback for prefixes/suffixes.");

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("lpc.adminnotify")) {
                    p.sendMessage(Component.text("§c[WARNING] LuckPerms is not detected! Using Bukkit fallback prefixes/suffixes."));
                }
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§aLPCPlus v" + getPluginMeta().getVersion() + " by " + getPluginMeta().getAuthors());
            sender.sendMessage("§7Commands: /lpcplus reload | /lpcplus about | /lpcplus version");
            return true;
        }

        if (args.length == 1) {
            switch (args[0].toLowerCase()) {
                case "reload":
                    reloadConfig();
                    sender.sendMessage("§aLPCPlus configuration reloaded.");
                    return true;

                case "about":
                    sender.sendMessage("§aLPCPlus §7- Modern Chat Formatter");
                    String website = getPluginMeta().getWebsite();
                    sender.sendMessage("§7Website: " + (website != null ? website : "N/A"));
                    return true;
                case "version":
                    String version = getPluginMeta().getVersion();
                    String author = String.join(", ", getPluginMeta().getAuthors());
                    sender.sendMessage("§eLPCPlus §fversion §b" + version + " §fby §a" + author);
                    return true;
            }
        }

        sender.sendMessage("§cUsage: /lpcplus reload | /lpcplus about");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission("lpc.reload")) subs.add("reload");
            subs.add("about");
            return subs;
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
        String prefix;
        String suffix;
        String primaryGroup = "default";

        if (hasLuckPerms) {
            CachedMetaData metaData = luckPerms.getPlayerAdapter(Player.class).getMetaData(player);
            prefix = metaData.getPrefix() != null ? metaData.getPrefix() : "";
            suffix = metaData.getSuffix() != null ? metaData.getSuffix() : "";
            primaryGroup = metaData.getPrimaryGroup();
        } else {
            // Fallback: read from config
            prefix = getConfig().getString("vanilla-groups." + getPlayerGroup(player) + ".prefix", "&7[Player]&r ");
            suffix = getConfig().getString("vanilla-groups." + getPlayerGroup(player) + ".suffix", "");
        }

        Component prefixComponent = parseFormatted(prefix);
        Component suffixComponent = parseFormatted(suffix);

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

        Component formatComponent = parseFormatted(format)
                .replaceText(builder -> builder.matchLiteral("{prefix}").replacement(prefixComponent))
                .replaceText(builder -> builder.matchLiteral("{suffix}").replacement(suffixComponent))
                .replaceText(builder -> builder.matchLiteral("{name}").replacement(Component.text(name)))
                .replaceText(builder -> builder.matchLiteral("{displayname}").replacement(Component.text(displayName)))
                .replaceText(builder -> builder.matchLiteral("{world}").replacement(Component.text(world)));

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

    private Component parseFormatted(String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        if (input.contains("<")) {
            return miniMessage.deserialize(input);
        } else {
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

    // Determine fallback group based on vanilla permissions
    private String getPlayerGroup(Player player) {
        if (player.hasPermission("group.admin")) return "admin";
        if (player.hasPermission("group.mod")) return "mod";
        return "default";
    }
}