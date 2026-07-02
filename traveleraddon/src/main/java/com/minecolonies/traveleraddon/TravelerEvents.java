package com.minecolonies.traveleraddon;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHall;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;
import java.util.List;

public final class TravelerEvents {
    private static final long DAY_START = 0L;
    private static final long DAY_END = 12000L;
    private static final long NIGHT_START = 12000L;
    private static final long NIGHT_END = 24000L;
    private static final long DESPAWN_TICKS = 6000L;
    private static final String STATE_KEY = "traveleraddon_state";

    @SubscribeEvent
    public void onLevelTick(final TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.isClientSide()) {
            return;
        }

        final long dayTime = level.getDayTime();
        final long timeOfDay = dayTime % 24000L;
        final boolean inDayWindow = timeOfDay >= DAY_START && timeOfDay < DAY_END;
        final boolean inNightWindow = timeOfDay >= NIGHT_START && timeOfDay < NIGHT_END;
        if (!inDayWindow && !inNightWindow) {
            return;
        }

        final long phase = dayTime / 24000L;
        final long windowKey = inDayWindow ? phase * 2L : phase * 2L + 1L;
        final TravelerState state = level.getDataStorage().computeIfAbsent(TravelerState::load, TravelerState::new, STATE_KEY);

        final List<IColony> colonies = IColonyManager.getInstance().getColonies(level);
        for (final IColony colony : colonies) {
            tickColony(level, colony, state, windowKey, dayTime);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(final PlayerInteractEvent.EntityInteract event) {
        final Entity target = event.getTarget();
        if (!(event.getEntity() instanceof ServerPlayer player) || !TravelerUtils.isTravelerEntity(target)) {
            return;
        }

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        final int remainingPurchases = target.getPersistentData().getInt("RemainingMarketPurchases");
        final int remainingDemandPurchases = target.getPersistentData().getInt("RemainingDemandPurchases");
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(player.level(), target.blockPosition());
        final int townHallLevel = colony != null && colony.getServerBuildingManager().hasTownHall()
            ? colony.getServerBuildingManager().getTownHall().getBuildingLevel()
            : 1;

        NetworkHooks.openScreen(player, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("screen.traveleraddon.title");
            }

            @Override
            public AbstractContainerMenu createMenu(final int containerId, final net.minecraft.world.entity.player.Inventory inventory, final net.minecraft.world.entity.player.Player menuPlayer) {
                return new TravelerMenu(containerId, inventory, target.getId(), remainingPurchases, remainingDemandPurchases, townHallLevel);
            }
        }, buffer -> {
            buffer.writeInt(target.getId());
            buffer.writeInt(remainingPurchases);
            buffer.writeInt(remainingDemandPurchases);
            buffer.writeInt(townHallLevel);
        });
    }

    @SubscribeEvent
    public void onTravelerDeath(final LivingDeathEvent event) {
        if (!(event.getEntity() instanceof WanderingTrader traveler) || !TravelerUtils.isTravelerEntity(traveler)) {
            return;
        }
        if (!(traveler.level() instanceof ServerLevel level)) {
            return;
        }

        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, traveler.blockPosition());
        if (colony == null) {
            return;
        }

        final TravelerState state = level.getDataStorage().computeIfAbsent(TravelerState::load, TravelerState::new, STATE_KEY);
        final TravelerState.Record record = state.getOrCreate(colony.getID());
        if (traveler.getUUID().equals(record.entityUuid)) {
            record.entityUuid = null;
            state.setDirty();
        }

        TravelerUtils.notifyColonyPlayers(
            colony,
            Component.translatable("message.traveleraddon.traveler_killed", describeDamage(event.getSource()))
        );
    }

    private void tickColony(
        final ServerLevel level,
        final IColony colony,
        final TravelerState state,
        final long windowKey,
        final long dayTime
    ) {
        final var structures = colony.getServerBuildingManager();
        if (!structures.hasTownHall()) {
            return;
        }

        final TravelerState.Record record = state.getOrCreate(colony.getID());
        final Entity currentEntity = record.entityUuid == null ? null : level.getEntity(record.entityUuid);
        if (currentEntity != null && currentEntity.isAlive() && TravelerUtils.isTravelerEntity(currentEntity)) {
            final long spawnTime = currentEntity.getPersistentData().getLong("TravelerSpawnTime");
            if (dayTime - spawnTime >= DESPAWN_TICKS) {
                currentEntity.discard();
                record.entityUuid = null;
                state.setDirty();
                TravelerUtils.notifyColonyPlayers(colony, Component.translatable("message.traveleraddon.traveler_departed"));
            } else if (record.windowKey != windowKey) {
                currentEntity.discard();
                record.entityUuid = null;
                state.setDirty();
                TravelerUtils.notifyColonyPlayers(colony, Component.translatable("message.traveleraddon.traveler_departed"));
            } else {
                return;
            }
        }

        if (record.windowKey == windowKey) {
            return;
        }

        final ITownHall townHall = structures.getTownHall();
        final BlockPos anchor = townHall.getPosition();
        final IWareHouse warehouse = TravelerUtils.findWarehouse(colony);
        if (warehouse == null || warehouse.getTileEntity() == null) {
            return;
        }

        final BlockPos spawnPos = TravelerUtils.findSpawnPos(level, anchor);
        if (spawnPos == null) {
            TravelerAddonMod.LOGGER.debug("Traveler spawn skipped: no safe position near town hall {}", anchor);
            return;
        }

        final WanderingTrader traveler = TravelerUtils.spawnTraveler(level, spawnPos, colony.getID());
        if (traveler == null) {
            return;
        }

        traveler.getPersistentData().putLong("TravelerSpawnTime", dayTime);
        traveler.getPersistentData().putInt("RemainingDemandPurchases", TravelerUtils.demandPurchasesForTownHall(townHall));
        traveler.getPersistentData().putInt("RemainingMarketPurchases", TravelerUtils.marketPurchasesForTownHall(townHall));
        record.windowKey = windowKey;
        record.entityUuid = traveler.getUUID();
        state.setDirty();

        TravelerAddonMod.LOGGER.info("Traveler spawned for colony {} at {}", colony.getID(), spawnPos);
        TravelerUtils.notifyColonyPlayers(colony, Component.translatable("message.traveleraddon.traveler_arrived"));
    }

    private static Component describeDamage(final DamageSource source) {
        final Entity attacker = source.getEntity();
        if (attacker != null) {
            return attacker.getDisplayName();
        }
        return Component.translatable("message.traveleraddon.unknown_attacker");
    }
}
