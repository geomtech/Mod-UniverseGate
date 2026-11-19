package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class PortalKeyboardMenu extends AbstractContainerMenu {

    private static final int CORE_SEARCH_RADIUS_XZ = 8;
    private static final int CORE_SEARCH_RADIUS_Y = 4;
    private static final long POWER_REFRESH_INTERVAL_TICKS = 10L;
    private static final long CORE_RESCAN_INTERVAL_TICKS = 40L;

    private final PortalKeyboardBlockEntity keyboard;
    private final BlockPos keyboardPos;
    private final ContainerData data;
    private BlockPos cachedCorePos;
    private long nextPowerRefreshTick;
    private long nextCoreRescanTick;

    public BlockPos getKeyboardPos() {
        return keyboardPos;
    }

    // constructeur CLIENT
    public PortalKeyboardMenu(int syncId, Inventory inv, BlockPos pos) {
        this(syncId, inv, getKeyboardAt(inv, pos), new SimpleContainerData(1));
    }

    // constructeur SERVEUR
    public PortalKeyboardMenu(int syncId, Inventory inv, PortalKeyboardBlockEntity keyboard) {
        this(syncId, inv, keyboard, new SimpleContainerData(1));
    }

    protected PortalKeyboardMenu(int syncId, Inventory inv, PortalKeyboardBlockEntity keyboard, ContainerData data) {
        super(ModMenuTypes.PORTAL_KEYBOARD, syncId);
        this.keyboard = keyboard;
        this.keyboardPos = keyboard.getBlockPos();
        this.data = data;
        this.cachedCorePos = null;
        this.nextPowerRefreshTick = Long.MIN_VALUE;
        this.nextCoreRescanTick = Long.MIN_VALUE;
        addDataSlots(data);
    }

    private static PortalKeyboardBlockEntity getKeyboardAt(Inventory inv, BlockPos pos) {
        var be = inv.player.level().getBlockEntity(pos);
        if (be instanceof PortalKeyboardBlockEntity kb) return kb;
        if (be == null) {
            throw new IllegalStateException("Portal keyboard block entity not found at " + pos);
        }
        throw new IllegalStateException("Expected PortalKeyboardBlockEntity at " + pos + " but found " + be.getClass().getName());
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (keyboard != null && keyboard.getLevel() != null && !keyboard.getLevel().isClientSide) {
            ServerLevel serverLevel = (ServerLevel) keyboard.getLevel();
            long now = serverLevel.getGameTime();
            if (now < nextPowerRefreshTick) {
                return;
            }
            nextPowerRefreshTick = now + POWER_REFRESH_INTERVAL_TICKS;

            BlockPos corePos = resolveCorePos(serverLevel, keyboard.getBlockPos(), now);

            boolean powered = false;
            BlockEntity be = corePos == null ? null : serverLevel.getBlockEntity(corePos);
            if (be instanceof PortalCoreBlockEntity core) {
                powered = core.isDarkEnergyComplete()
                        && DarkEnergyNetworkHelper.isPortalPowered(serverLevel, corePos);
            }
            data.set(0, powered ? 1 : 0);
        }
    }

    private BlockPos resolveCorePos(ServerLevel level, BlockPos start, long now) {
        if (cachedCorePos != null
                && now < nextCoreRescanTick
                && level.getBlockState(cachedCorePos).is(ModBlocks.PORTAL_CORE)
                && level.getBlockEntity(cachedCorePos) instanceof PortalCoreBlockEntity) {
            return cachedCorePos;
        }

        cachedCorePos = findCore(start, level);
        nextCoreRescanTick = now + CORE_RESCAN_INTERVAL_TICKS;
        return cachedCorePos;
    }

    private BlockPos findCore(BlockPos start, ServerLevel level) {
        for (BlockPos p : BlockPos.betweenClosed(
                start.offset(-CORE_SEARCH_RADIUS_XZ, -CORE_SEARCH_RADIUS_Y, -CORE_SEARCH_RADIUS_XZ),
                start.offset(CORE_SEARCH_RADIUS_XZ, CORE_SEARCH_RADIUS_Y, CORE_SEARCH_RADIUS_XZ))) {
            if (level.getBlockState(p).is(ModBlocks.PORTAL_CORE)) {
                return p.immutable();
            }
        }
        return null;
    }

    public boolean isDarkPowered() {
        return data.get(0) == 1;
    }

    @Override
    public boolean stillValid(Player player) {
        return keyboard != null && keyboard.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
