package ace.actually.sableinbottle;

import ace.actually.sableinbottle.blocks.entity.ShipInBottleBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SableInBottle.MOD_ID);

    public static final Supplier<BlockEntityType<ShipInBottleBlockEntity>> SHIP_IN_BOTTLE =
            BLOCK_ENTITY_TYPES.register("ship_in_bottle",
                    () -> BlockEntityType.Builder.of(ShipInBottleBlockEntity::new, ModBlocks.BOTTLE.get()).build(null));
}
