package com.particlenav;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Renders the navigation path using colored dust particles.
 * Green = confirmed (A* calculated), Yellow = estimated (straight-line).
 */
public class PathRenderer {

    // Mojang mappings: DustParticleOptions(int color, float scale)
    private static final DustParticleOptions GREEN_PARTICLE =
            new DustParticleOptions(0x00FF33, 1.2f);
    private static final DustParticleOptions YELLOW_PARTICLE =
            new DustParticleOptions(0xFFFF00, 1.0f);
    private static final DustParticleOptions RED_PARTICLE =
            new DustParticleOptions(0xFF3300, 1.5f);

    private static final double RENDER_DISTANCE = 60.0;
    private static final int PARTICLE_INTERVAL_TICKS = 3;

    private int tickCounter = 0;

    public void tick(List<PathNode> path) {
        tickCounter++;
        if (tickCounter % PARTICLE_INTERVAL_TICKS != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        ClientLevel level = mc.level;
        Vec3 playerPos = mc.player.position();

        int startIndex = findClosestNodeIndex(path, playerPos);

        for (int i = Math.max(0, startIndex - 5); i < path.size(); i++) {
            PathNode node = path.get(i);
            Vec3 center = Vec3.atCenterOf(node.pos());
            double dist = playerPos.distanceTo(center);

            if (dist > RENDER_DISTANCE) continue;

            DustParticleOptions particle = node.confirmed() ? GREEN_PARTICLE : YELLOW_PARTICLE;

            level.addParticle(particle,
                    center.x, center.y, center.z,
                    0, 0.02, 0);

            // Interpolated particles for smooth trail
            if (i + 1 < path.size()) {
                PathNode next = path.get(i + 1);
                double nodeDist = Math.sqrt(node.pos().distSqr(next.pos()));
                if (nodeDist > 1.5) {
                    Vec3 nextCenter = Vec3.atCenterOf(next.pos());
                    int steps = (int) Math.ceil(nodeDist);
                    for (int s = 1; s < steps; s++) {
                        double t = (double) s / steps;
                        double ix = center.x + (nextCenter.x - center.x) * t;
                        double iy = center.y + (nextCenter.y - center.y) * t;
                        double iz = center.z + (nextCenter.z - center.z) * t;
                        level.addParticle(particle, ix, iy, iz, 0, 0.01, 0);
                    }
                }
            }
        }

        // Beacon column at goal
        if (!path.isEmpty()) {
            PathNode goalNode = path.getLast();
            Vec3 goalCenter = Vec3.atCenterOf(goalNode.pos());
            if (playerPos.distanceTo(goalCenter) < RENDER_DISTANCE) {
                for (int dy = 0; dy < 5; dy++) {
                    level.addParticle(RED_PARTICLE,
                            goalCenter.x, goalCenter.y + dy, goalCenter.z,
                            0, 0.05, 0);
                }
            }
        }
    }

    private int findClosestNodeIndex(List<PathNode> path, Vec3 playerPos) {
        int closest = 0;
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            double dist = playerPos.distanceToSqr(Vec3.atCenterOf(path.get(i).pos()));
            if (dist < minDist) {
                minDist = dist;
                closest = i;
            }
        }
        return closest;
    }
}
