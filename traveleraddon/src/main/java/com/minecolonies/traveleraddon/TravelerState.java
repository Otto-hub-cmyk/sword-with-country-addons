package com.minecolonies.traveleraddon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TravelerState extends SavedData {
    private final Map<Integer, Record> records = new HashMap<>();

    public static TravelerState load(final CompoundTag tag) {
        final TravelerState state = new TravelerState();
        final CompoundTag colonies = tag.getCompound("colonies");
        for (final String key : colonies.getAllKeys()) {
            final CompoundTag entry = colonies.getCompound(key);
            final Record record = new Record();
            record.windowKey = entry.getLong("windowKey");
            if (entry.hasUUID("entityUuid")) {
                record.entityUuid = entry.getUUID("entityUuid");
            }
            state.records.put(Integer.parseInt(key), record);
        }
        return state;
    }

    public Record getOrCreate(final int colonyId) {
        return records.computeIfAbsent(colonyId, ignored -> new Record());
    }

    @Override
    public CompoundTag save(final CompoundTag tag) {
        final CompoundTag colonies = new CompoundTag();
        for (final Map.Entry<Integer, Record> entry : records.entrySet()) {
            final CompoundTag recordTag = new CompoundTag();
            recordTag.putLong("windowKey", entry.getValue().windowKey);
            if (entry.getValue().entityUuid != null) {
                recordTag.putUUID("entityUuid", entry.getValue().entityUuid);
            }
            colonies.put(Integer.toString(entry.getKey()), recordTag);
        }
        tag.put("colonies", colonies);
        return tag;
    }

    public static final class Record {
        public long windowKey = Long.MIN_VALUE;
        public UUID entityUuid;
    }
}
