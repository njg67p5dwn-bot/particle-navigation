package com.particlenav;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central navigation manager. Handles target setting, path calculation,
 * chunk-load recalculation, and rendering coordination.
 */
public class NavigationManager {

    private static final NavigationManager INSTANCE = new NavigationManager();
    private static final long TICK_BUDGET_MS = 3;
    private static final int RECALC_COOLDOWN_TICKS = 40;
    private static final double RECALC_DISTANCE = 5.0;

    private BlockPos target;
    private List<PathNode> currentPath = new CopyOnWriteArrayList<>();
    private AStarPathfinder pathfinder;
    private final PathRenderer renderer = new PathRenderer();

    private int recalcCooldown = 0;
    private boolean active = false;

    public static NavigationManager getInstance() {
        return INSTANCE;
    }

    public void setTarget(BlockPos target) {
        this.target = target;
        this.active = true;
        this.currentPath.clear();
        startPathfinding();

        sendMessage(Component.literal("[Nav] ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal("목표 설정: " + target.getX() + ", " + target.getY() + ", " + target.getZ())
                        .withStyle(ChatFormatting.WHITE)));
    }

    public void stop() {
        this.active = false;
        this.target = null;
        this.pathfinder = null;
        this.currentPath.clear();

        sendMessage(Component.literal("[Nav] ")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal("네비게이션 종료")
                        .withStyle(ChatFormatting.WHITE)));
    }

    public void tick() {
        if (!active || target == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (pathfinder != null && !pathfinder.isFinished()) {
            boolean done = pathfinder.tick(TICK_BUDGET_MS);
            if (done) {
                onPathfindingComplete();
            }
        }

        if (recalcCooldown > 0) recalcCooldown--;

        if (!currentPath.isEmpty() && pathfinder != null && pathfinder.isFinished()) {
            checkDeviation(mc.player);
        }

        if (!currentPath.isEmpty()) {
            renderer.tick(currentPath);
        }

        showActionBar(mc.player);
    }

    public void onChunkLoad(int chunkX, int chunkZ) {
        if (!active || target == null || currentPath.isEmpty()) return;
        if (recalcCooldown > 0) return;

        boolean shouldRecalc = false;
        for (PathNode node : currentPath) {
            if (!node.confirmed()) {
                int nodeChunkX = node.pos().getX() >> 4;
                int nodeChunkZ = node.pos().getZ() >> 4;
                if (nodeChunkX == chunkX && nodeChunkZ == chunkZ) {
                    shouldRecalc = true;
                    break;
                }
            }
        }

        if (shouldRecalc) {
            startPathfinding();
            recalcCooldown = RECALC_COOLDOWN_TICKS;
        }
    }

    private void startPathfinding() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        BlockPos start = mc.player.blockPosition();
        pathfinder = new AStarPathfinder(mc.level, start, target);
    }

    private void onPathfindingComplete() {
        if (pathfinder == null) return;

        List<PathNode> result = pathfinder.getResult();
        if (result != null && !result.isEmpty()) {
            currentPath.clear();
            currentPath.addAll(result);

            long confirmed = result.stream().filter(PathNode::confirmed).count();
            long estimated = result.size() - confirmed;

            MutableComponent msg = Component.literal("[Nav] ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal("경로 계산 완료! ")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(confirmed + "블록 확정")
                            .withStyle(ChatFormatting.GREEN));
            if (estimated > 0) {
                msg.append(Component.literal(" + " + estimated + "블록 추정")
                        .withStyle(ChatFormatting.YELLOW));
            }
            sendMessage(msg);
        } else {
            sendMessage(Component.literal("[Nav] ")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal("경로를 찾을 수 없습니다!")
                            .withStyle(ChatFormatting.RED)));
        }
    }

    private void checkDeviation(LocalPlayer player) {
        if (currentPath.isEmpty() || recalcCooldown > 0) return;

        Vec3 playerPos = player.position();
        double minDist = Double.MAX_VALUE;
        for (PathNode node : currentPath) {
            double dist = playerPos.distanceTo(Vec3.atCenterOf(node.pos()));
            if (dist < minDist) minDist = dist;
        }

        if (minDist > RECALC_DISTANCE) {
            startPathfinding();
            recalcCooldown = RECALC_COOLDOWN_TICKS;
        }
    }

    private void showActionBar(LocalPlayer player) {
        if (target == null) return;

        double distance = player.position().distanceTo(Vec3.atCenterOf(target));
        String distStr = String.format("%.1f", distance);

        if (distance < 3.0) {
            player.displayClientMessage(
                    Component.literal("  목표 도착!  ")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                    true);
            return;
        }

        Vec3 toTarget = Vec3.atCenterOf(target).subtract(player.position()).normalize();
        float playerYaw = player.getYRot();
        double targetAngle = Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        double relativeAngle = ((targetAngle - playerYaw) % 360 + 360) % 360;
        String arrow = getDirectionArrow(relativeAngle);

        boolean searching = pathfinder != null && !pathfinder.isFinished();
        String status = searching ? " [탐색중...]" : "";

        player.displayClientMessage(
                Component.literal(arrow + " ")
                        .withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(distStr + "m")
                                .withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(status)
                                .withStyle(ChatFormatting.GRAY)),
                true);
    }

    private String getDirectionArrow(double angle) {
        if (angle >= 337.5 || angle < 22.5) return "\u2191";
        if (angle < 67.5) return "\u2197";
        if (angle < 112.5) return "\u2192";
        if (angle < 157.5) return "\u2198";
        if (angle < 202.5) return "\u2193";
        if (angle < 247.5) return "\u2199";
        if (angle < 292.5) return "\u2190";
        return "\u2196";
    }

    private void sendMessage(Component text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(text, false);
        }
    }

    public boolean isActive() { return active; }
    public BlockPos getTarget() { return target; }
}
