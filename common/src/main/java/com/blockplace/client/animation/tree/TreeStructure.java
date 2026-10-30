package com.blockplace.client.animation.tree;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import java.util.*;

/**
 * Holds all spatial, topological, and geometric information about a detected tree.
 */
public class TreeStructure {

    private final BlockPos hitPos;
    private final BlockPos rootPos;
    private final BlockPos minPos;
    private final BlockPos maxPos;

    private final int minSectionX;
    private final int minSectionY;
    private final int minSectionZ;
    private final int maxSectionX;
    private final int maxSectionY;
    private final int maxSectionZ;

    private final List<TreeBlockEntry> allBlocks;
    private final List<TreeBlockEntry> logs;
    private final List<TreeBlockEntry> leaves;
    private final Set<BlockPos> blockPositions;

    public TreeStructure(BlockPos hitPos, BlockPos rootPos, BlockPos minPos, BlockPos maxPos, List<TreeBlockEntry> entries) {
        this.hitPos = hitPos != null ? hitPos : rootPos;
        this.rootPos = rootPos;
        this.minPos = minPos;
        this.maxPos = maxPos;

        this.minSectionX = SectionPos.blockToSectionCoord(minPos.getX());
        this.minSectionY = SectionPos.blockToSectionCoord(minPos.getY());
        this.minSectionZ = SectionPos.blockToSectionCoord(minPos.getZ());
        this.maxSectionX = SectionPos.blockToSectionCoord(maxPos.getX());
        this.maxSectionY = SectionPos.blockToSectionCoord(maxPos.getY());
        this.maxSectionZ = SectionPos.blockToSectionCoord(maxPos.getZ());

        this.allBlocks = new ArrayList<>(entries);
        this.logs = new ArrayList<>();
        this.leaves = new ArrayList<>();
        this.blockPositions = new HashSet<>();

        for (TreeBlockEntry entry : entries) {
            this.blockPositions.add(entry.pos());
            if (entry.isLeaf()) {
                this.leaves.add(entry);
            } else {
                this.logs.add(entry);
            }
        }
    }

    public TreeStructure(BlockPos rootPos, BlockPos minPos, BlockPos maxPos, List<TreeBlockEntry> entries) {
        this(rootPos, rootPos, minPos, maxPos, entries);
    }

    public BlockPos getHitPos() {
        return hitPos;
    }

    public BlockPos getRootPos() {
        return rootPos;
    }

    public BlockPos getMinPos() {
        return minPos;
    }

    public BlockPos getMaxPos() {
        return maxPos;
    }

    public int getMinSectionX() {
        return minSectionX;
    }

    public int getMinSectionY() {
        return minSectionY;
    }

    public int getMinSectionZ() {
        return minSectionZ;
    }

    public int getMaxSectionX() {
        return maxSectionX;
    }

    public int getMaxSectionY() {
        return maxSectionY;
    }

    public int getMaxSectionZ() {
        return maxSectionZ;
    }

    public List<TreeBlockEntry> getAllBlocks() {
        return allBlocks;
    }

    public List<TreeBlockEntry> getLogs() {
        return logs;
    }

    public List<TreeBlockEntry> getLeaves() {
        return leaves;
    }

    public Set<BlockPos> getBlockPositions() {
        return blockPositions;
    }

    public boolean contains(BlockPos pos) {
        return blockPositions.contains(pos);
    }

    public void removeBlock(BlockPos pos) {
        if (blockPositions.remove(pos)) {
            allBlocks.removeIf(entry -> entry.pos().equals(pos));
            logs.removeIf(entry -> entry.pos().equals(pos));
            leaves.removeIf(entry -> entry.pos().equals(pos));
        }
    }

    public boolean isEmpty() {
        return allBlocks.isEmpty();
    }
}
