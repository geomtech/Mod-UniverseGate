package fr.geomtech.universegate;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class WeatherMachineCommand {

    private static final int SEARCH_RADIUS_XZ = 24;
    private static final int SEARCH_RADIUS_Y = 8;

    private WeatherMachineCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("universegate")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("weather_machine")
                                .then(Commands.literal("charge")
                                        .executes(context -> chargeNearest(context.getSource()))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> chargeAtPosition(
                                                        context.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos")
                                                ))
                                        )
                                )
                        )
        ));
    }

    private static int chargeNearest(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Cette commande sans position doit etre executee par un joueur operateur."));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();

        MeteorologicalControllerBlockEntity bestController = null;
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (int dy = -SEARCH_RADIUS_Y; dy <= SEARCH_RADIUS_Y; dy++) {
            for (int dx = -SEARCH_RADIUS_XZ; dx <= SEARCH_RADIUS_XZ; dx++) {
                for (int dz = -SEARCH_RADIUS_XZ; dz <= SEARCH_RADIUS_XZ; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (!(blockEntity instanceof MeteorologicalControllerBlockEntity controller)) continue;

                    double dist = pos.distSqr(center);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestController = controller;
                        bestPos = pos.immutable();
                    }
                }
            }
        }

        if (bestController == null || bestPos == null) {
            source.sendFailure(Component.literal("Aucun controleur meteorologique trouve dans un rayon de 24 blocs."));
            return 0;
        }

        return chargeController(source, bestPos, bestController);
    }

    private static int chargeAtPosition(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof MeteorologicalControllerBlockEntity controller)) {
            source.sendFailure(Component.literal("Aucun controleur meteorologique a cette position."));
            return 0;
        }

        return chargeController(source, pos, controller);
    }

    private static int chargeController(CommandSourceStack source,
                                        BlockPos controllerPos,
                                        MeteorologicalControllerBlockEntity controller) {
        MeteorologicalControllerBlockEntity.ForceChargeResult result = controller.forceFullCharge();

        return switch (result) {
            case CHARGED -> {
                source.sendSuccess(() -> Component.literal(
                        "Condensateurs charges immediatement a 100% pour la machine en " + formatPos(controllerPos) + "."
                ), true);
                yield 1;
            }
            case ALREADY_CHARGED -> {
                source.sendSuccess(() -> Component.literal(
                        "La machine en " + formatPos(controllerPos) + " est deja chargee a 100%."
                ), true);
                yield 1;
            }
            case SEQUENCE_RUNNING -> {
                source.sendFailure(Component.literal(
                        "Impossible de forcer la charge pendant une sequence meteo en cours."
                ));
                yield 0;
            }
        };
    }

    private static String formatPos(BlockPos pos) {
        return "[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
    }
}
