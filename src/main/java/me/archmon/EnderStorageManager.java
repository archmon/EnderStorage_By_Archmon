package me.archmon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.util.BsonUtil;
import org.bson.BsonDocument;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("removal")
public class EnderStorageManager {

    private static final short INVENTORY_SLOT_COUNT = 54;
    private static final String DEFAULT_ENDER_CHEST_COLOR_CODE = "0:0:0";

    private final Map<String, ItemContainer> loadedPocketDimensionSafeContainers = new ConcurrentHashMap<>();
    private final Map<String, ItemContainer> loadedEnderChestContainers = new ConcurrentHashMap<>();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final DatabaseHandler dbJsonObject;

    public EnderStorageManager(JsonObject dbConfigFile) {
        JsonObject databaseJsonObject;

        if (dbConfigFile != null && dbConfigFile.has("database")) {
            databaseJsonObject = dbConfigFile.get("database").getAsJsonObject();
        } else {
            databaseJsonObject = new JsonObject();
        }

        this.dbJsonObject = new DatabaseHandler(databaseJsonObject);

        try {
            this.dbJsonObject.dbConnect();
        } catch (Exception databaseFailedConnect) {
            System.err.println("[EnderStorage] Failed to connect to database: " + databaseFailedConnect.getMessage());
        }
    }

    public boolean isEnderChestBlock(BlockType blockType) {
        return blockType != null
                && blockType.getId() != null
                && blockType.getId().contains("Ender_Chest");
    }

    public boolean isPocket_DimensionSafeBlock(BlockType blockType) {
        return blockType != null
                && blockType.getId() != null
                && blockType.getId().contains("pocket_DimensionSafe");
    }

    private void sendPlayerMessage(Player player, String message) {
        if (player != null) {
            player.sendMessage(Message.raw(message));
        }
    }

    public void openPocketDimensionSafe(Player player, int posX, int posY, int posZ, int rotationIndex, BlockType blockType) {
        if (player == null) {
            return;
        }

        String locationKey = this.createPocketDimensionSafeLocationKey(posX, posY, posZ);

        if (!this.canAccessPocketDimensionSafe(player, locationKey)) {
            sendPlayerMessage(player, "You do not have permission to access this Pocket Dimension Safe.");
            return;
        }

        UUID ownerUuid = this.getOrCreatePocketDimensionSafeOwner(locationKey, player.getUuid());

        if (ownerUuid == null) {
            return;
        }

        ItemContainer itemContainer = this.getPocketDimensionSafeContainer(locationKey, ownerUuid);

        this.openContainerForPlayer(
                player,
                itemContainer,
                posX,
                posY,
                posZ,
                rotationIndex,
                blockType,
                true,
                () -> this.savePocketDimensionSafeContainer(locationKey, ownerUuid, itemContainer)
        );
    }

    public void openSharedEnderChest(Player player, int posX, int posY, int posZ, int rotationIndex, BlockType blockType) {
        if (player == null) {
            return;
        }

        String colorCode = DEFAULT_ENDER_CHEST_COLOR_CODE;
        UUID playerUuid = null;
        ItemContainer itemContainer = this.getEnderChestContainer(colorCode, playerUuid);

        this.openContainerForPlayer(
                player,
                itemContainer,
                posX,
                posY,
                posZ,
                rotationIndex,
                blockType,
                true,
                () -> this.saveEnderChestContainer(colorCode, playerUuid, itemContainer)
        );
    }

    public ItemContainer getSharedEnderChestContainer() {
        return this.getEnderChestContainer(DEFAULT_ENDER_CHEST_COLOR_CODE, null);
    }

    public ItemContainer getEnderChestInventoryForAutomation() {
        return this.getSharedEnderChestContainer();
    }

    public void saveEnderChestInventory() {
        ItemContainer itemContainer = this.getSharedEnderChestContainer();
        this.saveEnderChestContainer(DEFAULT_ENDER_CHEST_COLOR_CODE, null, itemContainer);
    }

    public ItemContainer removePocketDimensionSafeAndReturnContents(int posX, int posY, int posZ) {
        String locationKey = this.createPocketDimensionSafeLocationKey(posX, posY, posZ);
        ItemContainer itemContainer = this.loadPocketDimensionSafeContainer(locationKey);

        try {
            this.dbJsonObject.deletePocketDimensionSafe(locationKey);
            this.loadedPocketDimensionSafeContainers.remove(locationKey);
        } catch (Exception errCatch) {
            System.err.println("[EnderStorage] Failed to delete pocket_DimensionSafe at " + locationKey + ": " + errCatch.getMessage());
        }

        return itemContainer;
    }

    public boolean canDestroyPocketDimensionSafe(Player player, int posX, int posY, int posZ) {
        if (player == null) {
            return false;
        }

        String locationKey = this.createPocketDimensionSafeLocationKey(posX, posY, posZ);
        return this.canAccessPocketDimensionSafe(player, locationKey);
    }

    public void saveAll() {
        for (Map.Entry<String, ItemContainer> keyValue : this.loadedPocketDimensionSafeContainers.entrySet()) {
            String locationKey = keyValue.getKey();
            ItemContainer itemContainer = keyValue.getValue();

            try {
                UUID ownerUuid = this.dbJsonObject.getPocketDimensionSafeOwner(locationKey);

                if (ownerUuid != null) {
                    this.savePocketDimensionSafeContainer(locationKey, ownerUuid, itemContainer);
                }
            } catch (Exception errCatch) {
                System.err.println("[EnderStorage] Failed to save pocket_DimensionSafe at " + locationKey + ": " + errCatch.getMessage());
            }
        }

        for (Map.Entry<String, ItemContainer> keyValue : this.loadedEnderChestContainers.entrySet()) {
            String enderChestStorageKey = keyValue.getKey();
            EnderChestKey parsedKey = EnderChestKey.fromStorageKey(enderChestStorageKey);
            ItemContainer itemContainer = keyValue.getValue();

            this.saveEnderChestContainer(parsedKey.colorCode, parsedKey.playerUuid, itemContainer);
        }

        this.dbJsonObject.close();
    }

    public void setTickSystem(EnderStorageTickSystem tick) {
        // Kept for compatibility with EnderStoragePlugin.
        // Recipe-removal timing is handled through EnderStorageTickSystem.
    }

    private ItemContainer getPocketDimensionSafeContainer(String locationKey, UUID ownerUuid) {
        ItemContainer itemContainer = this.loadedPocketDimensionSafeContainers.get(locationKey);

        if (itemContainer != null && itemContainer.getCapacity() == INVENTORY_SLOT_COUNT) {
            return itemContainer;
        }

        if (itemContainer != null) {
            this.savePocketDimensionSafeContainer(locationKey, ownerUuid, itemContainer);
            this.loadedPocketDimensionSafeContainers.remove(locationKey);
        }

        itemContainer = this.loadPocketDimensionSafeContainer(locationKey);
        this.loadedPocketDimensionSafeContainers.put(locationKey, itemContainer);

        //removed cause duplication glitch
        //final ItemContainer finalItemContainer = itemContainer;
        //itemContainer.registerChangeEvent((_) -> this.savePocketDimensionSafeContainer(locationKey, ownerUuid, finalItemContainer));

        return itemContainer;
    }

    private ItemContainer getEnderChestContainer(String colorCode, UUID optionalPlayerUuid) {
        String enderChestStorageKey = EnderChestKey.toStorageKey(colorCode, optionalPlayerUuid);
        ItemContainer itemContainer = this.loadedEnderChestContainers.get(enderChestStorageKey);

        if (itemContainer != null && itemContainer.getCapacity() == INVENTORY_SLOT_COUNT) {
            return itemContainer;
        }

        if (itemContainer != null) {
            this.saveEnderChestContainer(colorCode, optionalPlayerUuid, itemContainer);
            this.loadedEnderChestContainers.remove(enderChestStorageKey);
        }

        itemContainer = this.loadEnderChestContainer(colorCode, optionalPlayerUuid);
        this.loadedEnderChestContainers.put(enderChestStorageKey, itemContainer);

        //removed cause duplication glitch
        //final ItemContainer finalItemContainer = itemContainer;
        //itemContainer.registerChangeEvent((_) -> this.saveEnderChestContainer(colorCode, playerUuid, finalItemContainer));

        return itemContainer;
    }

    private void openContainerForPlayer(
            Player player,
            ItemContainer itemContainer,
            int posX,
            int posY,
            int posZ,
            int rotationIndex,
            BlockType blockType,
            boolean useBlockWindow,
            Runnable closeAction
    ) {
        Object containerWindow;

        if (useBlockWindow && blockType != null && itemContainer.getCapacity() == INVENTORY_SLOT_COUNT) {
            containerWindow = new ContainerBlockWindow(posX, posY, posZ, rotationIndex, blockType, itemContainer);
        } else {
            containerWindow = new ContainerWindow(itemContainer);
        }

        ((Window) containerWindow).registerCloseEvent((_) -> closeAction.run());

        @SuppressWarnings("rawtypes") Ref playerReference = player.getReference();

        if (playerReference == null) {
            return;
        }

        @SuppressWarnings("rawtypes") Store playerReferenceStore = playerReference.getStore();

        //noinspection unchecked
        player.getPageManager().setPageWithWindows(
                playerReference,
                playerReferenceStore,
                Page.Bench,
                true,
                new Window[]{(Window) containerWindow}
        );
    }

    private String createPocketDimensionSafeLocationKey(int posX, int posY, int posZ) {
        return posX + ":" + posY + ":" + posZ;
    }

    private boolean canAccessPocketDimensionSafe(Player player, String locationKey) {
        try {
            UUID ownerUuid = this.dbJsonObject.getPocketDimensionSafeOwner(locationKey);

            if (ownerUuid == null) {
                return true;
            }

            if (ownerUuid.equals(player.getUuid())) {
                return true;
            }

            return this.isServerOperator(player);
        } catch (Exception errCatch) {
            System.err.println("[EnderStorage] Failed to check pocket_DimensionSafe owner for " + locationKey + ": " + errCatch.getMessage());
            return false;
        }
    }

    private UUID getOrCreatePocketDimensionSafeOwner(String locationKey, UUID fallbackOwnerUuid) {
        try {
            UUID existingOwnerUuid = this.dbJsonObject.getPocketDimensionSafeOwner(locationKey);

            if (existingOwnerUuid != null) {
                return existingOwnerUuid;
            }

            this.dbJsonObject.savePocketDimensionSafeInventory(locationKey, fallbackOwnerUuid, "{}");
            return fallbackOwnerUuid;
        } catch (Exception errCatch) {
            System.err.println("[EnderStorage] Failed to create pocket_DimensionSafe owner for " + locationKey + ": " + errCatch.getMessage());
            return null;
        }
    }

    private boolean isServerOperator(Player player) {
        /*
         * TODO:
         * Replace this with the official Hytale permission/op check once the correct API call is known.
         *
         * For now this returns false so pocket_DimensionSafe access fails closed.
         */
        return false;
    }

    private ItemContainer loadPocketDimensionSafeContainer(String locationKey) {
        try {
            String inventoryJson = this.dbJsonObject.getPocketDimensionSafeInventory(locationKey);
            return this.itemContainerFromJson(inventoryJson, "pocket_DimensionSafe " + locationKey);
        } catch (Exception errCatch) {
            System.err.println("[EnderStorage] Error loading pocket_DimensionSafe data for " + locationKey + ": " + errCatch.getMessage());
            return new SimpleItemContainer(INVENTORY_SLOT_COUNT);
        }
    }

    public void savePocketDimensionSafeContainer(String locationKey, UUID ownerUuid, ItemContainer itemContainer) {
        if (!this.canUseDatabase()) {
            return;
        }

        try {
            String inventoryJson = this.itemContainerToJson(itemContainer);
            this.dbJsonObject.savePocketDimensionSafeInventory(locationKey, ownerUuid, inventoryJson);
        } catch (Exception errCatch) {
            System.err.println("[EnderStorage] Error saving pocket_DimensionSafe data for " + locationKey + ": " + errCatch.getMessage());
        }
    }

    private ItemContainer loadEnderChestContainer(String colorCode, UUID playerUuid) {
        try {
            String inventoryJson = this.dbJsonObject.getEnderChestInventory(colorCode, playerUuid);
            return this.itemContainerFromJson(inventoryJson, "Ender_Chest " + colorCode);
        } catch (Exception errCatch) {
            System.err.println("[EnderStorage] Error loading Ender_Chest data for " + colorCode + ": " + errCatch.getMessage());
            return new SimpleItemContainer(INVENTORY_SLOT_COUNT);
        }
    }

    private void saveEnderChestContainer(String colorCode, UUID optionalPlayerUuid, ItemContainer itemContainer) {
        if (!this.canUseDatabase()) {
            return;
        }

        try {
            String inventoryJson = this.itemContainerToJson(itemContainer);
            this.dbJsonObject.saveEnderChestInventory(colorCode, optionalPlayerUuid, inventoryJson);
        } catch (Exception errCatch) {
            System.err.println("[EnderStorage] Error saving Ender_Chest data for " + colorCode + ": " + errCatch.getMessage());
        }
    }

    private ItemContainer itemContainerFromJson(String inventoryJson, String debugName) {
        SimpleItemContainer simpleItemContainer = new SimpleItemContainer(INVENTORY_SLOT_COUNT);

        if (inventoryJson == null || inventoryJson.isEmpty()) {
            return simpleItemContainer;
        }

        Map<Integer, SavedItem> savedItems = this.gson.fromJson(
                inventoryJson,
                new TypeToken<Map<Integer, SavedItem>>() {
                }.getType()
        );

        if (savedItems == null) {
            return simpleItemContainer;
        }

        for (Map.Entry<Integer, SavedItem> entry : savedItems.entrySet()) {
            int inventorySlot = entry.getKey();
            SavedItem savedItem = entry.getValue();

            if (savedItem == null || savedItem.id == null || savedItem.id.isEmpty()) {
                continue;
            }

            if (inventorySlot < 0 || inventorySlot >= INVENTORY_SLOT_COUNT) {
                continue;
            }

            try {
                BsonDocument metadata = null;

                if (savedItem.metadata != null && !savedItem.metadata.isEmpty()) {
                    try {
                        metadata = BsonDocument.parse(savedItem.metadata);
                    } catch (Exception metadataError) {
                        System.err.println("[EnderStorage] Failed to parse metadata for " + debugName + " slot " + inventorySlot);
                    }
                }

                ItemStack itemStack;

                if (metadata != null) {
                    itemStack = new ItemStack(savedItem.id, savedItem.amount).withMetadata(metadata);
                } else {
                    itemStack = new ItemStack(savedItem.id, savedItem.amount);
                }

                itemStack = itemStack.withDurability(savedItem.durability);
                simpleItemContainer.setItemStackForSlot((short) inventorySlot, itemStack);
            } catch (Exception ignored) {
            }
        }

        return simpleItemContainer;
    }

    private String itemContainerToJson(ItemContainer itemContainer) {
        Map<Integer, SavedItem> inventoryMap = new HashMap<>();

        short itemContainerCapacity = itemContainer.getCapacity();

        for (short slot = 0; slot < itemContainerCapacity; ++slot) {
            ItemStack itemStack = itemContainer.getItemStack(slot);

            if (itemStack == null || itemStack.isEmpty()) {
                continue;
            }

            String metadataJson = null;

            if (itemStack.getMetadata() != null) {
                metadataJson = BsonUtil.toJson(itemStack.getMetadata());
            }

            SavedItem savedItem = new SavedItem(
                    itemStack.getItemId(),
                    itemStack.getQuantity(),
                    metadataJson,
                    itemStack.getDurability()
            );

            inventoryMap.put((int) slot, savedItem);
        }

        return this.gson.toJson(inventoryMap);
    }

    private boolean canUseDatabase() {
        return this.dbJsonObject != null && this.dbJsonObject.isConnected();
    }

    private static class SavedItem {
        String id;
        int amount;
        String metadata;
        double durability;

        public SavedItem(String id, int amount, String metadata, double durability) {
            this.id = id;
            this.amount = amount;
            this.metadata = metadata;
            this.durability = durability;
        }
    }

    private static class EnderChestKey {
        private final String colorCode;
        private final UUID playerUuid;

        private EnderChestKey(String colorCode, UUID playerUuid) {
            this.colorCode = colorCode;
            this.playerUuid = playerUuid;
        }

        private static String toStorageKey(String colorCode, UUID playerUuid) {
            if (playerUuid == null) {
                return "public:" + colorCode;
            }

            return "player:" + playerUuid + ":" + colorCode;
        }

        private static EnderChestKey fromStorageKey(String storageKey) {
            if (storageKey.startsWith("player:")) {
                String remaining = storageKey.substring("player:".length());
                int uuidEndIndex = remaining.indexOf(':');

                if (uuidEndIndex > 0) {
                    UUID playerUuid = UUID.fromString(remaining.substring(0, uuidEndIndex));
                    String colorCode = remaining.substring(uuidEndIndex + 1);
                    return new EnderChestKey(colorCode, playerUuid);
                }
            }

            if (storageKey.startsWith("public:")) {
                return new EnderChestKey(storageKey.substring("public:".length()), null);
            }

            return new EnderChestKey(storageKey, null);
        }
    }
}