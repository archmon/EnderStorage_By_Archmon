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
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

public class EnderStorageBreakBlockSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private final EnderStorageManager manager;

    public EnderStorageBreakBlockSystem(EnderStorageManager manager) {
        super(BreakBlockEvent.class);
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
            @NonNull BreakBlockEvent event
    ) {
        BlockType blockType = event.getBlockType();

        if (!this.manager.isPocket_DimensionSafeBlock(blockType)) {
            return;
        }

        Player player = this.getPlayerFromEvent(id, store, event);

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
            player.sendMessage(Message.raw("You do not have permission to break this pocket dimension safe."));
            return;
        }

        boolean safeIsEmpty = this.manager.isPocketDimensionSafeEmpty(
                targetBlock.x,
                targetBlock.y,
                targetBlock.z
        );

        if (!safeIsEmpty) {
            event.setCancelled(true);
            player.sendMessage(Message.raw("This pocket dimension safe still contains items. Empty it before breaking it."));
            return;
        }

        this.manager.deletePocketDimensionSafeData(
                targetBlock.x,
                targetBlock.y,
                targetBlock.z
        );

        /*
         * TODO:
         * Drop itemContainer contents into the world using ItemComponent.generateItemDrop(...)
         * or ItemComponent.generateItemDrops(...).
         *
         * Database cleanup is now handled above.
         */
    }

    private Player getPlayerFromEvent(int id, Store<EntityStore> store, BreakBlockEvent event) {
        try {
            @SuppressWarnings({"rawtypes", "unchecked"}) Ref refStoreID = new Ref(store, id);
            //noinspection unchecked
            return store.getComponent(refStoreID, Player.getComponentType());
        } catch (Exception ignored) {
            return null;
        }
    }
}
