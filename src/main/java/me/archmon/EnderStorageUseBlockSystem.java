//This class was heavily influenced by the EnderChestUseBlockSystem class of the original EnderChest mod by 01Kvothe10

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

import static java.lang.IO.println;

public class EnderStorageUseBlockSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private final EnderStorageManager manager;

    public EnderStorageUseBlockSystem(EnderStorageManager manager) {
        super(UseBlockEvent.Pre.class);
        this.manager = manager;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(new Query[]{Player.getComponentType()});
    }

    @Override
    public void handle(int id, @NonNull ArchetypeChunk<EntityStore> archetypeChunk, @NonNull Store<EntityStore> store, @NonNull CommandBuffer<EntityStore> commandBuffer, UseBlockEvent.@NonNull Pre event) {

        InteractionType interactionType = event.getInteractionType();
        /*if (event.getBlockType().getId().contains("EnderSafe")){
            println("[EnderStorage] Endersafe has been interacted with by ? and didn't use .use");
        }*/
        if (interactionType == InteractionType.Use) {

            BlockType blockType = event.getBlockType();
            String interactedBlockName;
            if (blockType != null) { //consider removing if cause it's always true
                interactedBlockName = blockType.getId();
            } else {
                interactedBlockName = "null";
            }

            if (interactedBlockName != null && interactedBlockName.contains("EnderSafe")) {//I think this is what intercepts the use of the chest
                Player player = null;
                //println("[EnderStorage] Endersafe has been interacted with by ?");
                try {
                    InteractionContext interactionContext = event.getContext();
                    if (interactionContext != null) {
                        Ref owningEntity = interactionContext.getOwningEntity();
                        if (owningEntity != null) {
                            player = store.getComponent(owningEntity, Player.getComponentType());
                            //println("[EnderStorage] Endersafe has been interacted with by :"+owningEntity);
                        }//else {println("[EnderStorage] Endersafe has been interacted with by null");}
                    }
                } catch (Exception err){
                }

                if (player == null) {
                    Ref refStoreID = new Ref(store,id);
                    player = (Player)store.getComponent(refStoreID, Player.getComponentType());
                    //println("[EnderStorage] Endersafe has been interacted with by null");
                }

                if (player != null) {
                    Vector3i targetedBlock = event.getTargetBlock();
                    BlockType blockType1 = event.getBlockType();
                    byte rotationalIndex =0;
                    this.manager.openEnderStorage(player, targetedBlock.x, targetedBlock.y, targetedBlock.z, rotationalIndex, blockType1);
                    event.setCancelled(true);
                    //println("[EnderStorage] Endersafe has been interacted with by:" + player);
                }
            }

        }
    }


}
