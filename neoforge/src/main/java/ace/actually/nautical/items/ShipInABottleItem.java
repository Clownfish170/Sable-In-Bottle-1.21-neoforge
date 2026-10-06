package ace.actually.nautical.items;

import ace.actually.nautical.util.SableShipControl;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

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

        if (data != null && data.copyTag().contains("ship")) {
            return releaseShip(stack, level, player, context.getClickedPos());
        }
        return captureShip(stack, level, player, context.getClickedPos());
    }

    private InteractionResult captureShip(ItemStack stack, Level level, Player player, BlockPos clickedPos) {
        ServerSubLevel subLevel = SableShipControl.getSubLevel(level, clickedPos);
        if (subLevel == null) {
            player.sendSystemMessage(Component.translatable("text.nautical.not_on_ship"));
            return InteractionResult.PASS;
        }

        SubLevelData subLevelData = SubLevelSerializer.toData(subLevel, List.of());

        CompoundTag tag = new CompoundTag();
        tag.put("ship", subLevelData.fullTag());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        SubLevelContainer.getContainer((ServerLevel) level).removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);

        player.sendSystemMessage(Component.translatable("text.nautical.ship_bottled"));
        return InteractionResult.SUCCESS;
    }

    private InteractionResult releaseShip(ItemStack stack, Level level, Player player, BlockPos clickedPos) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return InteractionResult.PASS;

        CompoundTag tag = data.copyTag();
        if (!tag.contains("ship")) return InteractionResult.PASS;

        CompoundTag shipTag = tag.getCompound("ship");
        moveShipToClick(shipTag, clickedPos);

        SubLevelData subLevelData = SubLevelSerializer.fromData(shipTag);
        if (subLevelData == null) {
            player.sendSystemMessage(Component.translatable("text.nautical.invalid_ship"));
            return InteractionResult.PASS;
        }

        ServerSubLevel subLevel = SubLevelSerializer.fullyLoad((ServerLevel) level, subLevelData);
        if (subLevel == null) {
            player.sendSystemMessage(Component.translatable("text.nautical.invalid_ship"));
            return InteractionResult.PASS;
        }

        stack.remove(DataComponents.CUSTOM_DATA);
        player.sendSystemMessage(Component.translatable("text.nautical.ship_released"));
        return InteractionResult.SUCCESS;
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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.copyTag().contains("ship")) {
            tooltipComponents.add(Component.translatable("text.nautical.bottle_contains").withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
