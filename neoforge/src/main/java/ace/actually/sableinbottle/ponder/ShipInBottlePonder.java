package ace.actually.sableinbottle.ponder;

import ace.actually.sableinbottle.ModItems;
import ace.actually.sableinbottle.SableInBottle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ponder scene for the ship in a bottle. While the player hovers the item and holds
 * the ponder key, {@link ShipInBottlePonderPlugin} opens this storyboard; the structure
 * currently saved inside the hovered bottle (captured through the ponder tooltip
 * callback) is decoded and built into the virtual scene so the player can inspect it,
 * followed by a capture / carry / release walkthrough.
 */
public class ShipInBottlePonder {

    /** Last hovered bottle stack, kept fresh by the tooltip callback registered on client setup. */
    public static ItemStack lastHoveredBottle = ItemStack.EMPTY;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Upper bound of a structure the scene can preview, loaded lazily from
     * {@code config/sableinbottle.json} so oversized ships can be allowed without a rebuild.
     */
    private static int[] maxSize = {48, 32, 48};

    private static int[] previewLimit() {
        try {
            Path file = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("sableinbottle.json");
            if (!Files.exists(file)) {
                JsonObject json = new JsonObject();
                JsonArray size = new JsonArray();
                size.add(maxSize[0]);
                size.add(maxSize[1]);
                size.add(maxSize[2]);
                json.add("max_structure_size", size);
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(json) + System.lineSeparator());
            }
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (json.has("max_structure_size")) {
                JsonArray size = json.getAsJsonArray("max_structure_size");
                if (size.size() >= 3) {
                    maxSize = new int[]{
                        Math.max(1, size.get(0).getAsInt()),
                        Math.max(1, size.get(1).getAsInt()),
                        Math.max(1, size.get(2).getAsInt()),
                    };
                }
            }
        } catch (Exception e) {
            SableInBottle.LOGGER.warn("Could not read sableinbottle.json, using default preview limit", e);
        }
        return maxSize;
    }

    public static void bottledShip(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("bottle", "Ship in a Bottle");

        int[] limit = previewLimit();

        Map<BlockPos, BlockState> structure;
        boolean oversized = false;

        Map<BlockPos, BlockState> saved = decodeSavedStructure();
        if (saved == null) {
            structure = demoShip();
        } else {
            Map<BlockPos, BlockState> normalized = normalize(saved);
            BlockPos size = maxSize(normalized.keySet());
            if (size.getX() < limit[0] - 1 && size.getY() < limit[1] - 1 && size.getZ() < limit[2] - 1) {
                structure = normalized;
            } else {
                structure = demoShip();
                oversized = true;
            }
        }

        int w = maxSize(structure.keySet()).getX() + 1;
        int d = maxSize(structure.keySet()).getZ() + 1;

        boolean doubleSpot = (2L * w + 1) < limit[0] - 2 && d < limit[2] - 2;
        int shift = doubleSpot ? w + 1 : 0;

        BlockPos originA = new BlockPos(2, 1, 2);
        BlockPos originB = originA.offset(shift, 0, 0);
        Map<BlockPos, BlockState> blocksA = offset(structure, originA);
        Map<BlockPos, BlockState> blocksB = offset(structure, originB);
        Selection selA = selectionOf(util, blocksA.keySet());
        Selection selB = selectionOf(util, blocksB.keySet());

        Vec3 centerA = centerOf(blocksA.keySet());
        Vec3 centerB = centerOf(blocksB.keySet());
        Vec3 topA = new Vec3(centerA.x, maxY(blocksA.keySet()) + 1.5, centerA.z);
        Vec3 topB = new Vec3(centerB.x, maxY(blocksB.keySet()) + 1.5, centerB.z);

        // The base plate is the y=0 checker layer of the schematic, cropped to this
        // square: grow it with the structure so the whole footprint always sits on it.
        int spanX = shift > 0 ? shift + w : w;
        int plateSize = Math.min(limit[0] - 1, Math.max(7, Math.max(spanX, d) + 3));
        scene.configureBasePlate(1, 1, plateSize);
        scene.showBasePlate();
        scene.scaleSceneView(Math.max(0.3f, Math.min(1f, 10f / plateSize)));

        ItemStack bottle = new ItemStack(ModItems.SHIP_IN_A_BOTTLE.get());

        scene.special().movePointOfInterest(centerA);

        if (oversized) {
            scene.overlay().showText(90)
                .text("The saved structure exceeds the preview limit, showing a stand-in instead")
                .pointAt(centerA)
                .placeNearTarget();
            scene.idle(100);
            scene.overlay().showText(90)
                .text("Raise max_structure_size in config/sableinbottle.json to preview it")
                .pointAt(centerA)
                .placeNearTarget();
            scene.idle(100);
        }

        for (Map.Entry<BlockPos, BlockState> entry : blocksA.entrySet()) {
            scene.world().setBlock(entry.getKey(), entry.getValue(), false);
        }
        scene.idle(4);
        ElementLink<WorldSectionElement> linkA = scene.world().showIndependentSection(selA, Direction.DOWN);
        scene.idle(18);

        scene.overlay().showText(70)
            .text("This is the physics structure saved in the bottle")
            .pointAt(centerA)
            .placeNearTarget();
        scene.idle(95);

        scene.overlay().showControls(topA, Pointing.DOWN, 40).rightClick().withItem(bottle);
        scene.idle(30);
        scene.world().hideIndependentSection(linkA, Direction.UP);
        scene.idle(25);
        scene.world().setBlocks(selA, Blocks.AIR.defaultBlockState(), false);
        scene.idle(10);

        scene.overlay().showText(70)
            .text("Block entities, ticks and structure data are serialized in full")
            .pointAt(centerA)
            .placeNearTarget();
        scene.idle(95);

        if (shift > 0) {
            scene.special().movePointOfInterest(centerB);
        }
        scene.overlay().showControls(shift > 0 ? topB : topA, Pointing.UP, 40).withItem(bottle);
        scene.idle(30);
        scene.overlay().showText(70)
            .text("Saved data is not tied to coordinates - take it anywhere")
            .pointAt(shift > 0 ? centerB : centerA)
            .placeNearTarget();
        scene.idle(95);

        scene.overlay().showControls(topB, Pointing.DOWN, 40).rightClick().withItem(bottle);
        scene.idle(30);
        for (Map.Entry<BlockPos, BlockState> entry : blocksB.entrySet()) {
            scene.world().setBlock(entry.getKey(), entry.getValue(), false);
        }
        ElementLink<WorldSectionElement> linkB = scene.world().showIndependentSection(selB, Direction.DOWN);
        scene.idle(25);
        scene.overlay().showOutline(PonderPalette.WHITE, new Object(), selB, 60);
        scene.overlay().showText(70)
            .text("On release it appears where you point")
            .pointAt(centerB)
            .placeNearTarget();
        scene.idle(95);

        scene.idle(40);

        scene.markAsFinished();
    }

    // ------------------------------------------------------------------
    // Saved structure decoding
    // ------------------------------------------------------------------

    /**
     * Decodes the block data of the ship saved in the hovered bottle. Returns the
     * blocks in plot-local coordinates, or null when there is nothing to show.
     */
    private static Map<BlockPos, BlockState> decodeSavedStructure() {
        ItemStack stack = lastHoveredBottle;
        if (stack.isEmpty() || !stack.is(ModItems.SHIP_IN_A_BOTTLE.get())) return null;

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;

        CompoundTag tag = data.copyTag();
        if (!tag.contains("ship")) return null;

        var level = Minecraft.getInstance().level;
        if (level == null) return null;

        CompoundTag chunks = tag.getCompound("ship").getCompound("plot").getCompound("chunks");
        if (chunks.isEmpty()) return null;

        int minSection = level.getMinSection();
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
        CompoundTag orientation = tag.getCompound("ship").getCompound("pose").getCompound("orientation");
        return applyPoseOrientation(blocks, orientation);
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

    // ------------------------------------------------------------------
    // Structure helpers
    // ------------------------------------------------------------------

    /** Shifts blocks so their minimum corner sits at the origin. */
    private static Map<BlockPos, BlockState> normalize(Map<BlockPos, BlockState> blocks) {
        BlockPos min = minOf(blocks.keySet());
        return offset(blocks, new BlockPos(-min.getX(), -min.getY(), -min.getZ()));
    }

    private static Map<BlockPos, BlockState> offset(Map<BlockPos, BlockState> blocks, BlockPos delta) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            out.put(entry.getKey().offset(delta), entry.getValue());
        }
        return out;
    }

    private static BlockPos minOf(Iterable<BlockPos> positions) {
        int x = Integer.MAX_VALUE, y = Integer.MAX_VALUE, z = Integer.MAX_VALUE;
        for (BlockPos pos : positions) {
            x = Math.min(x, pos.getX());
            y = Math.min(y, pos.getY());
            z = Math.min(z, pos.getZ());
        }
        return new BlockPos(x, y, z);
    }

    /** Size of the axis-aligned span: x = width-1, y = height-1, z = depth-1. */
    private static BlockPos maxSize(Iterable<BlockPos> positions) {
        BlockPos min = minOf(positions);
        int dx = 0, dy = 0, dz = 0;
        for (BlockPos pos : positions) {
            dx = Math.max(dx, pos.getX() - min.getX());
            dy = Math.max(dy, pos.getY() - min.getY());
            dz = Math.max(dz, pos.getZ() - min.getZ());
        }
        return new BlockPos(dx, dy, dz);
    }

    private static Selection selectionOf(SceneBuildingUtil util, Iterable<BlockPos> positions) {
        Selection selection = null;
        for (BlockPos pos : positions) {
            Selection single = util.select().position(pos);
            selection = selection == null ? single : selection.add(single);
        }
        return selection;
    }

    private static Vec3 centerOf(Iterable<BlockPos> positions) {
        BlockPos min = minOf(positions);
        BlockPos size = maxSize(positions);
        return new Vec3(
            min.getX() + (size.getX() + 1) / 2.0,
            min.getY() + (size.getY() + 1) / 2.0,
            min.getZ() + (size.getZ() + 1) / 2.0
        );
    }

    private static double maxY(Iterable<BlockPos> positions) {
        double max = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            max = Math.max(max, pos.getY());
        }
        return max;
    }

    /** A small galleon used when the bottle is empty or the saved ship is too large. */
    private static Map<BlockPos, BlockState> demoShip() {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        BlockState hull = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState mast = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState sail = Blocks.WHITE_WOOL.defaultBlockState();

        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 2; z++) {
                blocks.put(new BlockPos(x, 0, z), hull);
                blocks.put(new BlockPos(x, 1, z), hull);
            }
        }
        for (int y = 2; y <= 4; y++) {
            blocks.put(new BlockPos(2, y, 1), mast);
        }
        for (int y = 2; y <= 4; y++) {
            for (int x = 1; x <= 3; x++) {
                blocks.put(new BlockPos(x, y, 2), sail);
            }
        }
        return blocks;
    }
}
