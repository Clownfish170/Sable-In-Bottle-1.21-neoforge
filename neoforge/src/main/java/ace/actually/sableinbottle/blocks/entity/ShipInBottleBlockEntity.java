package ace.actually.sableinbottle.blocks.entity;

import ace.actually.sableinbottle.ModBlockEntities;
import ace.actually.sableinbottle.ModItems;
import ace.actually.sableinbottle.ship.ShipStructureDecoder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Holds the ship data of a placed bottle so breaking it gives back
 * a bottle item with the same contents, and so the block entity
 * renderer can show the bottled structure inside the glass.
 */
public class ShipInBottleBlockEntity extends BlockEntity {

    private CompoundTag shipTag;

    /** Lazily decoded client preview; invalidated whenever the tag changes. */
    private Map<BlockPos, BlockState> decodedShip;
    private boolean decodeAttempted;

    public ShipInBottleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIP_IN_BOTTLE.get(), pos, state);
    }

    public void setShipTag(CompoundTag shipTag) {
        this.shipTag = shipTag;
        this.decodedShip = null;
        this.decodeAttempted = false;
        setChanged();
        // Placement runs setBlock before the tag is known, so push the update
        // packet explicitly; the renderer on the client needs the ship data.
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean hasShip() {
        return shipTag != null && !shipTag.isEmpty();
    }

    /**
     * Plot-local blocks of the stored ship, normalized to the origin and with the
     * pose orientation applied. Decoded once per tag change on the render thread;
     * blocks fully buried behind opaque neighbours are dropped since they can
     * never be seen through the glass. Returns null when the bottle is empty or
     * the data cannot be decoded.
     */
    @Nullable
    public Map<BlockPos, BlockState> getDecodedShip() {
        if (!decodeAttempted && hasShip() && level != null) {
            decodeAttempted = true;
            Map<BlockPos, BlockState> decoded =
                ShipStructureDecoder.decode(shipTag, level.getMinSection());
            if (decoded != null) {
                decoded = ShipStructureDecoder.normalize(decoded);
                decodedShip = decoded.isEmpty() ? null : cullBuried(decoded);
            }
        }
        return decodedShip;
    }

    /** Drops blocks whose six neighbours are all present and opaque. */
    private static Map<BlockPos, BlockState> cullBuried(Map<BlockPos, BlockState> blocks) {
        Map<BlockPos, BlockState> visible = new java.util.LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            boolean buried = true;
            for (Direction direction : Direction.values()) {
                BlockState neighbour = blocks.get(pos.relative(direction));
                if (neighbour == null || !neighbour.canOcclude()) {
                    buried = false;
                    break;
                }
            }
            if (!buried) visible.put(pos, entry.getValue());
        }
        return visible;
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
        decodedShip = null;
        decodeAttempted = false;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
