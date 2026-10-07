package ace.actually.sableinbottle.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decodes the plot NBT stored inside a bottled ship into world block positions.
 * Shared by the Ponder preview and the placed bottle's block entity renderer so
 * both show exactly the same structure.
 */
public final class ShipStructureDecoder {

    private ShipStructureDecoder() {
    }

    /**
     * Decodes the {@code ship} compound of a bottle (plot chunks + pose) into
     * plot-local block positions, with the pose orientation applied so the result
     * matches the world frame the player saw before bottling.
     *
     * @param shipTag    the {@code ship} compound ({@code plot} + {@code pose})
     * @param minSection section Y index corresponding to section key 0
     * @return decoded blocks, or null when the tag holds nothing displayable
     */
    public static Map<BlockPos, BlockState> decode(CompoundTag shipTag, int minSection) {
        CompoundTag chunks = shipTag.getCompound("plot").getCompound("chunks");
        if (chunks.isEmpty()) return null;

        Map<BlockPos, BlockState> blocks = new HashMap<>();

        try {
            for (String chunkKey : chunks.getAllKeys()) {
                long packed = Long.parseLong(chunkKey);
                int chunkX = ChunkPos.getX(packed);
                int chunkZ = ChunkPos.getZ(packed);

                CompoundTag sections = chunks.getCompound(chunkKey).getCompound("sections");
                for (String sectionKey : sections.getAllKeys()) {
                    int sectionIndex = Integer.parseInt(sectionKey);
                    int sectionY = minSection + sectionIndex;

                    CompoundTag blockStates = sections.getCompound(sectionKey).getCompound("block_states");
                    ListTag paletteTag = blockStates.getList("palette", Tag.TAG_COMPOUND);
                    if (paletteTag.isEmpty()) continue;

                    List<BlockState> palette = new ArrayList<>(paletteTag.size());
                    boolean paletteOk = true;
                    for (int i = 0; i < paletteTag.size(); i++) {
                        BlockState state = BlockState.CODEC
                            .parse(NbtOps.INSTANCE, paletteTag.getCompound(i))
                            .result()
                            .orElse(null);
                        if (state == null) {
                            paletteOk = false;
                            break;
                        }
                        palette.add(state);
                    }
                    if (!paletteOk) continue;

                    int paletteSize = palette.size();
                    int bits = paletteSize <= 1
                        ? 0
                        : Math.max(4, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));

                    long[] dataLongs = bits == 0 ? null : blockStates.getLongArray("data");
                    int perLong = bits == 0 ? 1 : 64 / bits;
                    if (bits > 0 && (dataLongs == null || dataLongs.length < 4096 / perLong)) continue;

                    for (int i = 0; i < 4096; i++) {
                        int paletteIndex;
                        if (bits == 0) {
                            paletteIndex = 0;
                        } else {
                            long packedBits = dataLongs[i / perLong];
                            paletteIndex = (int) ((packedBits >>> ((i % perLong) * bits)) & ((1L << bits) - 1));
                        }
                        if (paletteIndex >= paletteSize) continue;

                        BlockState state = palette.get(paletteIndex);
                        if (state.isAir()) continue;

                        int x = i & 15;
                        int z = (i >> 4) & 15;
                        int y = i >> 8;
                        blocks.put(new BlockPos(chunkX * 16 + x, (sectionY * 16) + y, chunkZ * 16 + z), state);
                    }
                }
            }
        } catch (RuntimeException e) {
            return null;
        }

        if (blocks.isEmpty()) return null;

        // The plot stores geometry in the sublevel's body frame; the pose orientation
        // maps it to the world frame the player saw before bottling. Without this the
        // preview shows the raw plot frame, which can be upside down (verified against
        // saved bottles whose pose is a 180 degree flip). Snapping to the nearest cube
        // rotation keeps every block on the integer lattice.
        return applyPoseOrientation(blocks, shipTag.getCompound("pose").getCompound("orientation"));
    }

    /** Shifts blocks so their minimum corner sits at the origin. */
    public static Map<BlockPos, BlockState> normalize(Map<BlockPos, BlockState> blocks) {
        BlockPos min = minOf(blocks.keySet());
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        BlockPos delta = new BlockPos(-min.getX(), -min.getY(), -min.getZ());
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            out.put(entry.getKey().offset(delta), entry.getValue());
        }
        return out;
    }

    public static BlockPos minOf(Iterable<BlockPos> positions) {
        int x = Integer.MAX_VALUE, y = Integer.MAX_VALUE, z = Integer.MAX_VALUE;
        for (BlockPos pos : positions) {
            x = Math.min(x, pos.getX());
            y = Math.min(y, pos.getY());
            z = Math.min(z, pos.getZ());
        }
        return new BlockPos(x, y, z);
    }

    /** Size of the axis-aligned span: x = width-1, y = height-1, z = depth-1. */
    public static BlockPos maxSize(Iterable<BlockPos> positions) {
        BlockPos min = minOf(positions);
        int dx = 0, dy = 0, dz = 0;
        for (BlockPos pos : positions) {
            dx = Math.max(dx, pos.getX() - min.getX());
            dy = Math.max(dy, pos.getY() - min.getY());
            dz = Math.max(dz, pos.getZ() - min.getZ());
        }
        return new BlockPos(dx, dy, dz);
    }

    /** The 24 proper rotations of the cube, as row-major 3x3 matrices with det +1. */
    private static final int[][] CUBE_ROTATIONS = buildCubeRotations();

    private static int[][] buildCubeRotations() {
        List<int[]> out = new ArrayList<>(24);
        int[] perm = {0, 1, 2};
        do {
            for (int signs = 0; signs < 8; signs++) {
                int[] m = new int[9];
                int sx = (signs & 1) == 0 ? 1 : -1;
                int sy = (signs & 2) == 0 ? 1 : -1;
                int sz = (signs & 4) == 0 ? 1 : -1;
                m[perm[0]] = sx;
                m[3 + perm[1]] = sy;
                m[6 + perm[2]] = sz;
                int det = m[0] * (m[4] * m[8] - m[5] * m[7])
                    - m[1] * (m[3] * m[8] - m[5] * m[6])
                    + m[2] * (m[3] * m[7] - m[4] * m[6]);
                if (det > 0) out.add(m);
            }
        } while (nextPermutation(perm));
        return out.toArray(new int[0][]);
    }

    private static boolean nextPermutation(int[] a) {
        int i = a.length - 2;
        while (i >= 0 && a[i] >= a[i + 1]) i--;
        if (i < 0) return false;
        int j = a.length - 1;
        while (a[j] <= a[i]) j--;
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
        for (int l = i + 1, r = a.length - 1; l < r; l++, r--) {
            t = a[l];
            a[l] = a[r];
            a[r] = t;
        }
        return true;
    }

    /**
     * Rotates the decoded plot blocks by the bottle's stored pose orientation, replaced
     * by the closest axis-aligned cube rotation so the result stays a valid block grid.
     * Missing or degenerate orientations leave the blocks untouched.
     */
    private static Map<BlockPos, BlockState> applyPoseOrientation(
        Map<BlockPos, BlockState> blocks, CompoundTag orientation
    ) {
        if (orientation.isEmpty()) return blocks;

        double x = orientation.getDouble("x");
        double y = orientation.getDouble("y");
        double z = orientation.getDouble("z");
        double w = orientation.getDouble("w");
        double len = Math.sqrt(x * x + y * y + z * z + w * w);
        if (len < 1.0E-6) return blocks;
        x /= len;
        y /= len;
        z /= len;
        w /= len;

        double[] m = {
            1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
            2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
            2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y),
        };

        int[] best = CUBE_ROTATIONS[0];
        double bestScore = -Double.MAX_VALUE;
        for (int[] candidate : CUBE_ROTATIONS) {
            double score = 0;
            for (int i = 0; i < 9; i++) score += m[i] * candidate[i];
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos p = entry.getKey();
            out.put(new BlockPos(
                best[0] * p.getX() + best[1] * p.getY() + best[2] * p.getZ(),
                best[3] * p.getX() + best[4] * p.getY() + best[5] * p.getZ(),
                best[6] * p.getX() + best[7] * p.getY() + best[8] * p.getZ()
            ), entry.getValue());
        }
        return out;
    }
}
