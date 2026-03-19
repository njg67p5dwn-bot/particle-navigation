package com.particlenav;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/**
 * Client-side commands: /nav set|stop|info
 */
public class NavCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommandManager.literal("nav")
                            .then(ClientCommandManager.literal("set")
                                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                                    .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                                            .executes(ctx -> {
                                                                int x = IntegerArgumentType.getInteger(ctx, "x");
                                                                int y = IntegerArgumentType.getInteger(ctx, "y");
                                                                int z = IntegerArgumentType.getInteger(ctx, "z");
                                                                NavigationManager.getInstance().setTarget(new BlockPos(x, y, z));
                                                                return 1;
                                                            })))))
                            .then(ClientCommandManager.literal("stop")
                                    .executes(ctx -> {
                                        NavigationManager.getInstance().stop();
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("mark")
                                    .executes(ctx -> {
                                        NavigationManager.getInstance().markPosition();
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("back")
                                    .executes(ctx -> {
                                        NavigationManager.getInstance().navigateToMark();
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("info")
                                    .executes(ctx -> {
                                        NavigationManager mgr = NavigationManager.getInstance();
                                        FabricClientCommandSource source = ctx.getSource();
                                        if (mgr.isActive() && mgr.getTarget() != null) {
                                            BlockPos t = mgr.getTarget();
                                            source.sendFeedback(
                                                    Component.literal("[Nav] 현재 목표: "
                                                                    + t.getX() + ", " + t.getY() + ", " + t.getZ())
                                                            .withStyle(ChatFormatting.GREEN));
                                        } else {
                                            source.sendFeedback(
                                                    Component.literal("[Nav] 활성화된 네비게이션 없음")
                                                            .withStyle(ChatFormatting.GRAY));
                                        }
                                        if (mgr.getMarkedPos() != null) {
                                            BlockPos m = mgr.getMarkedPos();
                                            source.sendFeedback(
                                                    Component.literal("[Nav] 저장된 위치: "
                                                                    + m.getX() + ", " + m.getY() + ", " + m.getZ())
                                                            .withStyle(ChatFormatting.AQUA));
                                        }
                                        return 1;
                                    }))
            );
        });
    }
}
