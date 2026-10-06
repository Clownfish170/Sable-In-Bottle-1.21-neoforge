package ace.actually.sableinbottle.blocks.entity;

import ace.actually.sableinbottle.ModBlockEntities;
import ace.actually.sableinbottle.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.HolderLookup;

/**
 * Holds the ship data of a placed bottle so breaking it gives back
 * a bottle item with the same contents.
 */
public class ShipInBottleBlockEntity extends BlockEntity {

    private CompoundTag shipTag;

    public ShipInBottleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIP_IN_BOTTLE.get(), pos, state);
    }

    public void setShipTag(CompoundTag shipTag) {
        this.shipTag = shipTag;
        setChanged();
    }

    public boolean hasShip() {
        return shipTag != null && !shipTag.isEmpty();
    }

    /** The bottle item matching this block entity (empty bottle when no ship). */
    public ItemStack toItemStack() {
        ItemStack stack = new ItemStack(ModItems.SHIP_IN_A_BOTTLE.get());
        if (hasShip()) {
            CompoundTag tag = new CompoundTag();
            tag.put("ship", shipTag.copy());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return stack;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (hasShip()) {
            tag.put("ship", shipTag);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        shipTag = tag.contains("ship") ? tag.getCompound("ship") : null;
    }
}
