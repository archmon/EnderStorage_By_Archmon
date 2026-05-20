package me.archmon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.util.BsonUtil;

import java.io.PrintStream;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.BsonDocument;

@SuppressWarnings("removal")
public class EnderStorageManager {

    private static final UUID DEFAULT_ENDER_CHEST_CHANNEL_UUID = UUID.nameUUIDFromBytes("EnderStorage:Ender_Chest:default".getBytes());
    @SuppressWarnings({"rawtypes", "unchecked"}) private final Map<UUID, ItemContainer> loadedContainers = new ConcurrentHashMap();
    @SuppressWarnings({"rawtypes", "unchecked"}) private final Map<UUID, ItemContainer> loadedEnderChestContainers = new ConcurrentHashMap();
    private final Gson gson = (new GsonBuilder()).setPrettyPrinting().create();
    private final DatabaseHandler dbJsonObject;

    public EnderStorageManager(JsonObject dbConfigFile) {

        JsonObject databaseJsonObject = dbConfigFile.get("database").getAsJsonObject();
        this.dbJsonObject = new DatabaseHandler(databaseJsonObject);

        try {
            this.dbJsonObject.dbConnect();
        } catch (Exception databaseFailedConnect) {
            System.err.println("[EnderStorage] Failed to connect to database: "+ databaseFailedConnect.getMessage());
        }
    }

    public void openEnderStorage(Player player, int posX, int posY, int posZ, int rotationIndex, BlockType blockType) {
        this.openEnderStorage(player, player.getUuid(), posX, posY, posZ, rotationIndex, blockType);
    }

    public void openEnderStorage(Player player, UUID uuid, int posX, int posY, int posZ, int rotationIndex, BlockType blockType) {

        ItemContainer itemContainer;
        final ItemContainer finalItemContainer;
        final ItemContainer finalItemContainer2;

        if (this.loadedContainers.containsKey(uuid)){
            itemContainer = this.loadedContainers.get(uuid);
            short itemContainerCapacity = itemContainer.getCapacity();
            short numberOfSlotsInInventory =63;

            if (itemContainerCapacity != numberOfSlotsInInventory){
                this.saveContainer(uuid, itemContainer);
                this.loadedContainers.remove(uuid);
                itemContainer = this.loadContainer(uuid);
                this.loadedContainers.put(uuid, itemContainer);
                finalItemContainer=itemContainer;
                itemContainer.registerChangeEvent((_) -> this.saveContainer(uuid, finalItemContainer));
            }
        } else {
            itemContainer = this.loadContainer(uuid);
            this.loadedContainers.put(uuid, itemContainer);
            finalItemContainer=itemContainer;
            itemContainer.registerChangeEvent((_) -> this.saveContainer(uuid, finalItemContainer));
        }

        Object containerBlockWindow;
        if (blockType != null && itemContainer.getCapacity() == 63){
            containerBlockWindow = new ContainerBlockWindow(posX, posY, posZ, rotationIndex, blockType, itemContainer);
        } else {
            containerBlockWindow = new ContainerWindow(itemContainer);
        }

        finalItemContainer2 = itemContainer;
        ((Window)containerBlockWindow).registerCloseEvent((_)-> this.saveContainer(uuid, finalItemContainer2));
        @SuppressWarnings("rawtypes") Ref playerReference = player.getReference();
        assert playerReference != null;
        @SuppressWarnings("rawtypes") Store playerReferenceStore = playerReference.getStore();
        //noinspection unchecked
        player.getPageManager().setPageWithWindows(playerReference, playerReferenceStore, Page.Bench, true, new Window[]{(Window)containerBlockWindow});
    }

    public ItemContainer getSharedEnderChestContainer() {
        UUID channelUuid = DEFAULT_ENDER_CHEST_CHANNEL_UUID;

        if (this.loadedEnderChestContainers.containsKey(channelUuid)) {
            return this.loadedEnderChestContainers.get(channelUuid);
        }

        ItemContainer itemContainer = this.loadContainer(channelUuid);
        this.loadedEnderChestContainers.put(channelUuid, itemContainer);

        final ItemContainer finalItemContainer = itemContainer;
        itemContainer.registerChangeEvent((_) -> this.saveContainer(channelUuid, finalItemContainer));

        return itemContainer;
    }

    //Other mods can use this to access the shared ender chest inventory for automation purposes.
    public ItemContainer getEnderChestInventoryForAutomation() {
        return this.getSharedEnderChestContainer();
    }

    //This is used to save the shared ender chest inventory when the player closes the inventory window.
    public void saveEnderChestInventory() {
        this.saveContainer(DEFAULT_ENDER_CHEST_CHANNEL_UUID, this.getSharedEnderChestContainer());
    }

    //used in the EnderStoragePlugin to check if a block is an ender chest.
    public boolean isEnderChestBlock(BlockType blockType) {
        return blockType != null && blockType.getId() != null && blockType.getId().contains("Ender_Chest");
    }

    //used in the EnderStoragePlugin to check if a block is an ender safe.
    public boolean isPocket_DimensionSafeBlock(BlockType blockType) {
        return blockType != null && blockType.getId() != null && blockType.getId().contains("pocket_DimensionSafe");
    }

    public void openSharedEnderChest(Player player, int posX, int posY, int posZ, int rotationIndex, BlockType blockType) {
        UUID channelUuid = DEFAULT_ENDER_CHEST_CHANNEL_UUID;
        ItemContainer itemContainer = this.getSharedEnderChestContainer();

        Object containerBlockWindow;
        if (blockType != null && itemContainer.getCapacity() == 63) {
            containerBlockWindow = new ContainerBlockWindow(posX, posY, posZ, rotationIndex, blockType, itemContainer);
        } else {
            containerBlockWindow = new ContainerWindow(itemContainer);
        }

        final ItemContainer finalItemContainer = itemContainer;
        ((Window)containerBlockWindow).registerCloseEvent((_) -> this.saveContainer(channelUuid, finalItemContainer));

        @SuppressWarnings("rawtypes") Ref playerReference = player.getReference();
        assert playerReference != null;
        @SuppressWarnings("rawtypes") Store playerReferenceStore = playerReference.getStore();
        //noinspection unchecked
        player.getPageManager().setPageWithWindows(playerReference, playerReferenceStore, Page.Bench, true, new Window[]{(Window)containerBlockWindow});
    }

    private ItemContainer loadContainer(UUID uuid){

        short numberOfSlotsShort = 63;

        SimpleItemContainer simpleItemContainer = new SimpleItemContainer((numberOfSlotsShort));

        try{
            String jsonStringInventory_Data = this.dbJsonObject.getInventory(uuid);

            if (jsonStringInventory_Data != null) {
                Map<Integer, EnderStorageManager.SavedItem> inventoryLoadOutOfChest = (Map)this.gson.fromJson(jsonStringInventory_Data,
                        (new TypeToken<Map<Integer, EnderStorageManager.SavedItem>>() {{
                        Objects.requireNonNull(EnderStorageManager.this);
                    }}).getType());

                if (inventoryLoadOutOfChest != null) {
                    for(@SuppressWarnings("rawtypes") Map.Entry databaseTable : inventoryLoadOutOfChest.entrySet()){
                        int inventorySlotKeyNumber = (Integer)databaseTable.getKey();
                        SavedItem databaseSavedItem = (SavedItem)databaseTable.getValue();

                        try {
                            BsonDocument databaseMetadata = null;
                            if (databaseSavedItem.metadata != null && !databaseSavedItem.metadata.isEmpty()){
                                try {
                                    databaseMetadata = BsonDocument.parse(databaseSavedItem.metadata);
                                } catch (Exception ErrorMetadata) {
                                    PrintStream systemErrorVar = System.err;
                                    String uuidString = String.valueOf(uuid);
                                    systemErrorVar.println("[EnderStorage] Failed to parse metadata for " + uuidString + " slot " + inventorySlotKeyNumber);
                                }
                            }

                            ItemStack itemStack;
                            if (databaseMetadata != null) {
                                itemStack = (new ItemStack(databaseSavedItem.id, databaseSavedItem.amount)).withMetadata(databaseMetadata);
                            } else {
                                itemStack = new ItemStack(databaseSavedItem.id, databaseSavedItem.amount);
                            }

                            itemStack = itemStack.withDurability(databaseSavedItem.durability);
                            simpleItemContainer.setItemStackForSlot((short) inventorySlotKeyNumber, itemStack);
                        } catch (Exception _) {
                        }
                    }
                }
            }
        } catch (Exception errCatch) {
            PrintStream errorMessage = System.err;
            String errorMessage1 = String.valueOf(uuid);
            errorMessage.println("[EnderStorage] Error loading data for " + errorMessage1 + ": " + errCatch.getMessage());
        }

        return simpleItemContainer;
    }


    public void saveContainer(UUID uuid, ItemContainer itemContainer){

        if (this.dbJsonObject != null &&  this.dbJsonObject.isConnected()) {
            @SuppressWarnings("rawtypes") ConcurrentHashMap concurrentHashMap = new ConcurrentHashMap();
            short itemContainerCapacity = itemContainer.getCapacity();

            for (short i=0; i < itemContainerCapacity; ++i) {
                ItemStack itemStack = itemContainer.getItemStack(i);
                if (itemStack != null && !itemStack.isEmpty()) {
                    String nullString = null;
                    if (itemStack.getMetadata() != null) {//warning: getMetadata is deprecated.
                        nullString = BsonUtil.toJson(itemStack.getMetadata());
                    }

                    double itemStackDurability = itemStack.getDurability();
                    //noinspection unchecked
                    concurrentHashMap.put((int) i, new SavedItem(itemStack.getItemId(), itemStack.getQuantity(), nullString, itemStackDurability));
                }
            }

            try {
                String jsonString = this.gson.toJson(concurrentHashMap);
                this.saveInventoryToDB(uuid, jsonString);
            } catch (Exception errCatch) {
                PrintStream errorMessage = System.err;
                String errorMessage1 = String.valueOf(uuid);
                errorMessage.println("[EnderStorage] Error saving data for " + errorMessage1 + ": " + errCatch.getMessage());
            }
        }

    }

    public void saveInventoryToDB(UUID uuid, String itemContainer) throws Exception{
        this.dbJsonObject.saveInventory(uuid, itemContainer);
    }

    public void saveAll() {
        for (@SuppressWarnings("rawtypes") Map.Entry keyValue : this.loadedContainers.entrySet()) {
            this.saveContainer((UUID)keyValue.getKey(), (ItemContainer)keyValue.getValue());
        }

        for (@SuppressWarnings("rawtypes") Map.Entry keyValue : this.loadedEnderChestContainers.entrySet()) {
            this.saveContainer((UUID)keyValue.getKey(), (ItemContainer)keyValue.getValue());
        }

        this.dbJsonObject.close();
    }

    public void setTickSystem(EnderStorageTickSystem tick) {
        //used for the removing recipes
    }


    private static class SavedItem {
        String id;
        int amount;
        String metadata;
        double durability;

        public SavedItem(String id, int amount, String metadata, double durability){
            this.id = id;
            this.amount=amount;
            this.metadata=metadata;
            this.durability=durability;
        }
    }
}
