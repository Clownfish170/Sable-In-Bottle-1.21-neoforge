package ace.actually.sableinbottle.client;

import ace.actually.sableinbottle.blocks.ShipInBottleBlock;
import ace.actually.sableinbottle.blocks.entity.ShipInBottleBlockEntity;
import ace.actually.sableinbottle.ship.ShipStructureDecoder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.util.RandomSource;

import java.util.Map;

/**
 * Draws the bottled physics structure inside the placed bottle block, scaled
 * down to fit the glass cavity. Empty bottles render nothing beyond the bottle
 * model itself, so filled and empty bottles are told apart at a glance.
 *
 * <p>Blocks are drawn through {@code renderBatched} against a lightweight view of
 * the structure so faces hidden behind opaque neighbours are culled; light is
 * sampled from the bottle's own position, so the miniature is lit like the glass
 * around it. The structure is decoded once per tag change and cached in the
 * block entity.
 */
public class ShipInBottleRenderer implements BlockEntityRenderer<ShipInBottleBlockEntity> {

    /**
     * The glass cavity of the bottle model (model units, 16 = one block):
     * walls span x 3..13, y 0..10, z 1..13 with 0.5 thickness, and the cork
     * plugs z 0..3, so the free interior sits behind the cork. The structure
     * is fitted into this box with a small margin.
     */
    private static final Vec3 CAVITY_MIN = new Vec3(4.0, 1.0, 3.5);
    private static final Vec3 CAVITY_MAX = new Vec3(12.0, 9.0, 12.5);

    /**
     * Fraction of the cavity the structure may occupy. The leftover ring of air
     * keeps the miniature from touching the glass, which both reads better and
     * leaves room for the floating bob below.
     */
    private static final float FILL_FACTOR = 0.85f;

    /** Bob period in seconds; one gentle rise and sink per ~3s. */
    private static final float BOB_PERIOD_TICKS = 60.0f;

    /**
     * Vertical travel of the bob in block units. Sized to stay inside the gap
     * left by {@link #FILL_FACTOR} even when the structure's tallest axis is
     * what determined the scale, so the miniature never pokes through glass.
     */
    private static final float BOB_AMPLITUDE = 0.03f;

    private final RandomSource random = RandomSource.create();

    public ShipInBottleRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
        ShipInBottleBlockEntity bottle,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        Map<BlockPos, BlockState> structure = bottle.getDecodedShip();
        if (structure == null || structure.isEmpty()) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        BlockPos span = ShipStructureDecoder.maxSize(structure.keySet());
        double w = span.getX() + 1;
        double h = span.getY() + 1;
        double d = span.getZ() + 1;

        Vec3 cavitySize = CAVITY_MAX.subtract(CAVITY_MIN);
        double scale = Math.min(
            Math.min(cavitySize.x / 16.0 / w, cavitySize.y / 16.0 / h),
            cavitySize.z / 16.0 / d
        ) * FILL_FACTOR;
        Vec3 center = CAVITY_MIN.add(cavitySize.scale(0.5)).scale(1.0 / 16.0);

        // Gentle vertical bob so the bottled structure reads as suspended in the
        // glass instead of welded into it. Phase is derived from the position so
        // neighbouring bottles don't bob in lockstep. Pure sine on the pose stack
        // - the per-block geometry work is unchanged, so this is effectively free.
        double gameTime = level.getGameTime() + partialTick;
        BlockPos bottlePos = bottle.getBlockPos();
        double phase = (bottlePos.getX() * 3 + bottlePos.getY() * 7 + bottlePos.getZ() * 11)
            % Math.round(BOB_PERIOD_TICKS);
        float bob = (float) (Math.sin((gameTime + phase) / BOB_PERIOD_TICKS * Math.PI * 2.0) * BOB_AMPLITUDE);

        StructureView view = new StructureView(structure, level, bottle.getBlockPos(), packedLight);
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

        poseStack.pushPose();
        // Model units to block units, then fit the structure around the cavity centre.
        poseStack.translate(center.x, center.y + bob, center.z);
        // The glass (and its cork) is rotated by the block's facing; the miniature
        // turns with it so the ship stays inside the cavity's open side.
        poseStack.mulPose(Axis.YP.rotationDegrees(-bottle.getBlockState().getValue(ShipInBottleBlock.FACING).toYRot()));
        poseStack.scale((float) scale, (float) scale, (float) scale);
        poseStack.translate(-w / 2.0, -h / 2.0, -d / 2.0);

        for (Map.Entry<BlockPos, BlockState> entry : structure.entrySet()) {
            BlockState state = entry.getValue();
            BlockPos pos = entry.getKey();
            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            if (state.getRenderShape() == RenderShape.MODEL) {
                dispatcher.renderBatched(
                    state, pos, view, poseStack,
                    bufferSource.getBuffer(ItemBlockRenderTypes.getRenderType(state, false)),
                    true, random
                );
            } else if (state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
                dispatcher.renderSingleBlock(state, poseStack, bufferSource, packedLight, packedOverlay);
            } else if (!state.getFluidState().isEmpty()) {
                // Liquids report INVISIBLE as a block shape but still draw as fluids.
                dispatcher.renderLiquid(
                    pos, view,
                    bufferSource.getBuffer(ItemBlockRenderTypes.getRenderLayer(state.getFluidState())),
                    state, state.getFluidState()
                );
            }
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    /**
     * Read-only view of the decoded structure in plot-local coordinates. Block
     * lookups fall through to the map (air outside), light comes from the packed
     * light of the bottle's world position, and biome tinting uses the bottle's
     * real coordinates.
     */
    private record StructureView(
        Map<BlockPos, BlockState> blocks,
        Level level,
        BlockPos bottlePos,
        int packedLight
    ) implements BlockAndTintGetter {

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return level.getShade(direction, shade);
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return level.getBlockTint(bottlePos, colorResolver);
        }

        @Override
        public int getBrightness(LightLayer lightType, BlockPos pos) {
            return lightType == LightLayer.SKY
                ? (packedLight >> 20) & 0xF
                : (packedLight >> 4) & 0xF;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            int sky = (packedLight >> 20) & 0xF;
            int block = (packedLight >> 4) & 0xF;
            return Math.max(sky, block);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return level.getLightEngine();
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
        }
    }
}
