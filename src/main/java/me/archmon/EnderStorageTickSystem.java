//This class was heavily influenced by the EnderChestTickSystem class of the original EnderChest mod by 01Kvothe10

package me.archmon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;


public class EnderStorageTickSystem extends EntityTickingSystem<EntityStore> {

    private final EnderStorageManager manager;
    private static boolean apiProbed = false;

    public EnderStorageTickSystem(EnderStorageManager manager) {
        this.manager = manager;
    }

    @Override
    public Query<EntityStore> getQuery() {
        //noinspection unchecked
        return Query.and(new Query[]{Player.getComponentType()});
    }

    @Override
    public void tick(float v, int i, @NonNull ArchetypeChunk<EntityStore> archetypeChunk, @NonNull Store<EntityStore> store, @NonNull CommandBuffer<EntityStore> commandBuffer) {
        if (!apiProbed) {
            apiProbed = true;
            EnderStoragePlugin.onFirstTick();
        }
    }
}