//This class was heavily influenced by the EnderChestTickSystem class of the original EnderChest mod by 01Kvothe10

package me.archmon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("removal")
public class EnderStorageTickSystem extends EntityTickingSystem<EntityStore> {

    private final EnderStorageManager manager;
    private final Map<UUID, PendingWindowInspect> pendingWindowInspects = new ConcurrentHashMap<>();
    private final Map<ItemContainer, PendingPhysicalImport> pendingPhysicalImports = new ConcurrentHashMap<>();
    private long tickCounter = 0L;
    private static boolean apiProbed = false;

    public EnderStorageTickSystem(EnderStorageManager manager) {
        this.manager = manager;
    }

    public void schedulePhysicalEnderChestImport(ItemContainer worldContainer) {
        if (worldContainer == null) {
            return;
        }

        this.pendingPhysicalImports.put(worldContainer, new PendingPhysicalImport(worldContainer, this.tickCounter));
    }

    public void inspectPlayerWindowsNextTick(Player player, int x, int y, int z, int rotationIndex, BlockType blockType) {
        if (player == null) {
            return;
        }

        this.pendingWindowInspects.put(
                player.getUuid(),
                new PendingWindowInspect(player, x, y, z, rotationIndex, blockType)
        );
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(new Query[]{Player.getComponentType()});
    }

    @Override
    public void tick(float v, int i, ArchetypeChunk<EntityStore> archetypeChunk, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        this.tickCounter++;

        if (!apiProbed) {
            apiProbed = true;
            EnderStoragePlugin.onFirstTick();
        }

        if (!this.pendingPhysicalImports.isEmpty()) {
            Iterator<Map.Entry<ItemContainer, PendingPhysicalImport>> physicalImportIterator = this.pendingPhysicalImports.entrySet().iterator();

            while (physicalImportIterator.hasNext()) {
                Map.Entry<ItemContainer, PendingPhysicalImport> entry = physicalImportIterator.next();
                PendingPhysicalImport pendingPhysicalImport = entry.getValue();

                if (this.tickCounter - pendingPhysicalImport.createdTick < 1L) {
                    continue;
                }

                boolean imported = this.manager.importWorldEnderChestContainer(pendingPhysicalImport.worldContainer);

                if (imported) {
                    System.out.println("[EnderStorage] Auto-imported physical Ender_Chest contents after container change.");
                }

                physicalImportIterator.remove();
            }
        }

        if (!this.pendingWindowInspects.isEmpty()) {
            Iterator<Map.Entry<UUID, PendingWindowInspect>> inspectIterator = this.pendingWindowInspects.entrySet().iterator();

            while (inspectIterator.hasNext()) {
                Map.Entry<UUID, PendingWindowInspect> entry = inspectIterator.next();
                PendingWindowInspect pendingWindowInspect = entry.getValue();

                if (pendingWindowInspect.lastAttemptTick == this.tickCounter) {
                    continue;
                }

                pendingWindowInspect.lastAttemptTick = this.tickCounter;
                pendingWindowInspect.retries++;

                try {
                    Player player = pendingWindowInspect.player;

                    if (player == null) {
                        inspectIterator.remove();
                        continue;
                    }

                    ItemContainer worldContainer = this.findOpenWindowItemContainer(player);

                    if (worldContainer == null) {
                        if (pendingWindowInspect.retries >= 5) {
                            inspectIterator.remove();
                        }

                        continue;
                    }

                    this.manager.registerPhysicalEnderChestContainer(worldContainer);

                    boolean importedItems = this.manager.importWorldEnderChestContainer(worldContainer);
                    System.out.println("[EnderStorage] Import result: " + importedItems);

                    player.getWindowManager().closeAllWindows(player.getReference(), store);

                    this.manager.openSharedEnderChest(
                            player,
                            pendingWindowInspect.x,
                            pendingWindowInspect.y,
                            pendingWindowInspect.z,
                            pendingWindowInspect.rotationIndex,
                            pendingWindowInspect.blockType
                    );

                    inspectIterator.remove();
                } catch (Exception err) {
                    System.out.println("[EnderStorage] Window inspect failed: " + err.getMessage());

                    if (pendingWindowInspect.retries >= 5) {
                        inspectIterator.remove();
                    }
                }
            }
        }
    }

    private ItemContainer findOpenWindowItemContainer(Player player) {
        try {
            Object windowManager = player.getWindowManager();

            if (windowManager == null) {
                return null;
            }

            java.lang.reflect.Method getWindowsMethod = windowManager.getClass().getMethod("getWindows");
            Object windowsObject = getWindowsMethod.invoke(windowManager);

            if (windowsObject == null) {
                return null;
            }

            if (windowsObject instanceof Iterable<?> iterableWindows) {
                for (Object window : iterableWindows) {
                    ItemContainer itemContainer = this.getItemContainerFromWindow(window);

                    if (itemContainer != null) {
                        return itemContainer;
                    }
                }
            } else if (windowsObject instanceof Object[] windowArray) {
                for (Object window : windowArray) {
                    ItemContainer itemContainer = this.getItemContainerFromWindow(window);

                    if (itemContainer != null) {
                        return itemContainer;
                    }
                }
            } else if (windowsObject instanceof java.util.Map<?, ?> windowMap) {
                for (Object window : windowMap.values()) {
                    ItemContainer itemContainer = this.getItemContainerFromWindow(window);

                    if (itemContainer != null) {
                        return itemContainer;
                    }
                }
            } else {
                return this.getItemContainerFromWindow(windowsObject);
            }
        } catch (Exception err) {
            System.out.println("[EnderStorage] Failed to find open window item container: " + err.getMessage());
        }

        return null;
    }

    private ItemContainer getItemContainerFromWindow(Object window) {
        if (window == null) {
            return null;
        }

        try {
            java.lang.reflect.Method getItemContainerMethod = window.getClass().getMethod("getItemContainer");
            Object result = getItemContainerMethod.invoke(window);

            if (result instanceof ItemContainer itemContainer) {
                return itemContainer;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return null;
    }

    private static class PendingPhysicalImport {
        ItemContainer worldContainer;
        long createdTick;

        PendingPhysicalImport(ItemContainer worldContainer, long createdTick) {
            this.worldContainer = worldContainer;
            this.createdTick = createdTick;
        }
    }

    private static class PendingWindowInspect {
        Player player;
        int x;
        int y;
        int z;
        int rotationIndex;
        BlockType blockType;
        int retries;
        long lastAttemptTick;

        PendingWindowInspect(Player player, int x, int y, int z, int rotationIndex, BlockType blockType) {
            this.player = player;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rotationIndex = rotationIndex;
            this.blockType = blockType;
            this.retries = 0;
            this.lastAttemptTick = -1L;
        }
    }
}