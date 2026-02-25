package fr.geomtech.universegate;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DnaExtractorItem extends Item {

    public static final int REQUIRED_CRYSTAL_STACKS = 10;
    public static final int REQUIRED_CRYSTALS = REQUIRED_CRYSTAL_STACKS * 64;

    private static final int EXTRACTION_DURATION_TICKS = 20 * 20;
    private static final double MAX_SCAN_DISTANCE = 14.0D;
    private static final float HEALTH_EPSILON = 0.001F;

    private static final Map<UUID, ExtractionState> ACTIVE_EXTRACTIONS = new HashMap<>();

    public DnaExtractorItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static int extractionDurationTicks() {
        return EXTRACTION_DURATION_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        StartResult startResult = tryStartExtraction(level, player, hand, null);
        if (startResult == StartResult.SUCCESS) {
            return InteractionResultHolder.consume(stack);
        }

        if (!level.isClientSide) {
            if (startResult == StartResult.NOT_ENOUGH_CRYSTALS) {
                sendNotEnoughCrystalsMessage(player);
            } else {
                player.displayClientMessage(Component.translatable("message.universegate.dna_extractor_target_mob"), true);
            }
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack,
                                                  Player player,
                                                  LivingEntity interactionTarget,
                                                  InteractionHand usedHand) {
        if (!(interactionTarget instanceof Mob mob)) {
            return InteractionResult.PASS;
        }

        StartResult startResult = tryStartExtraction(player.level(), player, usedHand, mob);
        if (startResult == StartResult.SUCCESS) {
            return InteractionResult.CONSUME;
        }

        if (!player.level().isClientSide) {
            if (startResult == StartResult.NOT_ENOUGH_CRYSTALS) {
                sendNotEnoughCrystalsMessage(player);
            } else {
                player.displayClientMessage(Component.translatable("message.universegate.dna_extractor_target_mob"), true);
            }
        }
        return InteractionResult.FAIL;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity livingEntity) {
        return EXTRACTION_DURATION_TICKS;
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide || !(livingEntity instanceof ServerPlayer serverPlayer)) {
            return;
        }

        Mob aimedMob = findTargetMob(serverPlayer);
        ExtractionState state = ACTIVE_EXTRACTIONS.get(serverPlayer.getUUID());

        if (state == null) {
            if (aimedMob == null) {
                cancelExtraction(serverPlayer, "message.universegate.dna_extractor_target_lost");
                return;
            }

            ACTIVE_EXTRACTIONS.put(serverPlayer.getUUID(), new ExtractionState(aimedMob.getUUID(), aimedMob.getHealth()));
            return;
        }

        if (aimedMob == null || !state.targetUuid.equals(aimedMob.getUUID()) || !aimedMob.isAlive()) {
            cancelExtraction(serverPlayer, "message.universegate.dna_extractor_target_lost");
            return;
        }

        if (aimedMob.getHealth() + HEALTH_EPSILON < state.lastKnownHealth) {
            cancelExtraction(serverPlayer, "message.universegate.dna_extractor_target_damaged");
            return;
        }

        state.lastKnownHealth = aimedMob.getHealth();
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        if (level.isClientSide || !(livingEntity instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return stack;
        }

        ExtractionState state = ACTIVE_EXTRACTIONS.remove(serverPlayer.getUUID());
        if (state == null) {
            return stack;
        }

        Entity entity = serverLevel.getEntity(state.targetUuid);
        if (!(entity instanceof Mob targetMob) || !targetMob.isAlive()) {
            serverPlayer.displayClientMessage(Component.translatable("message.universegate.dna_extractor_target_lost"), true);
            return stack;
        }

        if (targetMob.getHealth() + HEALTH_EPSILON < state.lastKnownHealth) {
            serverPlayer.displayClientMessage(Component.translatable("message.universegate.dna_extractor_target_damaged"), true);
            return stack;
        }

        if (!consumeRiftCrystals(serverPlayer, REQUIRED_CRYSTALS)) {
            sendNotEnoughCrystalsMessage(serverPlayer);
            return stack;
        }

        ItemStack dnaStack = DnaSampleItem.createFromMob(targetMob);
        if (!serverPlayer.addItem(dnaStack)) {
            serverPlayer.drop(dnaStack, false);
        }

        level.playSound(null, serverPlayer.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.9F, 1.15F);
        serverPlayer.displayClientMessage(
                Component.translatable("message.universegate.dna_extractor_success", targetMob.getDisplayName()),
                true
        );
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeLeft) {
        if (level.isClientSide || !(livingEntity instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ExtractionState removed = ACTIVE_EXTRACTIONS.remove(serverPlayer.getUUID());
        if (removed == null) {
            return;
        }

        int elapsedTicks = getUseDuration(stack, livingEntity) - timeLeft;
        if (elapsedTicks < EXTRACTION_DURATION_TICKS) {
            serverPlayer.displayClientMessage(Component.translatable("message.universegate.dna_extractor_interrupted"), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                TooltipContext context,
                                List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(
                Component.translatable("tooltip.universegate.dna_extractor_cost", REQUIRED_CRYSTAL_STACKS, REQUIRED_CRYSTALS)
                        .withStyle(ChatFormatting.AQUA)
        );
        tooltipComponents.add(
                Component.translatable("tooltip.universegate.dna_extractor_duration", EXTRACTION_DURATION_TICKS / 20)
                        .withStyle(ChatFormatting.DARK_GRAY)
        );
    }

    private StartResult tryStartExtraction(Level level, Player player, InteractionHand hand, @Nullable Mob explicitTarget) {
        if (!hasEnoughRiftCrystals(player)) {
            return StartResult.NOT_ENOUGH_CRYSTALS;
        }

        Mob target = explicitTarget;
        if (target == null || !target.isAlive()) {
            target = findTargetMob(player);
        }

        if (target == null) {
            return StartResult.NO_TARGET;
        }

        if (!level.isClientSide) {
            ACTIVE_EXTRACTIONS.put(player.getUUID(), new ExtractionState(target.getUUID(), target.getHealth()));
        }

        player.startUsingItem(hand);
        return StartResult.SUCCESS;
    }

    private void cancelExtraction(ServerPlayer player, String messageKey) {
        ACTIVE_EXTRACTIONS.remove(player.getUUID());
        player.stopUsingItem();
        player.displayClientMessage(Component.translatable(messageKey), true);
    }

    private static boolean hasEnoughRiftCrystals(Player player) {
        return countRiftCrystals(player) >= REQUIRED_CRYSTALS;
    }

    private static int countRiftCrystals(Player player) {
        Inventory inventory = player.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack slotStack = inventory.getItem(slot);
            if (!slotStack.is(ModItems.RIFT_CRYSTAL)) {
                continue;
            }

            count += slotStack.getCount();
            if (count >= REQUIRED_CRYSTALS) {
                return count;
            }
        }
        return count;
    }

    private static boolean consumeRiftCrystals(Player player, int amount) {
        Inventory inventory = player.getInventory();
        int remaining = amount;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack slotStack = inventory.getItem(slot);
            if (!slotStack.is(ModItems.RIFT_CRYSTAL)) {
                continue;
            }

            int removed = Math.min(remaining, slotStack.getCount());
            slotStack.shrink(removed);
            remaining -= removed;
            if (remaining <= 0) {
                inventory.setChanged();
                return true;
            }
        }

        return false;
    }

    private static void sendNotEnoughCrystalsMessage(Player player) {
        player.displayClientMessage(
                Component.translatable("message.universegate.dna_extractor_need_crystals", REQUIRED_CRYSTAL_STACKS, REQUIRED_CRYSTALS),
                true
        );
    }

    public static boolean hasTarget(Player player) {
        return findTargetMob(player) != null;
    }

    @Nullable
    private static Mob findTargetMob(Player player) {
        HitResult hitResult = ProjectileUtil.getHitResultOnViewVector(
                player,
                entity -> entity instanceof Mob mob && mob.isAlive() && entity.isPickable(),
                MAX_SCAN_DISTANCE
        );
        if (hitResult.getType() != HitResult.Type.ENTITY) {
            return null;
        }

        Entity entity = ((EntityHitResult) hitResult).getEntity();
        if (!(entity instanceof Mob mob) || !mob.isAlive()) {
            return null;
        }

        return mob;
    }

    private enum StartResult {
        SUCCESS,
        NOT_ENOUGH_CRYSTALS,
        NO_TARGET
    }

    private static final class ExtractionState {
        private final UUID targetUuid;
        private float lastKnownHealth;

        private ExtractionState(UUID targetUuid, float lastKnownHealth) {
            this.targetUuid = targetUuid;
            this.lastKnownHealth = lastKnownHealth;
        }
    }
}
