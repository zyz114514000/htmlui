package com.htmlui.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.Collection;
import java.util.List;

/**
 * /htmlui 命令
 *
 * /htmlui                          列出页面（玩家自己用）
 * /htmlui list                     同上
 * /htmlui reload                   清空缓存（管理员）
 * /htmlui <pageId>                 给自己打开页面
 * /htmlui open <pageId> <player>   给指定玩家打开指定页面（管理员，控制台可用）
 * /htmlui open <pageId> @a         给所有玩家打开
 */
public class HtmlUiCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, HtmlLoader loader) {
        dispatcher.register(Commands.literal("htmlui")
                .executes(ctx -> listPages(ctx, loader))
                .then(Commands.literal("list").executes(ctx -> listPages(ctx, loader)))
                .then(Commands.literal("reload")
                        .requires(HtmlUiCommand::isGamemaster)
                        .executes(ctx -> reload(ctx, loader)))
                // 给指定玩家打开指定页面（管理员/控制台可用）
                .then(Commands.literal("open")
                        .requires(HtmlUiCommand::isGamemaster)
                        .then(Commands.argument("pageId", StringArgumentType.string())
                                .executes(ctx -> openForSelf(ctx, loader, StringArgumentType.getString(ctx, "pageId")))
                                .then(Commands.argument("target", EntityArgument.players())
                                        .executes(ctx -> openForTargets(ctx, loader,
                                                StringArgumentType.getString(ctx, "pageId"),
                                                EntityArgument.getPlayers(ctx, "target"))))))
                // 直接 /htmlui <pageId> 给自己打开
                .then(Commands.argument("pageId", StringArgumentType.string())
                        .executes(ctx -> openForSelf(ctx, loader, StringArgumentType.getString(ctx, "pageId"))))
        );
    }

    /** 权限检查：GAMEMASTERS（原版权限等级 2）及以上 */
    private static boolean isGamemaster(CommandSourceStack src) {
        var perms = src.permissions();
        if (perms == LevelBasedPermissionSet.ALL) return true;
        if (perms instanceof LevelBasedPermissionSet lvl) {
            return lvl.level().isEqualOrHigherThan(PermissionLevel.GAMEMASTERS);
        }
        return false;
    }

    private static int listPages(CommandContext<CommandSourceStack> ctx, HtmlLoader loader) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("仅玩家可用，控制台请用 /htmlui open <pageId> <player>"));
            return 0;
        }
        List<String> pages = loader.listPages();
        if (pages.isEmpty()) {
            player.sendSystemMessage(Component.literal("[HTML UI] 暂无页面").withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(Component.literal("目录: config/htmlui/").withStyle(ChatFormatting.GRAY));
        } else {
            player.sendSystemMessage(Component.literal("[HTML UI] 可用页面 (" + pages.size() + "):").withStyle(ChatFormatting.GOLD));
            for (String p : pages) player.sendSystemMessage(Component.literal("  - " + p).withStyle(ChatFormatting.WHITE));
            player.sendSystemMessage(Component.literal("使用 /htmlui <pageId> 打开").withStyle(ChatFormatting.GRAY));
        }
        return pages.size();
    }

    private static int reload(CommandContext<CommandSourceStack> ctx, HtmlLoader loader) {
        loader.clearCache();
        ctx.getSource().sendSuccess(() -> Component.literal("[HTML UI] 缓存已清空").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /** 给自己打开页面 */
    private static int openForSelf(CommandContext<CommandSourceStack> ctx, HtmlLoader loader, String pageId) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("仅玩家可用，控制台请用 /htmlui open <pageId> <player>"));
            return 0;
        }
        if (!loader.sendTo(player, pageId)) {
            player.sendSystemMessage(Component.literal("[HTML UI] 未找到页面: " + pageId).withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    /** 给指定玩家打开页面（管理员/控制台可用） */
    private static int openForTargets(CommandContext<CommandSourceStack> ctx, HtmlLoader loader,
                                       String pageId, Collection<ServerPlayer> targets) {
        if (targets.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("未找到目标玩家"));
            return 0;
        }
        int success = 0;
        for (ServerPlayer target : targets) {
            if (loader.sendTo(target, pageId)) {
                success++;
            } else {
                ctx.getSource().sendFailure(Component.literal("[HTML UI] 无法为 " + target.getName().getString()
                        + " 打开页面: " + pageId + "（页面不存在）").withStyle(ChatFormatting.RED));
            }
        }
        if (success > 0) {
            String names = targets.stream().map(p -> p.getName().getString())
                    .reduce((a, b) -> a + ", " + b).orElse("");
            final int count = success;
            ctx.getSource().sendSuccess(() -> Component.literal("[HTML UI] 已为 " + count + " 名玩家打开: " + pageId
                    + " (" + names + ")").withStyle(ChatFormatting.GREEN), true);
        }
        return success;
    }
}
