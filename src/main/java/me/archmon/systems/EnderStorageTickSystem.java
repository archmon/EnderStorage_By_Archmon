//This class was heavily influenced by the EnderChestTickSystem class of the original EnderChest mod by 01Kvothe10

package me.archmon.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.archmon.EnderStoragePlugin;
import org.jspecify.annotations.NonNull;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("removal")
public class EnderStorageTickSystem extends EntityTickingSystem<EntityStore> {

    private final EnderStorageManager manager;
    private final Map<UUID, PendingOpen> pendingOpens = new ConcurrentHashMap();
    private static final int MAX_Retries = 10;
    private long tickCounter = 0L;
    private static boolean apiProbed = false;

    public EnderStorageTickSystem(EnderStorageManager manager) {
        this.manager = manager;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(new Query[]{Player.getComponentType()});
    }

    @Override
    public void tick(float v, int i, @NonNull ArchetypeChunk<EntityStore> archetypeChunk, @NonNull Store<EntityStore> store, @NonNull CommandBuffer<EntityStore> commandBuffer) {
        if (!apiProbed) {
            apiProbed = true;
            EnderStoragePlugin.onFirstTick();
        }

        if (!this.pendingOpens.isEmpty()) {
            ++this.tickCounter;
            Iterator iterator = this.pendingOpens.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry)iterator.next();
                PendingOpen pendingOpenValue = (PendingOpen)entry.getValue();
                Player player = pendingOpenValue.player;
                if (pendingOpenValue.lastAttemptTick < this.tickCounter) {
                    pendingOpenValue.lastAttemptTick = this.tickCounter;
                    if (player == null) {
                        iterator.remove();
                    } else if (pendingOpenValue.retries <1) {
                        ++pendingOpenValue.retries;
                    }else {
                        try {
                            player.getWindowManager().closeAllWindows(player.getReference(), store);
                            this.manager.openEnderStorage(player, pendingOpenValue.x, pendingOpenValue.y, pendingOpenValue.z, pendingOpenValue.rotationIndex, pendingOpenValue.blockType);
                        } catch (Exception errCatch) {
                            PrintStream errorMessage = System.err;
                            String errorMessage1 = String.valueOf(player.getUuid());
                            errorMessage.println("[EnderStorage] Error loading data for " + errorMessage1 + ": " + errCatch.getMessage());
                        }

                        iterator.remove();
                    }
                }
            }
        }
    }



    private static class PendingOpen {
        Player player;
        int x;
        int y;
        int z;
        int rotationIndex;
        BlockType blockType;
        int retries;
        long lastAttemptTick;

        PendingOpen(Player player, int x, int y, int z, int rotationIndex, BlockType blockType) {
            this.player = player;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rotationIndex = rotationIndex;
            this.blockType = blockType;
            this.retries = 0;
            this.lastAttemptTick = 0L;
        }
    }
}
