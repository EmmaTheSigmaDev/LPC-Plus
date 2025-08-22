# LPCPlus

A modern chat formatting plugin for Minecraft servers using LuckPerms.

LPCPlus enhances your server's chat by integrating seamlessly with LuckPerms, supporting MiniMessage, Legacy/Hex formatting, and PlaceholderAPI. It’s lightweight, highly configurable, and optimized for Paper servers running Java 21.

---

## 🔧 Features

- **LuckPerms Integration:** Automatically formats chat based on player prefixes, suffixes, and groups.  
- **MiniMessage Support:** Parse and render advanced formatting using `<green>`, `<bold><red>`, and more.  
- **Legacy & Hex Colors:** Supports `&` codes and hex colors like `&#FF5733`.  
- **PlaceholderAPI Compatibility:** Expand chat formats with placeholders like `%luckperms_prefix%`.  
- **Configurable Formats:** Define group-specific formats and global defaults in `config.yml`.  
- **Paper-Only:** Built for Paper servers (1.13–1.21+).  

---

## 📦 Installation

1. Download the latest `.jar` from the [Releases](https://github.com/EmmaTheSigmaDev/LPC-Plus/releases) page.  
2. Place it in your server’s `plugins/` directory.  
3. Restart or reload your server.  

---

## ⚙️ Configuration

Upon first run, LPCPlus generates a `config.yml` file. You can customize:

- **Default Chat Format:** Set a global format for all players.  
- **Group-Specific Formats:** Define formats for different LuckPerms groups.  
- **MiniMessage in Formats:** Enable or disable MiniMessage parsing in formats.  
- **PlaceholderAPI Support:** Toggle support for PlaceholderAPI placeholders.  

Example `config.yml`:

```yaml
chat-format: "<gray>{prefix}{name}: {message}"
group-formats:
  admin: "<bold><red>{prefix}{name}: {message}</bold>"
  mod: "<yellow>{prefix}{name}: {message}</yellow>"
allow-minimessage-in-formats: true
