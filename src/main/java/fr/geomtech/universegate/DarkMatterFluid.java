package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

public abstract class DarkMatterFluid extends FlowingFluid {

    @Override
    public Fluid getFlowing() {
        return ModFluids.FLOWING_DARK_MATTER;
    }

    @Override
    public Fluid getSource() {
        return ModFluids.STILL_DARK_MATTER;
    }

    @Override
    public Item getBucket() {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "dark_matter_bucket"));
    }

    @Override
    protected boolean canBeReplacedWith(FluidState fluidState, BlockGetter blockGetter, BlockPos blockPos, Fluid fluid, Direction direction) {
        return direction == Direction.DOWN && !fluid.isSame(ModFluids.STILL_DARK_MATTER);
    }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor levelAccessor, BlockPos blockPos, BlockState blockState) {
        BlockEntity blockEntity = blockState.hasBlockEntity() ? levelAccessor.getBlockEntity(blockPos) : null;
        Block.dropResources(blockState, levelAccessor, blockPos, blockEntity);
    }

    @Override
    public boolean isSame(Fluid fluid) {
        return fluid == ModFluids.STILL_DARK_MATTER || fluid == ModFluids.FLOWING_DARK_MATTER;
    }

    @Override
    protected boolean canConvertToSource(Level level) {
        return false;
    }

    @Override
    protected int getSlopeFindDistance(LevelReader levelReader) {
        return 4;
    }

    @Override
    protected int getDropOff(LevelReader levelReader) {
        return 1;
    }

    @Override
    public int getTickDelay(LevelReader levelReader) {
        return 5;
    }

    @Override
    protected boolean isRandomlyTicking() {
        return true;
    }

    @Override
    protected float getExplosionResistance() {
        return 100.0F;
    }

    @Override
    protected BlockState createLegacyBlock(FluidState fluidState) {
        return ModBlocks.DARK_MATTER_BLOCK.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(fluidState));
    }

    public static class Flowing extends DarkMatterFluid {
        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState fluidState) {
            return fluidState.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState fluidState) {
            return false;
        }
    }

    public static class Source extends DarkMatterFluid {
        @Override
        public int getAmount(FluidState fluidState) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState fluidState) {
            return true;
        }
    }
}
