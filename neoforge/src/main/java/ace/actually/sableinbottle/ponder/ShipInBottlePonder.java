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

        // The schematic's checker plate covers x/z 0..47 with the plate origin
        // at (1,1): anything larger silently draws as air, so clamp to it
        // on top of the configured size limit.
        int maxPlate = Math.min(47, limit[0] - 1);
        int plateSize = Math.min(maxPlate, Math.max(7, Math.max(w, d) + 3));
        // The camera looks at (plateSize/2 + offset) regardless of structure size,
        // so make plateSize share parity with the structure width: the centre then
        // lands exactly on the view centre instead of half a block off. (When w
        // and d differ in parity the z axis stays <= 0.5 block off.)
        if (plateSize < maxPlate && ((plateSize - w) & 1) != 0) {
            plateSize++;
        }

        // Slide the single structure copy onto the view centre. This used to
        // centre the midpoint of TWO side-by-side copies (capture/release demo),
        // which pushed whichever copy was actually on screen ~w/2 blocks to one
        // side - the whole scene then read as off-centre. The bottle icon now
        // flies off alone to convey "data travels with the bottle".
        int originX = Math.round(plateSize / 2f + 1 - w / 2f);
        int originZ = Math.round(plateSize / 2f + 1 - d / 2f);
        BlockPos origin = new BlockPos(originX, 1, originZ);
        Map<BlockPos, BlockState> blocks = offset(structure, origin);
        Selection sel = selectionOf(util, blocks.keySet());

        Vec3 center = centerOf(blocks.keySet());
        Vec3 top = new Vec3(center.x, maxY(blocks.keySet()) + 1.5, center.z);

        scene.configureBasePlate(1, 1, plateSize);
        scene.showBasePlate();
        // Zoom out for both footprint and height: the visible half-height of the
        // scene is ~5.5 blocks at scale 1, so a structure taller than ~10 blocks
        // used to run off the top of the panel. setSceneOffsetY places the view
        // centre (1 - yOffset) exactly on the structure's vertical centre so it
        // sits mid-screen instead of half a block low.
        float footprint = Math.max(w, d);
        float viewScale = Math.min(10f / Math.max(plateSize, footprint), 9f / (h + 1));
        scene.scaleSceneView(Math.max(0.15f, Math.min(1f, viewScale)));
        scene.setSceneOffsetY(-h / 2f);

        ItemStack bottle = new ItemStack(ModItems.SHIP_IN_A_BOTTLE.get());

        scene.special().movePointOfInterest(center);

        if (oversized) {
            scene.overlay().showText(90)
                .text("The saved structure exceeds the preview limit, showing a stand-in instead")
                .pointAt(center)
                .placeNearTarget();
            scene.idle(100);
            scene.overlay().showText(90)
                .text("Raise max_structure_size in config/sableinbottle.json to preview it")
                .pointAt(center)
                .placeNearTarget();
            scene.idle(100);
        }

        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            scene.world().setBlock(entry.getKey(), entry.getValue(), false);
        }
        scene.idle(4);
        ElementLink<WorldSectionElement> linkA = scene.world().showIndependentSection(sel, Direction.DOWN);
        scene.idle(18);

        scene.overlay().showText(70)
            .text("This is the physics structure saved in the bottle")
            .pointAt(center)
            .placeNearTarget();
        scene.idle(95);

        scene.overlay().showControls(top, Pointing.DOWN, 40).rightClick().withItem(bottle);
        scene.idle(30);
        scene.world().hideIndependentSection(linkA, Direction.UP);
        scene.idle(25);
        scene.world().setBlocks(sel, Blocks.AIR.defaultBlockState(), false);
        scene.idle(10);

        scene.overlay().showText(70)
            .text("Block entities, ticks and structure data are serialized in full")
            .pointAt(center)
            .placeNearTarget();
        scene.idle(95);

        // The bottle flies off with the data instead of a second structure copy
        // appearing elsewhere: the copy trick kept the view off-centre while the
        // visible structure was showing.
        scene.overlay().showControls(top, Pointing.UP, 40).withItem(bottle);
        scene.idle(30);
        scene.overlay().showText(70)
            .text("Saved data is not tied to coordinates - take it anywhere")
            .pointAt(center)
            .placeNearTarget();
        scene.idle(95);

        scene.overlay().showControls(top, Pointing.DOWN, 40).rightClick().withItem(bottle);
        scene.idle(30);
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            scene.world().setBlock(entry.getKey(), entry.getValue(), false);
        }
        ElementLink<WorldSectionElement> linkB = scene.world().showIndependentSection(sel, Direction.DOWN);
        scene.idle(25);
        scene.overlay().showOutline(PonderPalette.WHITE, new Object(), sel, 60);
        scene.overlay().showText(70)
            .text("On release it appears where you point")
            .pointAt(center)
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
