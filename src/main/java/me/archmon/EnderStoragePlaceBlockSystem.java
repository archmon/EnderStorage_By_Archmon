package me.archmon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

public class EnderStoragePlaceBlockSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private static final String POCKET_DIMENSION_SAFE_ITEM_ID = "pocket_DimensionSafe";


    private final EnderStorageManager manager;

    public EnderStoragePlaceBlockSystem(EnderStorageManager manager) {
        super(PlaceBlockEvent.class);
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
            @NonNull PlaceBlockEvent event
    ) {
        if (!this.isPocketDimensionSafePlacement(event)) {
            return;
        }

        Player player = this.getPlayerFromEvent(id, store);

        if (player == null) {
            return;
        }

        Vector3i targetBlock = event.getTargetBlock();

        this.manager.registerPlacedPocketDimensionSafe(
                player,
                targetBlock.x,
                targetBlock.y,
                targetBlock.z
        );
    }

    private boolean isPocketDimensionSafePlacement(PlaceBlockEvent event) {
        ItemStack itemInHand = event.getItemInHand();

        if (itemInHand == null || itemInHand.getItemId() == null) {
            return false;
        }

        return POCKET_DIMENSION_SAFE_ITEM_ID.equals(itemInHand.getItemId());
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
