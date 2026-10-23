package com.tabletopgames.command;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import com.tabletopgames.TabletopGamesPlugin;
import com.tabletopgames.ui.SettingsMenu;

/**
 * {@code /tabletopgames} 命令：<b>只做两件事</b> —— 打开全局配置表单、重载配置。
 *
 * <p>2026-10-22 用户要求：原来「发放棋盘 / 牌桌物品」的那一堆子命令全部删除，
 * 那些物品改成<b>创造物品栏里直接可拿</b>（见 {@code TabletopGamesPlugin} 里的
 * {@code Item.addCreativeItem} 注册）。所以这里不再需要 {@code give} 这类入口。</p>
 *
 * <pre>
 *   /tabletopgames            → 打开「全局配置」一级菜单（OP）
 *   /tabletopgames settings   → 同上
 *   /tabletopgames reload     → 重载配置（OP 或控制台）
 * </pre>
 */
public final class TabletopGamesCommand {

    private final TabletopGamesPlugin plugin;

    public TabletopGamesCommand(TabletopGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "";
        String root = command.getName().toLowerCase(java.util.Locale.ROOT);

        // 无参 与 settings 等价：直接进「全局配置」一级菜单
        if (sub.isEmpty() || "settings".equals(sub)) {
            if (!sender.isOp()) {
                sender.sendMessage("§c你没有权限使用该命令。");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§c设置表单只能在游戏内由 OP 玩家打开。");
                return true;
            }
            SettingsMenu.open(player);
            return true;
        }

        if ("reload".equals(sub)) {
            if (!sender.isOp()) {
                sender.sendMessage("§c你没有权限使用该命令。");
                return true;
            }
            sender.sendMessage("§7正在重载配置…");
            plugin.getManager().reloadConfig(sender);
            return true;
        }

        sender.sendMessage("§e用法：§f/" + root + " §7（打开全局配置）§8·§f /" + root
                + " reload §7（重载配置）");
        return true;
    }
}
