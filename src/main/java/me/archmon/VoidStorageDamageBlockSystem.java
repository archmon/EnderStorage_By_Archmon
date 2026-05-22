package me.archmon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

class VoidStorageDamageBlockSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {

    private final VoidStorageManager manager;

    VoidStorageDamageBlockSystem(VoidStorageManager manager) {
        super(DamageBlockEvent.class);
        this.manager = manager;
    }

    @Override
    public Query<EntityStore> getQuery() {
        //noinspection unchecked
        return Query.and(new Query[]{Player.getComponentType()});
    }

    @Override
    public void handle(
            int id,
            @NonNull ArchetypeChunk<EntityStore> archetypeChunk,
            @NonNull Store<EntityStore> store,
            @NonNull CommandBuffer<EntityStore> commandBuffer,
            @NonNull DamageBlockEvent event
    ) {
        BlockType blockType = event.getBlockType();

        if (!this.manager.isPocket_DimensionSafeBlock(blockType)) {
            return;
        }

        Player player = this.getPlayerFromEvent(id, store);

        if (player == null) {
            event.setCancelled(true);
            return;
        }

        Vector3i targetBlock = event.getTargetBlock();

        boolean canModify = this.manager.canModifyPocketDimensionSafe(
                player,
                targetBlock.x,
                targetBlock.y,
                targetBlock.z
        );

        if (!canModify) {
            event.setCancelled(true);
            player.sendMessage(Message.raw("You do not have permission to damage this pocket dimension safe."));
        }
    }

    private Player getPlayerFromEvent(int id, Store<EntityStore> store) {
        try {
            @SuppressWarnings({"rawtypes", "unchecked"}) Ref refStoreID = new Ref(store, id);
            //noinspection unchecked
            return store.getComponent(refStoreID, Player.getComponentType());
        } catch (Exception ignored) {
            return null;
        }
    }
}
