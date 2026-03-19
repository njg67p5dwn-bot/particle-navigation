package com.particlenav;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Time-sliced A* pathfinder that runs on the main thread.
 * Processes a limited number of nodes per tick to avoid lag.
 */
public class AStarPathfinder {

    private static final int MAX_ITERATIONS = 100_000;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};

    private final ClientLevel level;
    private final BlockPos goal;

    private final PriorityQueue<Node> openSet;
    private final Map<Long, Node> allNodes;
    private int iterations;
    private boolean finished;
    private List<PathNode> result;
    private Node bestNode;

    public AStarPathfinder(ClientLevel level, BlockPos start, BlockPos goal) {
        this.level = level;
        this.goal = goal;
        this.openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        this.allNodes = new HashMap<>();
        this.iterations = 0;
        this.finished = false;
        this.result = null;

        Node startNode = new Node(start, null, 0, heuristic(start, goal));
        openSet.add(startNode);
        allNodes.put(startNode.pos.asLong(), startNode);
        bestNode = startNode;
    }

    public boolean tick(long maxMs) {
        if (finished) return true;

        long deadline = System.nanoTime() + maxMs * 1_000_000;

        while (!openSet.isEmpty() && System.nanoTime() < deadline && iterations < MAX_ITERATIONS) {
            iterations++;
            Node current = openSet.poll();

            if (current.closed) continue;
            current.closed = true;

            if (heuristic(current.pos, goal) < heuristic(bestNode.pos, goal)) {
                bestNode = current;
            }

            if (current.pos.equals(goal) || current.pos.distManhattan(goal) <= 1) {
                result = reconstructPath(current, true);
                finished = true;
                return true;
            }

            expandNeighbors(current);
        }

        if (openSet.isEmpty() || iterations >= MAX_ITERATIONS) {
            result = reconstructPath(bestNode, false);
            finished = true;
            return true;
        }

        return false;
    }

    private void expandNeighbors(Node current) {
        int cx = current.pos.getX();
        int cy = current.pos.getY();
        int cz = current.pos.getZ();

        for (int i = 0; i < 4; i++) {
            int nx = cx + DX[i];
            int nz = cz + DZ[i];

            // Walk (same level)
            tryAddNeighbor(current, new BlockPos(nx, cy, nz), 1.0);

            // Jump up 1 block
            BlockPos jumpTarget = new BlockPos(nx, cy + 1, nz);
            if (canJumpTo(current.pos, jumpTarget)) {
                tryAddNeighbor(current, jumpTarget, 1.4);
            }

            // Fall down 1-3 blocks
            for (int drop = 1; drop <= 3; drop++) {
                BlockPos fallTarget = new BlockPos(nx, cy - drop, nz);
                if (canFallTo(current.pos, nx, nz, drop)) {
                    tryAddNeighbor(current, fallTarget, 1.0 + drop * 0.3);
                    break;
                }
            }
        }

        // Climb up (ladder, vine, water)
        BlockPos upPos = new BlockPos(cx, cy + 1, cz);
        if (canClimbTo(upPos)) {
            tryAddNeighbor(current, upPos, 1.5);
        }

        // Descend (ladder, vine, water)
        BlockPos downPos = new BlockPos(cx, cy - 1, cz);
        if (canDescendTo(current.pos, downPos)) {
            tryAddNeighbor(current, downPos, 1.0);
        }
    }

    private void tryAddNeighbor(Node current, BlockPos pos, double moveCost) {
        if (!isChunkLoaded(pos)) return;
        if (!isStandable(pos)) return;

        double newG = current.g + moveCost;
        long key = pos.asLong();
        Node existing = allNodes.get(key);

        if (existing != null) {
            if (existing.closed || newG >= existing.g) return;
            existing.g = newG;
            existing.f = newG + existing.h;
            existing.parent = current;
            openSet.add(existing);
        } else {
            double h = heuristic(pos, goal);
            Node node = new Node(pos, current, newG, h);
            allNodes.put(key, node);
            openSet.add(node);
        }
    }

    private boolean isStandable(BlockPos feetPos) {
        return isSolidGround(feetPos.below())
                && isPassable(feetPos)
                && isPassable(feetPos.above());
    }

    private boolean canJumpTo(BlockPos from, BlockPos to) {
        if (!isChunkLoaded(to)) return false;
        if (!isPassable(from.above().above())) return false;
        return isStandable(to);
    }

    private boolean canFallTo(BlockPos from, int nx, int nz, int drop) {
        int y = from.getY();
        BlockPos target = new BlockPos(nx, y - drop, nz);
        if (!isChunkLoaded(target)) return false;
        if (!isStandable(target)) return false;

        for (int dy = 0; dy >= -drop + 1; dy--) {
            BlockPos check = new BlockPos(nx, y + dy, nz);
            if (!isPassable(check)) return false;
            if (!isPassable(check.above())) return false;
        }
        return true;
    }

    private boolean canClimbTo(BlockPos pos) {
        if (!isChunkLoaded(pos)) return false;
        if (!isPassable(pos) || !isPassable(pos.above())) return false;

        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.LADDER)
                || state.is(Blocks.VINE)
                || state.is(Blocks.WATER)
                || state.is(Blocks.TWISTING_VINES)
                || state.is(Blocks.TWISTING_VINES_PLANT);
    }

    private boolean canDescendTo(BlockPos from, BlockPos to) {
        if (!isChunkLoaded(to)) return false;
        BlockState state = level.getBlockState(to);
        boolean climbable = state.is(Blocks.LADDER)
                || state.is(Blocks.VINE)
                || state.is(Blocks.WATER);
        return climbable && isPassable(to) && isPassable(to.above());
    }

    private boolean isPassable(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SWEET_BERRY_BUSH)) {
            return false;
        }
        return state.getCollisionShape(level, pos).isEmpty();
    }

    private boolean isSolidGround(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private boolean isChunkLoaded(BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static double heuristic(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private List<PathNode> reconstructPath(Node endNode, boolean reachedGoal) {
        List<PathNode> path = new ArrayList<>();
        Node current = endNode;
        while (current != null) {
            path.add(new PathNode(current.pos, true));
            current = current.parent;
        }
        Collections.reverse(path);

        if (!reachedGoal && !path.isEmpty()) {
            BlockPos lastPos = path.getLast().pos();
            addEstimatedPath(path, lastPos, goal);
        }

        return path;
    }

    private void addEstimatedPath(List<PathNode> path, BlockPos from, BlockPos to) {
        double dist = heuristic(from, to);
        int steps = (int) Math.ceil(dist);
        if (steps <= 0) return;

        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) Math.round(from.getX() + (to.getX() - from.getX()) * t);
            int y = (int) Math.round(from.getY() + (to.getY() - from.getY()) * t);
            int z = (int) Math.round(from.getZ() + (to.getZ() - from.getZ()) * t);
            path.add(new PathNode(new BlockPos(x, y, z), false));
        }
    }

    public boolean isFinished() { return finished; }
    public List<PathNode> getResult() { return result; }

    private static class Node {
        final BlockPos pos;
        final double h;
        Node parent;
        double g;
        double f;
        boolean closed;

        Node(BlockPos pos, Node parent, double g, double h) {
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.h = h;
            this.f = g + h;
            this.closed = false;
        }
    }
}
