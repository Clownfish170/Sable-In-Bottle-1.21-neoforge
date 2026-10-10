package ace.actually.sableinbottle.blocks;

import ace.actually.sableinbottle.ModBlocks;
import ace.actually.sableinbottle.blocks.entity.ShipInBottleBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * The placeable big bottle. Stores the captured ship (if any) in its block
 * entity so breaking or pick-block returns a bottle with the same contents.
 *
 * <p>{@link #FACING} is the direction the cork points. Placement aims it at
 * the player's left hand, so a bottle put down while looking north has its
 * cork on the west side of the view.
 */
public class ShipInBottleBlock extends BaseEntityBlock {

    public static final MapCodec<ShipInBottleBlock> CODEC = simpleCodec(ShipInBottleBlock::new);

    /** Direction the cork points out of the bottle. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ShipInBottleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShipInBottleBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ShipInBottleBlockEntity bottle) {
                Block.popResource(level, pos, bottle.toItemStack());
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ShipInBottleBlockEntity bottle) {
            return bottle.toItemStack();
        }
        return new ItemStack(ModBlocks.BOTTLE.get());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return null;
    }
}
