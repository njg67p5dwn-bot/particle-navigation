package com.particlenav;

import net.minecraft.core.BlockPos;

/**
 * A single node in the navigation path.
 * @param pos      block position (player feet level)
 * @param confirmed true = calculated by A*, false = estimated (straight-line)
 */
public record PathNode(BlockPos pos, boolean confirmed) {}
