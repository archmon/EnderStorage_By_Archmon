//This class was heavily influenced by the EnderChestBreakBlockSystem class of the original EnderChest mod by 01Kvothe10

package me.archmon.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.Set;

public class EnderStorageBreakSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {

    private final EnderStorageManager manager;
    private final Set<Long> clearedBlocks = new HashSet<>();
    private static final float CLEAR_THRESHOLD = 0.5F;

    protected EnderStorageBreakSystem(EnderStorageManager manager) {
        super(DamageBlockEvent.class);
        this.manager = manager;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(new Query[]{Player.getComponentType()});
    }

    @Override
    public void handle(int i, @NonNull ArchetypeChunk<EntityStore> archetypeChunk, @NonNull Store<EntityStore> store, @NonNull CommandBuffer<EntityStore> commandBuffer, @NonNull DamageBlockEvent damageBlockEvent) {
        BlockType blockType = damageBlockEvent.getBlockType();

        if (blockType != null) {
            String blockTypeIdString = blockType.getId();
            if (blockTypeIdString != null && blockTypeIdString.contains("Ender_Safe")) {
                Vector3i blockPosition = damageBlockEvent.getTargetBlock();
                if (blockPosition == null) { //consider removing cause it's always false
                    return;
                }

                long blockPositionLong = (long)blockPosition.getX() << 42 | (long)(blockPosition.getY() & 1048575) << 22 | (long)(blockPosition.getZ()) & 4194303;
                if (this.clearedBlocks.contains(blockPositionLong)) {
                    return;
                }

                float blockEventCurrentDamage = damageBlockEvent.getCurrentDamage();
                float blockEventGetDamage = damageBlockEvent.getDamage();
                float totalDamage = blockEventCurrentDamage + blockEventGetDamage;

                if (totalDamage >= 0.5F) {
                    boolean blockBrokenBoolean = this.manager.clearContainerAt(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
                    if (blockBrokenBoolean) {
                        this.clearedBlocks.add(blockPositionLong);
                        if (this.clearedBlocks.size() > 1000) {
                            this.clearedBlocks.clear();
                        }
                    }
                }
            }
        }
    }


}
