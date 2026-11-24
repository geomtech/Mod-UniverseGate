package fr.geomtech.universegate;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;

import java.util.List;

public class AcidTechnologyGameTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void laserRequiresWholeShotAndConsumesAcrossBatteries(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 1));
        player.moveTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D, 0, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LASER_RIFLE));
        ItemStack first = AcidBatteryItem.createChargedStack();
        ItemStack second = AcidBatteryItem.createChargedStack();
        AcidBatteryItem.setStoredEnergy(first, 600);
        AcidBatteryItem.setStoredEnergy(second, 300);
        player.getInventory().setItem(1, first);
        player.getInventory().setItem(2, second);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 5));
        ModItems.LASER_RIFLE.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(zombie.getHealth() == 20.0F, "Insufficient energy must not fire");
        helper.assertTrue(AcidBatteryItem.getStoredEnergy(first) == 600
                && AcidBatteryItem.getStoredEnergy(second) == 300, "Failed shots must not drain batteries");
        AcidBatteryItem.setStoredEnergy(second, 600);
        ModItems.LASER_RIFLE.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(zombie.getHealth() == 2.0F, "Laser must hit and bypass zombie armor for 18 damage");
        helper.assertTrue(AcidBatteryItem.getStoredEnergy(first) == 0
                && AcidBatteryItem.getStoredEnergy(second) == 200, "Exactly 1,000 EU must be drawn across batteries");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void horseTransformationRetainsRiderAndEquipment(GameTestHelper helper) {
        Player rider = helper.makeMockPlayer(GameType.SURVIVAL);
        Horse horse = helper.spawnWithNoFreeWill(EntityType.HORSE, new BlockPos(3, 2, 3));
        horse.setTamed(true);
        horse.setOwnerUUID(rider.getUUID());
        horse.setAge(0);
        horse.setCustomName(Component.literal("Acid test mount"));
        horse.equipSaddle(new ItemStack(Items.SADDLE), null);
        horse.setBodyArmorItem(new ItemStack(Items.DIAMOND_HORSE_ARMOR));
        ((LaserMount) horse).universegate$setLaserHarness(true);
        helper.assertTrue(rider.startRiding(horse, true), "Rider must mount the test horse");
        for (int shot = 0; shot < AcidTechnologyManager.HORSE_TRANSFORMATION_SHOTS; shot++) {
            AcidTechnologyManager.wearLaserHorse(horse, rider);
        }
        SkeletonHorse skeleton = helper.getEntities(EntityType.SKELETON_HORSE, new BlockPos(3, 2, 3), 4).getFirst();
        helper.assertTrue(horse.isRemoved() && rider.getVehicle() == skeleton, "Conversion must transfer the rider");
        helper.assertTrue(skeleton.isSaddled() && skeleton.isTamed()
                && rider.getUUID().equals(skeleton.getOwnerUUID()), "Saddle and ownership must survive conversion");
        helper.assertTrue(skeleton.getCustomName() != null && skeleton.getCustomName().getString().equals("Acid test mount"), "Name must survive conversion");
        helper.assertItemEntityCountIs(Items.DIAMOND_HORSE_ARMOR, new BlockPos(3, 2, 3), 4, 1);
        CompoundTag saved = skeleton.saveWithoutId(new CompoundTag());
        SkeletonHorse restored = EntityType.SKELETON_HORSE.create(helper.getLevel());
        restored.load(saved);
        helper.assertTrue(((LaserMount) restored).universegate$hasLaserHarness()
                && ((LaserMount) restored).universegate$getLaserShots() == 24, "Harness and corrosion must persist in saved entity data");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void acidRustsGolemsAndOxidizedIronPowersMachines(GameTestHelper helper) {
        var golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(3, 2, 3));
        golem.setPlayerCreated(true);
        golem.setHealth(50.0F);
        helper.assertTrue(AcidTechnologyManager.onAcidExposure(golem), "Acid must transform an iron golem");
        RustedGolemEntity rusty = helper.getEntities(ModEntityTypes.RUSTED_GOLEM, new BlockPos(3, 2, 3), 4).getFirst();
        helper.assertTrue(rusty.isPlayerCreated() && rusty.getMaxHealth() == 60.0F && rusty.getHealth() == 30.0F, "Conversion must retain builder allegiance and health ratio");
        helper.assertTrue(rusty.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.14D
                && rusty.getAttributeValue(Attributes.ATTACK_DAMAGE) == 7.0D, "Rusted golems must be slower and weaker");
        rusty.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
        BlockPos generatorPos = new BlockPos(1, 2, 1);
        helper.setBlock(generatorPos, ModBlocks.COMBUSTION_GENERATOR);
        CombustionGeneratorBlockEntity generator = helper.getBlockEntity(generatorPos);
        generator.setItem(0, new ItemStack(ModItems.OXIDIZED_IRON));
        helper.runAfterDelay(25, () -> {
            helper.assertItemEntityPresent(ModItems.OXIDIZED_IRON, new BlockPos(3, 2, 3), 4);
            helper.assertItemEntityNotPresent(Items.IRON_INGOT);
            helper.assertTrue(generator.getItem(0).isEmpty() && generator.dataAccess().get(0) == 8_000,
                    "One oxidized ingot must supply 8,000 EU to the generator");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void acidTamesCreepersAndPersistsOwnedOrders(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Creeper hostile = helper.spawn(EntityType.CREEPER, new BlockPos(3, 2, 3));
        player.moveTo(hostile.getX() + 1, hostile.getY(), hostile.getZ(), 0, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ACID_RAIN_BUCKET));
        UseEntityCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, hostile, null);
        AcidCreeperEntity friendly = helper.getEntities(ModEntityTypes.ACID_CREEPER, new BlockPos(3, 2, 3), 4).getFirst();
        helper.assertTrue(hostile.isRemoved() && friendly.isOwnedBy(player), "Acid bucket must create an owned companion");
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "Taming must return the empty bucket");
        BlockPos order = helper.absolutePos(new BlockPos(5, 2, 5));
        friendly.orderAttack(order, null);
        CompoundTag saved = friendly.saveWithoutId(new CompoundTag());
        AcidCreeperEntity restored = ModEntityTypes.ACID_CREEPER.create(helper.getLevel());
        restored.load(saved);
        CompoundTag resaved = restored.saveWithoutId(new CompoundTag());
        helper.assertTrue(restored.isOwnedBy(player) && resaved.getLong("AcidOrderPos") == order.asLong(), "Owner and target orders must survive a save/load");
        friendly.followOwner();
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(friendly.getTarget() == null && !friendly.isIgnited(), "A recalled friendly creeper must not attack nearby players");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
    public void beaconRequiresSkyAndGrantsFiveEffects(GameTestHelper helper) {
        BlockPos beaconPos = new BlockPos(3, 2, 3);
        helper.setBlock(beaconPos, ModBlocks.ACID_BEACON);
        helper.setBlock(beaconPos.above(2), Blocks.STONE);
        AcidBeaconBlockEntity beacon = helper.getBlockEntity(beaconPos);
        beacon.addEnergy(24_000);
        Player player = helper.makeMockServerPlayerInLevel();
        BlockPos absolute = helper.absolutePos(beaconPos);
        player.moveTo(absolute.getX() + 2, absolute.getY(), absolute.getZ(), 0, 0);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(beacon.getStoredEnergy() == 24_000, "Covered beacons must not consume power");
            helper.setBlock(beaconPos.above(2), Blocks.AIR);
            helper.runAfterDelay(85, () -> {
                helper.assertTrue(beacon.getStoredEnergy() < 24_000 && beacon.getStoredEnergy() >= 23_600, "Open beacons must draw 4 EU per tick");
                helper.assertLivingEntityHasMobEffect(player, MobEffects.MOVEMENT_SPEED, 1);
                helper.assertLivingEntityHasMobEffect(player, MobEffects.DIG_SPEED, 1);
                helper.assertLivingEntityHasMobEffect(player, MobEffects.DAMAGE_BOOST, 1);
                helper.assertLivingEntityHasMobEffect(player, MobEffects.DAMAGE_RESISTANCE, 0);
                helper.assertLivingEntityHasMobEffect(player, MobEffects.REGENERATION, 0);
                player.discard();
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void breakingBatteryMachinesConservesAllEnergy(GameTestHelper helper) {
        BlockPos position = new BlockPos(3, 2, 3);
        helper.setBlock(position, ModBlocks.ACID_NEON_LAMP);
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(position);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int index = 0; index < 4; index++) {
            ItemStack battery = AcidBatteryItem.createChargedStack();
            ((AcidNeonLampBlock) ModBlocks.ACID_NEON_LAMP).useItemOn(battery, level.getBlockState(absolute), level,
                    absolute, player, InteractionHand.MAIN_HAND, new BlockHitResult(absolute.getCenter(), Direction.UP, absolute, false));
            helper.assertTrue(battery.isEmpty(), "A completely transferred battery must be consumed to avoid duplicating casings");
        }
        level.destroyBlock(absolute, true);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2), item -> item.getItem().is(ModItems.ACID_BATTERY));
        int recovered = drops.stream().mapToInt(drop -> AcidBatteryItem.getStoredEnergy(drop.getItem())).sum();
        helper.assertTrue(drops.size() == 4 && recovered == 96_000, "Breaking a full machine must recover all four batteries without truncating charge");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void deadMountDropsItsHarness(GameTestHelper helper) {
        Horse horse = helper.spawnWithNoFreeWill(EntityType.HORSE, new BlockPos(3, 2, 3));
        ((LaserMount) horse).universegate$setLaserHarness(true);
        horse.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
        helper.runAfterDelay(25, () -> {
            helper.assertItemEntityCountIs(ModItems.HORSE_LASER_HARNESS, new BlockPos(3, 2, 3), 4, 1);
            helper.succeed();
        });
    }
}
