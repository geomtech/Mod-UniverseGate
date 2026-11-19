package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class MeteorologicalControllerMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 5;

    public static final int FLAG_TOWER_PRESENT = 1;
    public static final int FLAG_PARABOLA_PRESENT = 1 << 1;
    public static final int FLAG_CATALYST_PRESENT = 1 << 2;
    public static final int FLAG_CATALYST_HAS_CRYSTAL = 1 << 3;
    public static final int FLAG_STRUCTURE_READY = 1 << 4;
    public static final int FLAG_ENERGY_LINKED = 1 << 5;
    public static final int FLAG_FULLY_CHARGED = 1 << 6;
    public static final int FLAG_SEQUENCE_ACTIVE = 1 << 7;
    public static final int FLAG_WEATHER_UNLOCKED = 1 << 8;
    public static final int FLAG_PARABOLA_POWERED = 1 << 9;

    private static final int DATA_CHARGE_TICKS = 0;
    private static final int DATA_MAX_CHARGE_TICKS = 1;
    private static final int DATA_STATUS_FLAGS = 2;
    private static final int DATA_SEQUENCE_TICKS = 3;
    private static final int DATA_SEQUENCE_MAX_TICKS = 4;

    private final BlockPos controllerPos;
    private final ContainerData data;

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    // client constructor
    public MeteorologicalControllerMenu(int syncId, Inventory inv, BlockPos pos) {
        this(syncId, inv, pos, new SimpleContainerData(DATA_COUNT));
    }

    // server constructor
    public MeteorologicalControllerMenu(int syncId,
                                        Inventory inv,
                                        MeteorologicalControllerBlockEntity controller) {
        this(syncId, inv, controller.getBlockPos(), controller.dataAccess());
    }

    private MeteorologicalControllerMenu(int syncId,
                                         Inventory inv,
                                         BlockPos controllerPos,
                                         ContainerData data) {
        super(ModMenuTypes.METEOROLOGICAL_CONTROLLER, syncId);
        this.controllerPos = controllerPos.immutable();
        this.data = data;
        checkContainerDataCount(data, DATA_COUNT);
        this.addDataSlots(data);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        WeatherSelection selection = WeatherSelection.fromButtonId(id);
        if (selection == null) return false;
        if (!(player.level() instanceof ServerLevel serverLevel)) return false;
        if (!(serverLevel.getBlockEntity(controllerPos) instanceof MeteorologicalControllerBlockEntity controller)) return false;
        return controller.tryStartWeatherSequence(selection);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(controllerPos).is(ModBlocks.METEOROLOGICAL_CONTROLLER)
                && player.distanceToSqr(controllerPos.getX() + 0.5D, controllerPos.getY() + 0.5D, controllerPos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public int chargeTicks() {
        return this.data.get(DATA_CHARGE_TICKS);
    }

    public int maxChargeTicks() {
        return this.data.get(DATA_MAX_CHARGE_TICKS);
    }

    public int sequenceTicks() {
        return this.data.get(DATA_SEQUENCE_TICKS);
    }

    public int sequenceMaxTicks() {
        return this.data.get(DATA_SEQUENCE_MAX_TICKS);
    }

    public int statusFlags() {
        return this.data.get(DATA_STATUS_FLAGS);
    }

    public boolean hasFlag(int flag) {
        return (statusFlags() & flag) != 0;
    }

    public float chargeProgress() {
        int max = maxChargeTicks();
        if (max <= 0) return 0.0F;
        return Math.min(1.0F, (float) chargeTicks() / (float) max);
    }

    public float sequenceProgress() {
        int max = sequenceMaxTicks();
        if (max <= 0) return 0.0F;
        return Math.min(1.0F, (float) sequenceTicks() / (float) max);
    }
}
