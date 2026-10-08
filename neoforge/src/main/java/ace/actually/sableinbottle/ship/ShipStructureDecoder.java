package ace.actually.sableinbottle.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
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

    private static final int[] ROT_IDENTITY = {1, 0, 0, 0, 1, 0, 0, 0, 1};

    /**
     * One representative per coset of the yaw subgroup, so every cube rotation R
     * decomposes uniquely as R = yaw * base. yaw is handled by vanilla
     * {@link Rotation}; base needs manual property handling (see rotateState).
     */
    private static final int[][] FLIP_BASES = {
        {1, 0, 0, 0, 1, 0, 0, 0, 1},        // identity
        {1, 0, 0, 0, -1, 0, 0, 0, -1},      // 180 deg about X (upside down)
        {1, 0, 0, 0, 0, -1, 0, 1, 0},       // 90 deg about X
        {1, 0, 0, 0, 0, 1, 0, -1, 0},       // 270 deg about X
        {0, -1, 0, 1, 0, 0, 0, 0, 1},       // 90 deg about Z
        {0, 1, 0, -1, 0, 0, 0, 0, 1},       // 270 deg about Z
    };

    /** Inverses of FLIP_BASES (transpose of each rotation). */
    private static final int[][] FLIP_BASES_INVERSE = {
        {1, 0, 0, 0, 1, 0, 0, 0, 1},
        {1, 0, 0, 0, -1, 0, 0, 0, -1},
        {1, 0, 0, 0, 0, 1, 0, -1, 0},
        {1, 0, 0, 0, 0, -1, 0, 1, 0},
        {0, 1, 0, -1, 0, 0, 0, 0, 1},
        {0, -1, 0, 1, 0, 0, 0, 0, -1},
    };

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
            ), rotateState(entry.getValue(), best));
        }
        return out;
    }

    /**
     * Rotates a block state by the same cube rotation applied to positions, so
     * facings/axes/halves match what the ship looked like in the world (the
     * sublevel is rendered with the pose baked into the mesh).
     *
     * <p>The rotation is split into a yaw part and a base part:
     * {@code m = yaw * base}. The yaw goes through vanilla {@link Rotation},
     * which every block already implements correctly (stairs shapes, rails,
     * walls, ...). The base part - one of six flip/tilt representatives - is
     * applied property by property: directions and axes are transformed by the
     * matrix, TOP/BOTTOM and UPPER/LOWER swap when the base inverts Y, and
     * left/right labels swap with it. Properties a base cannot express (a ship
     * rolled onto its side turns horizontal facings vertical, which e.g. a
     * furnace cannot represent) keep their original value - a best effort, and
     * the only option, since vanilla block states cannot express those poses.
     */
    private static BlockState rotateState(BlockState state, int[] m) {
        if (isIdentity(m)) return state;

        // Decompose m = yaw * base; base is the unique representative with the
        // same image of +Y as m (yaw fixes +Y).
        int base = -1;
        int[] yaw = null;
        for (int i = 0; i < FLIP_BASES.length; i++) {
            int[] candidate = multiplyMatrices(m, FLIP_BASES_INVERSE[i]);
            if (isYawRotation(candidate)) {
                base = i;
                yaw = candidate;
                break;
            }
        }
        if (base < 0) return state; // unreachable for cube rotations

        BlockState out = state;
        if (base != 0) out = applyBaseRotation(out, FLIP_BASES[base]);
        Rotation rotation = yawRotationOf(yaw);
        if (rotation != Rotation.NONE) out = out.rotate(rotation);
        return out;
    }

    private static boolean isIdentity(int[] m) {
        for (int i = 0; i < 9; i++) {
            if (m[i] != ROT_IDENTITY[i]) return false;
        }
        return true;
    }

    /** A proper rotation about +Y keeps Y fixed and leaves the XZ plane invariant. */
    private static boolean isYawRotation(int[] m) {
        return m[3] == 0 && m[4] == 1 && m[5] == 0 && m[7] == 0;
    }

    private static int[] multiplyMatrices(int[] a, int[] b) {
        int[] r = new int[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                r[row * 3 + col] =
                    a[row * 3] * b[col]
                        + a[row * 3 + 1] * b[3 + col]
                        + a[row * 3 + 2] * b[6 + col];
            }
        }
        return r;
    }

    /** Maps a yaw matrix to vanilla's Rotation via where it sends EAST. */
    private static Rotation yawRotationOf(int[] m) {
        // image of EAST = first column (m0, m3, m6)
        int ex = m[0], ey = m[3], ez = m[6];
        if (ex == 1 && ey == 0 && ez == 0) return Rotation.NONE;
        if (ex == 0 && ey == 0 && ez == 1) return Rotation.CLOCKWISE_90;   // EAST -> SOUTH
        if (ex == -1 && ey == 0 && ez == 0) return Rotation.CLOCKWISE_180;
        if (ex == 0 && ey == 0 && ez == -1) return Rotation.COUNTERCLOCKWISE_90; // EAST -> NORTH
        return Rotation.NONE;
    }

    /** Applies one of the five non-identity base rotations to a block state. */
    private static BlockState applyBaseRotation(BlockState state, int[] base) {
        boolean invertY = base[1] == 0 && base[4] == -1 && base[7] == 0; // +Y maps to -Y
        Map<Property<?>, Object> changes = new LinkedHashMap<>();

        for (Property<?> property : state.getProperties()) {
            Object value = state.getValue(property);
            if (value instanceof Direction dir) {
                Direction rotated = transformDirection(dir, base);
                if (rotated != null && property.getPossibleValues().contains(rotated)) {
                    changes.put(property, rotated);
                }
            } else if (value instanceof Direction.Axis axis) {
                Direction rotated = transformDirection(axisDirection(axis), base);
                if (rotated != null) {
                    Direction.Axis newAxis = rotated.getAxis();
                    if (property.getPossibleValues().contains(newAxis)) {
                        changes.put(property, newAxis);
                    }
                }
            } else if (value instanceof RailShape shape && invertY) {
                RailShape mapped = mapRailShapeFlip(shape);
                if (mapped != null) changes.put(property, mapped);
            } else if (value instanceof Boolean connected && isConnectionName(property.getName())) {
                // walls/fences/panes: connections follow their side direction
                Direction side = directionByName(property.getName());
                Direction source = side == null ? null : transformDirection(side, invert(base));
                Boolean old = Boolean.FALSE;
                if (source != null) {
                    for (Property<?> other : state.getProperties()) {
                        if (other.getName().equals(source.getName())
                            && state.getValue(other) instanceof Boolean b) {
                            old = b;
                            break;
                        }
                    }
                }
                changes.put(property, old);
            } else if (value instanceof Enum<?> enumValue && invertY) {
                String flipped = flipEnumName(enumValue.name());
                if (flipped != null && !flipped.equals(enumValue.name())) {
                    for (Object candidate : property.getPossibleValues()) {
                        if (candidate instanceof Enum<?> ce && ce.name().equals(flipped)) {
                            changes.put(property, candidate);
                            break;
                        }
                    }
                }
            }
        }

        BlockState out = state;
        for (Map.Entry<Property<?>, Object> change : changes.entrySet()) {
            out = setProperty(out, change.getKey(), change.getValue());
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState setProperty(BlockState state, Property<?> property, Object value) {
        // raw types: the value always comes from property.getPossibleValues()
        return state.setValue((Property) property, (Comparable) value);
    }

    private static boolean isConnectionName(String name) {
        return name.equals("north") || name.equals("east") || name.equals("south")
            || name.equals("west") || name.equals("up") || name.equals("down");
    }

    private static Direction directionByName(String name) {
        switch (name) {
            case "north": return Direction.NORTH;
            case "east": return Direction.EAST;
            case "south": return Direction.SOUTH;
            case "west": return Direction.WEST;
            case "up": return Direction.UP;
            case "down": return Direction.DOWN;
            default: return null;
        }
    }

    private static Direction axisDirection(Direction.Axis axis) {
        switch (axis) {
            case X: return Direction.EAST;
            case Y: return Direction.UP;
            default: return Direction.NORTH;
        }
    }

    private static int[] invert(int[] m) {
        // rotation matrices are orthogonal: inverse == transpose
        return new int[]{
            m[0], m[3], m[6],
            m[1], m[4], m[7],
            m[2], m[5], m[8],
        };
    }

    private static Direction transformDirection(Direction dir, int[] m) {
        int x = dir.getStepX(), y = dir.getStepY(), z = dir.getStepZ();
        int nx = m[0] * x + m[1] * y + m[2] * z;
        int ny = m[3] * x + m[4] * y + m[5] * z;
        int nz = m[6] * x + m[7] * y + m[8] * z;
        for (Direction candidate : Direction.values()) {
            if (candidate.getStepX() == nx && candidate.getStepY() == ny && candidate.getStepZ() == nz) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Renames TOP/BOTTOM, UPPER/LOWER, FLOOR/CEILING and LEFT/RIGHT pairs for an
     * upside-down flip; returns null when the name has no flipped form.
     */
    private static String flipEnumName(String name) {
        switch (name) {
            case "TOP": return "BOTTOM";
            case "BOTTOM": return "TOP";
            case "UPPER": return "LOWER";
            case "LOWER": return "UPPER";
            case "FLOOR": return "CEILING";
            case "CEILING": return "FLOOR";
            default: break;
        }
        if (name.contains("LEFT") && !name.contains("RIGHT")) return name.replace("LEFT", "RIGHT");
        if (name.contains("RIGHT") && !name.contains("LEFT")) return name.replace("RIGHT", "LEFT");
        return null;
    }

    /**
     * Rail shapes under an upside-down flip: slope along Z survives the double
     * flip (Y and Z both invert), slope along X reverses, and curve corners
     * follow their north/south edge. Returns null when unchanged.
     */
    private static RailShape mapRailShapeFlip(RailShape shape) {
        switch (shape) {
            case ASCENDING_EAST: return RailShape.ASCENDING_WEST;
            case ASCENDING_WEST: return RailShape.ASCENDING_EAST;
            case NORTH_EAST: return RailShape.SOUTH_EAST;
            case NORTH_WEST: return RailShape.SOUTH_WEST;
            case SOUTH_EAST: return RailShape.NORTH_EAST;
            case SOUTH_WEST: return RailShape.NORTH_WEST;
            default: return null; // north_south, east_west, ascending north/south survive
        }
    }
}
