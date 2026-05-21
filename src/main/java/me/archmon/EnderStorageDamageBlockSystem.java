package me.archmon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

public class EnderStorageDamageBlockSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {

    private final EnderStorageManager manager;

    public EnderStorageDamageBlockSystem(EnderStorageManager manager) {
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

        if (this.manager.isEnderChestBlock(blockType)
                && this.manager.isEnderWrenchItem(event.getItemInHand())) {
            Player player = this.getPlayerFromEvent(id, store, event);

            if (player != null && this.isCrouching(player, store)) {
                Vector3i targetBlock = event.getTargetBlock();
                event.setCancelled(true);
                this.manager.openEnderChestWrenchWindow(
                        player,
                        targetBlock.x,
                        targetBlock.y,
                        targetBlock.z
                );
            }

            return;
        }

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
            player.sendMessage(Message.raw("You do not have permission to damage this pocket dimension safe."));
        }
    }

    private Player getPlayerFromEvent(int id, Store<EntityStore> store, DamageBlockEvent event) {
        try {
            @SuppressWarnings({"rawtypes", "unchecked"}) Ref refStoreID = new Ref(store, id);
            //noinspection unchecked
            return store.getComponent(refStoreID, Player.getComponentType());
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isCrouching(Player player, Store<EntityStore> store) {
        try {
            @SuppressWarnings("rawtypes") Ref playerReference = player.getReference();

            if (playerReference == null) {
                return false;
            }

            //noinspection unchecked
            MovementStatesComponent movementStatesComponent = store.getComponent(
                    playerReference,
                    MovementStatesComponent.getComponentType()
            );

            if (movementStatesComponent == null) {
                return false;
            }

            MovementStates movementStates = movementStatesComponent.getMovementStates();
            return movementStates != null && (movementStates.crouching || movementStates.forcedCrouching);
        } catch (Exception ignored) {
            return false;
        }
    }
}
