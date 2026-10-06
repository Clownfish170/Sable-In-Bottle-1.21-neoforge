package ace.actually.sableinbottle.item;

import ace.actually.sableinbottle.ModBlocks;
import ace.actually.sableinbottle.blocks.entity.ShipInBottleBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ShipInABottleItem extends Item {
    public ShipInABottleItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (level.isClientSide || player == null) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        boolean hasShip = data != null && data.copyTag().contains("ship");

        boolean pointingAtShip = getSubLevel(level, context.getClickedPos()) != null;
        if (pointingAtShip && !hasShip) {
            return captureShip(stack, level, player, context.getClickedPos());
        }
        if (hasShip && !player.isShiftKeyDown()) {
            return releaseShip(stack, level, player, context.getClickedPos());
        }
        // Ordinary ground with an empty bottle, or sneak + a filled bottle:
        // put the big bottle down as a block.
        return placeBottle(context, stack, hasShip ? data.copyTag().getCompound("ship") : null);
    }

    /**
     * Places the big bottle block against the clicked face. Any saved ship moves
     * into the block entity, and the item is consumed (except in creative).
     */
    private InteractionResult placeBottle(UseOnContext context, ItemStack stack, CompoundTag shipTag) {
        Level level = context.getLevel();
        BlockPos placePos = context.getClickedPos().relative(context.getClickedFace());
        if (!level.getBlockState(placePos).canBeReplaced(new BlockPlaceContext(context))) {
            return InteractionResult.PASS;
        }
        if (!level.setBlock(placePos, ModBlocks.BOTTLE.get().defaultBlockState(), 3)) {
            return InteractionResult.PASS;
        }

        if (shipTag != null && level.getBlockEntity(placePos) instanceof ShipInBottleBlockEntity bottle) {
            bottle.setShipTag(shipTag);
        }
        if (!context.getPlayer().hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    private InteractionResult captureShip(ItemStack stack, Level level, Player player, BlockPos clickedPos) {
        ServerSubLevel subLevel = getSubLevel(level, clickedPos);
        if (subLevel == null) {
            player.sendSystemMessage(Component.translatable("text.sableinbottle.not_on_ship"));
            return InteractionResult.PASS;
        }

        SubLevelData subLevelData = SubLevelSerializer.toData(subLevel, List.of());

        CompoundTag tag = new CompoundTag();
        tag.put("ship", subLevelData.fullTag());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        SubLevelContainer.getContainer((ServerLevel) level).removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);

        player.sendSystemMessage(Component.translatable("text.sableinbottle.ship_bottled"));
        return InteractionResult.SUCCESS;
    }

    private InteractionResult releaseShip(ItemStack stack, Level level, Player player, BlockPos clickedPos) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return InteractionResult.PASS;

        CompoundTag tag = data.copyTag();
        if (!tag.contains("ship")) return InteractionResult.PASS;

        CompoundTag shipTag = tag.getCompound("ship");

        Set<String> missingBlocks = new LinkedHashSet<>();
        sanitizeMissingMods(shipTag, (ServerLevel) level, missingBlocks);
        if (!missingBlocks.isEmpty()) {
            player.sendSystemMessage(Component.translatable("text.sableinbottle.missing_blocks", joinMissing(missingBlocks)));
        }

        moveShipToClick(shipTag, clickedPos);

        SubLevelData subLevelData = SubLevelSerializer.fromData(shipTag);
        if (subLevelData == null) {
            player.sendSystemMessage(Component.translatable("text.sableinbottle.invalid_ship"));
            return InteractionResult.PASS;
        }

        ServerSubLevel subLevel = SubLevelSerializer.fullyLoad((ServerLevel) level, subLevelData);
        if (subLevel == null) {
            player.sendSystemMessage(Component.translatable("text.sableinbottle.invalid_ship"));
            return InteractionResult.PASS;
        }

        stack.remove(DataComponents.CUSTOM_DATA);
        player.sendSystemMessage(Component.translatable("text.sableinbottle.ship_released"));
        return InteractionResult.SUCCESS;
    }

    /**
     * Sable stores block palettes as name-keyed compounds, so other mods' blocks survive
     * capture and release as long as those mods are installed. If one is missing, the
     * palette would fail to parse and abort the release, so unknown blocks are swapped
     * for air (same as a vanilla chunk loading without its mods) and the player is told
     * what was lost. Unknown plot biomes fall back to the overworld biome.
     */
    private static void sanitizeMissingMods(CompoundTag shipTag, ServerLevel level, Set<String> missingBlocks) {
        CompoundTag plotTag = shipTag.getCompound("plot");

        String biomeName = plotTag.getString("biome");
        ResourceLocation biomeId = biomeName.isEmpty() ? null : ResourceLocation.tryParse(biomeName);
        if (!biomeName.isEmpty() && (biomeId == null
                || level.registryAccess().registryOrThrow(Registries.BIOME).getOptional(biomeId).isEmpty())) {
            plotTag.putString("biome", "minecraft:overworld");
        }

        CompoundTag chunks = plotTag.getCompound("chunks");
        for (String chunkKey : chunks.getAllKeys()) {
            CompoundTag sections = chunks.getCompound(chunkKey).getCompound("sections");
            for (String sectionKey : sections.getAllKeys()) {
                ListTag palette = sections.getCompound(sectionKey)
                        .getCompound("block_states").getList("palette", Tag.TAG_COMPOUND);
                for (int i = 0; i < palette.size(); i++) {
                    if (!(palette.get(i) instanceof CompoundTag state)) continue;
                    String name = state.getString("Name");
                    if (name.isEmpty()) continue;
                    ResourceLocation blockId = ResourceLocation.tryParse(name);
                    if (blockId == null || !BuiltInRegistries.BLOCK.getOptional(blockId).isEmpty()) continue;

                    CompoundTag air = new CompoundTag();
                    air.putString("Name", "minecraft:air");
                    palette.set(i, air);
                    missingBlocks.add(name);
                }
            }
        }
    }

    private static String joinMissing(Set<String> missing) {
        List<String> names = List.copyOf(missing);
        String joined = String.join(", ", names.subList(0, Math.min(6, names.size())));
        return names.size() > 6 ? joined + " …" : joined;
    }

    /**
     * Rewrites the serialized pose so the ship loads at the clicked block instead of
     * the position it was captured at. Orientation is kept as-is; the ship's center of
     * mass keeps its height above the hull's lowest point, so the hull lands on top of
     * the clicked block, centred on it.
     */
    private static void moveShipToClick(CompoundTag shipTag, BlockPos clickedPos) {
        CompoundTag poseTag = shipTag.getCompound("pose");
        CompoundTag positionTag = poseTag.getCompound("position");
        CompoundTag boundsTag = shipTag.getCompound("world_bounds");
        if (positionTag.isEmpty()) return;

        double oldX = positionTag.getDouble("x");
        double oldY = positionTag.getDouble("y");
        double oldZ = positionTag.getDouble("z");

        double comAboveBottom = 1.0;
        if (boundsTag.contains("minY")) {
            double heightAboveBottom = oldY - boundsTag.getDouble("minY");
            if (!Double.isNaN(heightAboveBottom) && heightAboveBottom >= 0) {
                comAboveBottom = heightAboveBottom;
            }
        }

        double newX = clickedPos.getX() + 0.5;
        double newY = clickedPos.getY() + 1 + comAboveBottom;
        double newZ = clickedPos.getZ() + 0.5;

        positionTag.putDouble("x", newX);
        positionTag.putDouble("y", newY);
        positionTag.putDouble("z", newZ);

        if (boundsTag.contains("minX")) {
            double dx = newX - oldX;
            double dy = newY - oldY;
            double dz = newZ - oldZ;
            boundsTag.putDouble("minX", boundsTag.getDouble("minX") + dx);
            boundsTag.putDouble("maxX", boundsTag.getDouble("maxX") + dx);
            boundsTag.putDouble("minY", boundsTag.getDouble("minY") + dy);
            boundsTag.putDouble("maxY", boundsTag.getDouble("maxY") + dy);
            boundsTag.putDouble("minZ", boundsTag.getDouble("minZ") + dz);
            boundsTag.putDouble("maxZ", boundsTag.getDouble("maxZ") + dz);
        }
    }

    private static ServerSubLevel getSubLevel(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        SubLevel subLevel = Sable.HELPER.getContaining(serverLevel, pos);
        if (subLevel instanceof ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()) {
            return serverSubLevel;
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.copyTag().contains("ship")) {
            tooltipComponents.add(Component.translatable("text.sableinbottle.bottle_contains").withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
