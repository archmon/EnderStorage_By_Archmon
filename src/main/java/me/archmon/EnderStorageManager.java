//This class was heavily influenced by the EnderChestManager class of the original EnderChest mod by 01Kvothe10

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

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.BsonDocument;

@SuppressWarnings("removal")
public class EnderStorageManager {

    private static final Path DATA_DIR = Paths.get("ender_storage_data"); //Is this used for anything?
    private final Map<UUID, ItemContainer> loadedContainers = new ConcurrentHashMap();
    private final Gson gson = (new GsonBuilder()).setPrettyPrinting().create();
    private final DatabaseHandler dbJsonObject;
    private final Map<Long, ItemContainer> positionContainers = new ConcurrentHashMap();
    private EnderStorageTickSystem tickSystem;

    public EnderStorageManager(JsonObject dbConfigFile) {

        try {
            if (!Files.exists(DATA_DIR, new LinkOption[0])){
                Files.createDirectories(DATA_DIR);
            }
        } catch (IOException folderNotFound1) {
            folderNotFound1.printStackTrace();
        }

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
        final ItemContainer finalItemContainer;//had to use lamba "->" to assign value because it should be final or effectively final warning
        final ItemContainer finalItemContainer2;

        if (this.loadedContainers.containsKey(uuid)){
            itemContainer = (ItemContainer)this.loadedContainers.get(uuid);
            short itemContainerCapacity = itemContainer.getCapacity();
            short numberOfSlotsInInventory =63;

            if (itemContainerCapacity != numberOfSlotsInInventory){//not sure if needed because size is not var
                this.saveContainer(uuid, itemContainer);
                this.loadedContainers.remove(uuid);
                itemContainer = this.loadContainer(uuid);
                this.loadedContainers.put(uuid, itemContainer);
                finalItemContainer=itemContainer;
                itemContainer.registerChangeEvent((var3) -> this.saveContainer(uuid, finalItemContainer));
            }
        } else {
            itemContainer = this.loadContainer(uuid);
            this.loadedContainers.put(uuid, itemContainer);
            finalItemContainer=itemContainer;
            itemContainer.registerChangeEvent((var3) -> this.saveContainer(uuid, finalItemContainer));
        }

        Object containerBlockWindow;
        if (blockType != null && itemContainer.getCapacity() == 63){//investigate if this can be simplified
            containerBlockWindow = new ContainerBlockWindow(posX, posY, posZ, rotationIndex, blockType, itemContainer);
        } else {
            containerBlockWindow = new ContainerWindow(itemContainer);
        }

        finalItemContainer2 = itemContainer;
        ((Window)containerBlockWindow).registerCloseEvent((var3x)-> this.saveContainer(uuid, finalItemContainer2));
        Ref playerReference = player.getReference();
        Store playerReferenceStore = playerReference.getStore();
        player.getPageManager().setPageWithWindows(playerReference, playerReferenceStore, Page.Bench, true, new Window[]{(Window)containerBlockWindow});
    }

    private ItemContainer loadContainer(UUID uuid){

        short numberOfSlotsShort = 63;

        SimpleItemContainer simpleItemContainer = new SimpleItemContainer((numberOfSlotsShort));

        try{
            String jsonStringInventory_Data = this.dbJsonObject.getInventory(uuid);
            if (jsonStringInventory_Data == null) {
                Path directoryPath = DATA_DIR.resolve(uuid.toString()+".json");
                if (Files.exists(directoryPath, new LinkOption[0])) {
                    jsonStringInventory_Data = Files.readString(directoryPath);
                    this.saveInventoryToDB(uuid, jsonStringInventory_Data);
                }
            }

            if (jsonStringInventory_Data != null) {
                Map<Integer, EnderStorageManager.SavedItem> inventoryLoadOutOfChest = (Map)this.gson.fromJson(jsonStringInventory_Data, (new TypeToken<Map<Integer, EnderStorageManager.SavedItem>>() {{
                        Objects.requireNonNull(EnderStorageManager.this);
                    }}).getType());

                if (inventoryLoadOutOfChest != null) {
                    for(Map.Entry databaseTable : inventoryLoadOutOfChest.entrySet()){
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
                        } catch (Exception err) {
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
            ConcurrentHashMap concurrentHashMap = new ConcurrentHashMap();
            short itemContainerCapacity = ((SimpleItemContainer)itemContainer).getCapacity();

            for (short i=0; i < itemContainerCapacity; ++i) {
                ItemStack itemStack = ((SimpleItemContainer)itemContainer).getItemStack(i);
                if (itemStack != null && !itemStack.isEmpty()) {
                    String nullString = null;
                    if (itemStack.getMetadata() != null) {//warning: getMetadata is deprecated.
                        nullString = BsonUtil.toJson(itemStack.getMetadata());
                    }

                    double itemStackDurability = itemStack.getDurability();
                    concurrentHashMap.put(Integer.valueOf(i), new SavedItem(itemStack.getItemId(), itemStack.getQuantity(), nullString, itemStackDurability));
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
        for (Map.Entry keyValue : this.loadedContainers.entrySet()) {
            this.saveContainer((UUID)keyValue.getKey(), (ItemContainer)keyValue.getValue());
        }

        this.dbJsonObject.close();
    }

    public void setTickSystem(EnderStorageTickSystem tick) {
        this.tickSystem = tick;
    }

    private static long positionKey(int blockPositionX, int blockPositionY, int blockPositionZ) {
        return (long)blockPositionX << 42 | (long)(blockPositionY & 1048575) << 22 | (long)(blockPositionZ & 4194303);
    }

    public boolean clearContainerAt(int blockPositionX, int blockPositionY, int blockPositionZ) {
        long positionKeyLong = positionKey(blockPositionX, blockPositionY, blockPositionZ);
        ItemContainer itemContainer = (ItemContainer) this.positionContainers.get(positionKeyLong);
        if (itemContainer == null){
            return false;
        } else {
            short itemContainerCapacity = itemContainer.getCapacity();

            for (int slot = 0; slot < itemContainerCapacity; ++slot) {
                itemContainer.setItemStackForSlot((short)slot, (ItemStack) null);
            }

            this.positionContainers.remove(positionKeyLong);
            return true;
        }
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
