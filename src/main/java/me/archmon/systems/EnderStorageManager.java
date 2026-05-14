//This class was heavily influenced by the EnderChestManager class of the original EnderChest mod by 01Kvothe10

package me.archmon.systems;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("removal")
public class EnderStorageManager {

    private static final Path DATA_DIR = Paths.get("ender_storage_data"); //Is this used for anything?
    private final Map<UUID, ItemContainer> loadedContainers = new ConcurrentHashMap();
    private final Gson gson = (new GsonBuilder()).setPrettyPrinting().create();
    private final DatabaseHandler dbJsonObject;
    private final Map<Long, ItemContainer> positionContainers = new ConcurrentHashMap();
    //private EnderChestTickSystem tickSystem;

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

            if (itemContainerCapacity != numberOfSlotsInInventory){//not sure if needed if size not var
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

        return null;
    }


    public void saveContainer(UUID uuid, ItemContainer itemContainer){

    }
}
