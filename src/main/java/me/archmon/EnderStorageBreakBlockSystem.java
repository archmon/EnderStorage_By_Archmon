package me.archmon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
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
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;


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
            player.sendMessage(Message.raw("You do not have permission to break this pocket dimension safe."));
            return;
        }

        ItemContainer itemContainer = this.manager.getPocketDimensionSafeContents(
                targetBlock.x,
                targetBlock.y,
                targetBlock.z
        );

        List<ItemStack> itemStacks = this.getContainerItemStacks(itemContainer);

        if (!itemStacks.isEmpty()) {
            boolean droppedContents = this.dropPocketDimensionSafeContents(commandBuffer, itemStacks, targetBlock);

            if (!droppedContents) {
                event.setCancelled(true);
                player.sendMessage(Message.raw("Unable to drop pocket dimension safe contents. The safe was not broken."));
                return;
            }
        }

        this.manager.deletePocketDimensionSafeData(
                targetBlock.x,
                targetBlock.y,
                targetBlock.z
        );
    }

    private List<ItemStack> getContainerItemStacks(ItemContainer itemContainer) {
        List<ItemStack> itemStacks = new ArrayList<>();
        short capacity = itemContainer.getCapacity();

        for (short slot = 0; slot < capacity; slot++) {
            ItemStack itemStack = itemContainer.getItemStack(slot);

            if (itemStack != null && !itemStack.isEmpty()) {
                itemStacks.add(itemStack.withQuantity(itemStack.getQuantity()));
            }
        }

        return itemStacks;
    }

    private boolean dropPocketDimensionSafeContents(
            CommandBuffer<EntityStore> commandBuffer,
            List<ItemStack> itemStacks,
            Vector3i targetBlock
    ) {
        Vector3d dropPosition = new Vector3d(
                targetBlock.x + 0.5d,
                targetBlock.y + 1.25d,
                targetBlock.z + 0.5d
        );

        Vector3f dropRotation = new Vector3f(0.0f, 0.0f, 0.0f);

        Holder<EntityStore>[] dropHolders = ItemComponent.generateItemDrops(
                commandBuffer,
                itemStacks,
                dropPosition,
                dropRotation
        );

        if (dropHolders.length != itemStacks.size()) {
            System.err.println("[EnderStorage] Failed to generate all safe item drops at " + dropPosition
                    + ". Expected " + itemStacks.size() + ", generated " + dropHolders.length + ".");
            return false;
        }

        commandBuffer.addEntities(dropHolders, AddReason.SPAWN);

        System.out.println("[EnderStorage] Dropped " + dropHolders.length + " safe item stacks at " + dropPosition);
        return true;
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
