package ace.actually.sableinbottle.ponder;

import ace.actually.sableinbottle.ModItems;
import ace.actually.sableinbottle.ship.ShipStructureDecoder;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.LinkedHashMap;
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
            Map<BlockPos, BlockState> normalized = ShipStructureDecoder.normalize(saved);
            BlockPos size = maxSize(normalized.keySet());
            if (size.getX() < limit[0] - 1 && size.getY() < limit[1] - 1 && size.getZ() < limit[2] - 1) {
                structure = normalized;
            } else {
                structure = demoShip();
                oversized = true;
            }
        }

        BlockPos span = maxSize(structure.keySet());
        int w = span.getX() + 1;
        int d = span.getZ() + 1;
        int h = span.getY() + 1;

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
        // Zoom out for both footprint and height: the visible half-height of the
        // scene is ~5.5 blocks at scale 1, so a structure taller than ~10 blocks
        // used to run off the top of the panel. setSceneOffsetY then slides the
        // view centre from the plate (y=1) to the structure's mid-height.
        float viewScale = Math.min(10f / plateSize, 9f / (h + 1));
        scene.scaleSceneView(Math.max(0.15f, Math.min(1f, viewScale)));
        scene.setSceneOffsetY((1 - h) / 2f);

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

        return ShipStructureDecoder.decode(tag.getCompound("ship"), level.getMinSection());
    }

    private static Map<BlockPos, BlockState> offset(Map<BlockPos, BlockState> blocks, BlockPos delta) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            out.put(entry.getKey().offset(delta), entry.getValue());
        }
        return out;
    }

    private static BlockPos minOf(Iterable<BlockPos> positions) {
        return ShipStructureDecoder.minOf(positions);
    }

    /** Size of the axis-aligned span: x = width-1, y = height-1, z = depth-1. */
    private static BlockPos maxSize(Iterable<BlockPos> positions) {
        return ShipStructureDecoder.maxSize(positions);
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
