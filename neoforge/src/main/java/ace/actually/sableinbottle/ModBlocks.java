package ace.actually.sableinbottle;

import ace.actually.sableinbottle.blocks.ShipInBottleBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SableInBottle.MOD_ID);

    public static final Supplier<ShipInBottleBlock> BOTTLE = BLOCKS.register(
            "bottle",
            () -> new ShipInBottleBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).strength(0.4f))
    );
}
