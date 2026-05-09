package me.archmon.event;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class TutorialBlockBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    public TutorialBlockBreakSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(int id, @NonNull ArchetypeChunk<EntityStore> archetypeChunk, @NonNull Store<EntityStore> store,
                       @NonNull CommandBuffer<EntityStore> commandBuffer, @NonNull BreakBlockEvent event) {
        // Gets called when Blocks Break
        var reference = archetypeChunk.getReferenceTo(id);
        Player player = store.getComponent(reference, Player.getComponentType());

        //following needed for replace block
        //World world = player.getWorld();
        //Vector3i targetBlock = event.getTargetBlock();

        player.sendMessage(Message.raw("You just broke the Block: " + event.getBlockType().getId()));
        /*
        * following line needs the second to work.
        * world.setBlock(targetBlock.x, targetBlock.y, targetBlock.z, "Rock_Crystal_Pink_Block");
        *
        * event.setCancelled(true);//makes it so no block can really break
        *
         */
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}