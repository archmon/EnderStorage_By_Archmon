package me.archmon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

public class EnderStorageUseBlockSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private final EnderStorageManager manager;

    public EnderStorageUseBlockSystem(EnderStorageManager manager) {
        super(UseBlockEvent.Pre.class);
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
            UseBlockEvent.@NonNull Pre event
    ) {
        InteractionType interactionType = event.getInteractionType();

        if (interactionType != InteractionType.Use) {
            return;
        }

        BlockType blockType = event.getBlockType();

        //allow for player to open ender safe but not automation
        if (this.manager.isPocket_DimensionSafeBlock(blockType)) {
            Player player = this.getPlayerFromEvent(id, store, event);

            if (player != null) {
                Vector3i targetedBlock = event.getTargetBlock();
                BlockType blockType1 = event.getBlockType();
                byte rotationalIndex = 0;

                this.manager.openEnderStorage(
                        player,
                        targetedBlock.x,
                        targetedBlock.y,
                        targetedBlock.z,
                        rotationalIndex,
                        blockType1
                );

                event.setCancelled(true);
            }

            return;
        }

        // Allow players to open Ender_Chest. Automation must use the EnderStorage API/database path.
        if (this.manager.isEnderChestBlock(blockType)) {
            Player player = this.getPlayerFromEvent(id, store, event);

            if (player != null) {
                Vector3i targetedBlock = event.getTargetBlock();
                BlockType blockType1 = event.getBlockType();
                byte rotationalIndex = 0;

                this.manager.openSharedEnderChest(
                        player,
                        targetedBlock.x,
                        targetedBlock.y,
                        targetedBlock.z,
                        rotationalIndex,
                        blockType1
                );

                event.setCancelled(true);
            }
        }
    }

    private Player getPlayerFromEvent(int id, Store<EntityStore> store, UseBlockEvent.Pre event) {
        Player player = null;

        try {
            InteractionContext interactionContext = event.getContext();//note to self; getContext() is non-null

            @SuppressWarnings("rawtypes") Ref owningEntity = interactionContext.getOwningEntity();
            //noinspection unchecked
            player = store.getComponent(owningEntity, Player.getComponentType());

        } catch (Exception ignored) {
        }

        if (player == null) {
            @SuppressWarnings({"rawtypes", "unchecked"}) Ref refStoreID = new Ref(store, id);
            //noinspection unchecked
            player = store.getComponent(refStoreID, Player.getComponentType());
        }

        return player;
    }
}
