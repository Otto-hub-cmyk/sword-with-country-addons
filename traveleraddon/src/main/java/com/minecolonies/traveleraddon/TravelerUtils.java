package com.minecolonies.traveleraddon;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHall;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class TravelerUtils {
    public static final String TRAVELER_TAG = "traveleraddon.traveler";
    public static final String TRAVELER_COLONY_ID = "TravelerColonyId";

    private TravelerUtils() {
    }

    public static boolean isTravelerEntity(final Entity entity) {
        return entity != null && entity.getTags().contains(TRAVELER_TAG);
    }

    public static IWareHouse findWarehouse(final IColony colony) {
        return colony.getServerBuildingManager().hasWarehouse()
            ? colony.getServerBuildingManager().getClosestWarehouseInColony(colony.getCenter())
            : null;
    }

    public static List<ItemStack> createBaseBundle() {
        final List<ItemStack> stacks = new ArrayList<>();
        TravelerCatalog.baseBundle().forEach(entry -> stacks.add(new ItemStack(entry.item(), entry.count())));
        return stacks;
    }

    public static int marketPurchasesForTownHall(final ITownHall townHall) {
        final int level = Math.max(1, Math.min(5, townHall.getBuildingLevel()));
        return 5 * level + 5;
    }

    public static int demandPurchasesForTownHall(final ITownHall townHall) {
        return Math.max(1, Math.min(5, townHall.getBuildingLevel()));
    }

    public static void notifyColonyPlayers(final IColony colony, final Component message) {
        for (final Player player : colony.getMessagePlayerEntities()) {
            player.displayClientMessage(message, false);
        }
    }

    public static BlockPos findSpawnPos(final Level level, final BlockPos anchor) {
        final BlockPos[] candidates = new BlockPos[] {
            anchor.east(),
            anchor.west(),
            anchor.south(),
            anchor.north(),
            anchor.east().south(),
            anchor.west().south(),
            anchor.east().north(),
            anchor.west().north()
        };

        for (final BlockPos candidate : candidates) {
            if (canStandAt(level, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public static WanderingTrader spawnTraveler(final ServerLevel level, final BlockPos spawnPos, final int colonyId) {
        final WanderingTrader traveler = EntityType.WANDERING_TRADER.create(level);
        if (traveler == null) {
            return null;
        }

        traveler.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        traveler.setCustomName(Component.translatable("entity.traveleraddon.traveler"));
        traveler.setCustomNameVisible(true);
        traveler.addTag(TRAVELER_TAG);
        traveler.getPersistentData().putInt(TRAVELER_COLONY_ID, colonyId);
        traveler.setPersistenceRequired();
        traveler.setNoAi(true);
        traveler.setInvulnerable(true);
        traveler.setHealth(traveler.getMaxHealth());

        if (level.addFreshEntity(traveler)) {
            return traveler;
        }
        return null;
    }

    public static boolean canStandAt(final Level level, final BlockPos pos) {
        final BlockPos above = pos.above();
        final BlockPos twoAbove = above.above();
        final BlockState floor = level.getBlockState(pos.below());
        return floor.isFaceSturdy(level, pos.below(), Direction.UP)
            && level.getBlockState(pos).isAir()
            && level.getBlockState(above).isAir()
            && level.getBlockState(twoAbove).isAir();
    }
}
