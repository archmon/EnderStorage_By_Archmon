package me.archmon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
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
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.util.BsonUtil;
import org.bson.BsonDocument;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@SuppressWarnings("removal")
class EnderStorageManager implements EnderStorageApi {

    private static final short INVENTORY_SLOT_COUNT = 54;
    private static final short ENDER_CHEST_LOCK_SLOT_COUNT = 1;
    private static final String DEFAULT_ENDER_CHEST_COLOR_CODE = "0:0:0";
    private static final String ENDER_CHEST_ITEM_ID = "Ender_Chest";
    private static final String ENDER_WRENCH_ITEM_ID = "EnderWrench";
    private static final String ADAMANTITE_INGOT_ITEM_ID = "Ingredient_Bar_Adamantite";
    private static final String ENDER_CHEST_PUBLIC_VISUAL_STATE = "PublicNetwork";
    private static final String ENDER_CHEST_PRIVATE_VISUAL_STATE = "PrivateNetwork";

    private final Map<String, ItemContainer> loadedPocketDimensionSafeContainers = new ConcurrentHashMap<>();
    private final Map<String, ItemContainer> loadedEnderChestContainers = new ConcurrentHashMap<>();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final DatabaseHandler dbJsonObject;

    EnderStorageManager(JsonObject dbConfigFile) {
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

    boolean isEnderChestBlock(BlockType blockType) {
        return blockType != null
                && blockType.getId() != null
                && blockType.getId().contains("Ender_Chest");
    }

    boolean isPocket_DimensionSafeBlock(BlockType blockType) {
        return blockType != null
                && blockType.getId() != null
                && blockType.getId().contains("pocket_DimensionSafe");
    }

    boolean isEnderWrenchItem(ItemStack itemStack) {
        return itemStack != null
                && !itemStack.isEmpty()
                && ENDER_WRENCH_ITEM_ID.equals(itemStack.getItemId());
    }

    ItemStack createEnderChestItemStack() {
        return new ItemStack(ENDER_CHEST_ITEM_ID, 1);
    }

    private void sendPlayerMessage(Player player, String message) {
        if (player != null) {
            player.sendMessage(Message.raw(message));
        }
    }

    void openPocketDimensionSafe(Player player, int posX, int posY, int posZ, int rotationIndex, BlockType blockType) {
        if (player == null) {
            return;
        }

        String locationKey = this.createPocketDimensionSafeLocationKey(posX, posY, posZ);

        if (!this.canAccessPocketDimensionSafe(player, locationKey)) {
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

    void openSharedEnderChest(Player player, int posX, int posY, int posZ, int rotationIndex, BlockType blockType) {
        if (player == null) {
            return;
        }

        EnderChestBlockConfig blockConfig = this.getOrCreateEnderChestBlockConfig(posX, posY, posZ);
        ItemContainer itemContainer = this.getEnderChestContainer(blockConfig.colorCode, blockConfig.ownerUuid);

        this.openContainerForPlayer(
                player,
                itemContainer,
                posX,
                posY,
                posZ,
                rotationIndex,
                blockType,
                true,
                () -> {
                    this.saveEnderChestContainer(blockConfig.colorCode, blockConfig.ownerUuid, itemContainer);
                    this.syncEnderChestVisualState(player, posX, posY, posZ, blockConfig);
                }
        );
    }

    void openEnderChestWrenchWindow(Player player, int posX, int posY, int posZ) {
        if (player == null) {
            return;
        }

        EnderChestBlockConfig blockConfig = this.getOrCreateEnderChestBlockConfig(posX, posY, posZ);
        SimpleItemContainer lockContainer = new SimpleItemContainer(ENDER_CHEST_LOCK_SLOT_COUNT);
        lockContainer.setSlotFilter(
                FilterActionType.ADD,
                (short) 0,
                (actionType, itemContainer, slot, itemStack) -> this.isAdamantiteIngot(itemStack)
                        && (itemContainer.getItemStack(slot) == null || itemContainer.getItemStack(slot).isEmpty())
        );

        if (blockConfig.ownerUuid != null) {
            lockContainer.setItemStackForSlot((short) 0, new ItemStack(ADAMANTITE_INGOT_ITEM_ID, 1));
        }

        String ownerDescription = this.describeEnderChestOwner(player, blockConfig);

        this.openEnderChestWrenchPage(
                player,
                lockContainer,
                blockConfig,
                ownerDescription,
                selectedColorCode -> this.applyEnderChestLockWindow(
                        player,
                        posX,
                        posY,
                        posZ,
                        blockConfig,
                        lockContainer,
                        selectedColorCode
                )
        );
    }

    private ItemContainer getSharedEnderChestContainer() {
        return this.getEnderChestContainer(DEFAULT_ENDER_CHEST_COLOR_CODE, null);
    }

    //this is to be used for other mods to hook into the enderchest inventory
    @Override
    public ItemContainer getEnderChestInventoryForAutomation() {
        return this.getSharedEnderChestContainer();
    }

    //this is to be used for other mods to hook into the enderchest inventory
    @Override
    public void saveEnderChestInventory() {
        ItemContainer itemContainer = this.getSharedEnderChestContainer();
        this.saveEnderChestContainer(DEFAULT_ENDER_CHEST_COLOR_CODE, null, itemContainer);
    }

    ItemStack getEnderChestBlockLockItem(int posX, int posY, int posZ) {
        EnderChestBlockConfig blockConfig = this.getOrCreateEnderChestBlockConfig(posX, posY, posZ);

        if (blockConfig.ownerUuid == null) {
            return null;
        }

        return new ItemStack(ADAMANTITE_INGOT_ITEM_ID, 1);
    }

    ItemStack createPocketDimensionSafeItemStack() {
        return new ItemStack("pocket_DimensionSafe", 1);
    }

    private boolean isAdamantiteIngot(ItemStack itemStack) {
        return itemStack != null
                && !itemStack.isEmpty()
                && ADAMANTITE_INGOT_ITEM_ID.equals(itemStack.getItemId());
    }

    void deleteEnderChestBlockConfig(int posX, int posY, int posZ) {
        String locationKey = this.createEnderChestLocationKey(posX, posY, posZ);

        try {
            this.dbJsonObject.deleteEnderChestBlockConfig(locationKey);
        } catch (Exception errCatch) {
            System.err.println("[EnderStorage] Failed to delete Ender_Chest config at " + locationKey + ": " + errCatch.getMessage());
        }
    }

    //not sure when this was added, but it's to be used for other mods to hook into the pocket dimension safe inventory I think
    ItemContainer removePocketDimensionSafeAndReturnContents(int posX, int posY, int posZ) {
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

    boolean canDestroyPocketDimensionSafe(Player player, int posX, int posY, int posZ) {
        if (player == null) {
            return false;
        }

        String locationKey = this.createPocketDimensionSafeLocationKey(posX, posY, posZ);
        return this.canAccessPocketDimensionSafe(player, locationKey);
    }

    void saveAll() {
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

    void setTickSystem(EnderStorageTickSystem tick) {
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

    private void openEnderChestWrenchPage(
            Player player,
            ItemContainer lockContainer,
            EnderChestBlockConfig blockConfig,
            String ownerDescription,
            Consumer<String> closeAction
    ) {
        @SuppressWarnings("rawtypes") Ref playerReference = player.getReference();

        if (playerReference == null || player.getPlayerRef() == null) {
            return;
        }

        @SuppressWarnings("rawtypes") Store playerReferenceStore = playerReference.getStore();
        EnderChestWrenchPage wrenchPage = new EnderChestWrenchPage(
                player.getPlayerRef(),
                blockConfig.colorCode,
                ownerDescription,
                () -> this.hasAdamantiteInLockSlot(lockContainer),
                () -> this.insertAdamantiteIntoLockSlot(player, lockContainer),
                () -> this.removeAdamantiteFromLockSlot(player, lockContainer)
        );
        ContainerWindow lockWindow = new ContainerWindow(lockContainer);
        lockWindow.registerCloseEvent((_) -> closeAction.accept(wrenchPage.getSelectedColorCode()));

        //noinspection unchecked
        player.getPageManager().openCustomPageWithWindows(
                playerReference,
                playerReferenceStore,
                wrenchPage,
                lockWindow
        );
    }

    private String describeEnderChestOwner(Player player, EnderChestBlockConfig blockConfig) {
        if (blockConfig == null || blockConfig.ownerUuid == null) {
            return "Public network";
        }

        if (blockConfig.ownerName != null && !blockConfig.ownerName.isBlank()) {
            return blockConfig.ownerName;
        }

        if (player != null && blockConfig.ownerUuid.equals(player.getUuid())) {
            return this.getPlayerDisplayName(player);
        }

        return "Unknown owner";
    }

    private boolean hasAdamantiteInLockSlot(ItemContainer lockContainer) {
        ItemStack lockItem = lockContainer.getItemStack((short) 0);
        return this.isAdamantiteIngot(lockItem);
    }

    private void insertAdamantiteIntoLockSlot(Player player, ItemContainer lockContainer) {
        ItemStack lockItem = lockContainer.getItemStack((short) 0);

        if (lockItem != null && !lockItem.isEmpty()) {
            this.sendPlayerMessage(player, "The Ender_Chest lock slot is already occupied.");
            return;
        }

        ItemStack adamantiteIngot = new ItemStack(ADAMANTITE_INGOT_ITEM_ID, 1);

        if (player.getInventory() == null
                || !player.getInventory().getCombinedHotbarFirst().canRemoveItemStack(adamantiteIngot, true, true)) {
            this.sendPlayerMessage(player, "You do not have an adamantite ingot to insert.");
            return;
        }

        player.getInventory().getCombinedHotbarFirst().removeItemStack(adamantiteIngot, true, true);
        lockContainer.setItemStackForSlot((short) 0, adamantiteIngot);
        this.sendPlayerMessage(player, "Inserted one adamantite ingot into the Ender_Chest lock slot.");
    }

    private void removeAdamantiteFromLockSlot(Player player, ItemContainer lockContainer) {
        ItemStack lockItem = lockContainer.getItemStack((short) 0);

        if (lockItem == null || lockItem.isEmpty()) {
            this.sendPlayerMessage(player, "The Ender_Chest lock slot is empty.");
            return;
        }

        lockContainer.removeItemStackFromSlot((short) 0);
        this.returnItemToPlayer(player, lockItem);
        this.sendPlayerMessage(player, "Removed the adamantite ingot from the Ender_Chest lock slot.");
    }

    private String createPocketDimensionSafeLocationKey(int posX, int posY, int posZ) {
        return posX + ":" + posY + ":" + posZ;
    }

    private String createEnderChestLocationKey(int posX, int posY, int posZ) {
        return posX + ":" + posY + ":" + posZ;
    }

    private EnderChestBlockConfig getOrCreateEnderChestBlockConfig(int posX, int posY, int posZ) {
        String locationKey = this.createEnderChestLocationKey(posX, posY, posZ);

        try {
            DatabaseHandler.EnderChestBlockConfig savedConfig = this.dbJsonObject.getEnderChestBlockConfig(locationKey);

            if (savedConfig != null) {
                return new EnderChestBlockConfig(
                        this.normalizeColorCode(savedConfig.colorCode),
                        savedConfig.ownerUuid,
                        savedConfig.ownerName
                );
            }

            this.dbJsonObject.saveEnderChestBlockConfig(locationKey, DEFAULT_ENDER_CHEST_COLOR_CODE, null, null);
        } catch (Exception errCatch) {
            System.err.println("[EnderStorage] Failed to load Ender_Chest config for " + locationKey + ": " + errCatch.getMessage());
        }

        return new EnderChestBlockConfig(DEFAULT_ENDER_CHEST_COLOR_CODE, null, null);
    }

    private void saveEnderChestBlockConfig(int posX, int posY, int posZ, EnderChestBlockConfig blockConfig) {
        if (!this.canUseDatabase()) {
            return;
        }

        String locationKey = this.createEnderChestLocationKey(posX, posY, posZ);

        try {
            this.dbJsonObject.saveEnderChestBlockConfig(
                    locationKey,
                    this.normalizeColorCode(blockConfig.colorCode),
                    blockConfig.ownerUuid,
                    blockConfig.ownerName
            );
        } catch (Exception errCatch) {
            System.err.println("[EnderStorage] Failed to save Ender_Chest config for " + locationKey + ": " + errCatch.getMessage());
        }
    }

    private void applyEnderChestLockWindow(
            Player player,
            int posX,
            int posY,
            int posZ,
            EnderChestBlockConfig previousConfig,
            ItemContainer lockContainer,
            String selectedColorCode
    ) {
        ItemStack lockItem = lockContainer.getItemStack((short) 0);
        String colorCode = this.normalizeColorCode(selectedColorCode);

        if (lockItem == null || lockItem.isEmpty()) {
            EnderChestBlockConfig newConfig = new EnderChestBlockConfig(colorCode, null, null);
            this.saveEnderChestBlockConfig(posX, posY, posZ, newConfig);
            this.syncEnderChestVisualState(player, posX, posY, posZ, newConfig);
            this.sendPlayerMessage(player, "Ender_Chest set to public network " + colorCode + ".");
            return;
        }

        if (!ADAMANTITE_INGOT_ITEM_ID.equals(lockItem.getItemId())) {
            this.returnItemToPlayer(player, lockItem);
            EnderChestBlockConfig newConfig = new EnderChestBlockConfig(colorCode, null, null);
            this.saveEnderChestBlockConfig(posX, posY, posZ, newConfig);
            this.syncEnderChestVisualState(player, posX, posY, posZ, newConfig);
            this.sendPlayerMessage(player, "Only an adamantite ingot can lock an Ender_Chest. Invalid item returned.");
            return;
        }

        if (lockItem.getQuantity() > 1) {
            this.returnItemToPlayer(player, lockItem.withQuantity(lockItem.getQuantity() - 1));
        }

        UUID ownerUuid = previousConfig.ownerUuid == null ? player.getUuid() : previousConfig.ownerUuid;
        String ownerName = this.resolveOwnerName(player, previousConfig, ownerUuid);
        EnderChestBlockConfig newConfig = new EnderChestBlockConfig(colorCode, ownerUuid, ownerName);
        this.saveEnderChestBlockConfig(posX, posY, posZ, newConfig);
        this.syncEnderChestVisualState(player, posX, posY, posZ, newConfig);
        this.sendPlayerMessage(player, "Ender_Chest set to private network " + colorCode + " owned by " + ownerName + ".");
    }

    private void syncEnderChestVisualState(
            Player player,
            int posX,
            int posY,
            int posZ,
            EnderChestBlockConfig blockConfig
    ) {
        if (player == null || player.getWorld() == null || blockConfig == null) {
            return;
        }

        try {
            BlockType currentBlockType = player.getWorld().getBlockType(posX, posY, posZ);

            if (!this.isEnderChestBlock(currentBlockType)) {
                return;
            }

            String visualState = blockConfig.ownerUuid == null
                    ? ENDER_CHEST_PUBLIC_VISUAL_STATE
                    : ENDER_CHEST_PRIVATE_VISUAL_STATE;

            player.getWorld().setBlockInteractionState(
                    new Vector3i(posX, posY, posZ),
                    currentBlockType,
                    visualState
            );
        } catch (Exception errCatch) {
            System.err.println("[EnderStorage] Failed to sync Ender_Chest visual state at "
                    + posX + ":" + posY + ":" + posZ + ": " + errCatch.getMessage());
        }
    }

    private String resolveOwnerName(Player player, EnderChestBlockConfig previousConfig, UUID ownerUuid) {
        if (previousConfig.ownerName != null && !previousConfig.ownerName.isBlank()) {
            return previousConfig.ownerName;
        }

        if (player != null && ownerUuid != null && ownerUuid.equals(player.getUuid())) {
            return this.getPlayerDisplayName(player);
        }

        return "Unknown owner";
    }

    private String getPlayerDisplayName(Player player) {
        String displayName = player.getDisplayName();

        if (displayName == null || displayName.isBlank()) {
            return "Unknown owner";
        }

        return displayName;
    }

    private void returnItemToPlayer(Player player, ItemStack itemStack) {
        if (player == null || itemStack == null || itemStack.isEmpty()) {
            return;
        }

        @SuppressWarnings("rawtypes") Ref playerReference = player.getReference();

        if (playerReference == null) {
            return;
        }

        @SuppressWarnings("rawtypes") Store playerReferenceStore = playerReference.getStore();
        //noinspection unchecked
        player.giveItem(itemStack, playerReference, playerReferenceStore);
    }

    private String normalizeColorCode(String colorCode) {
        if (colorCode == null || colorCode.isBlank()) {
            return DEFAULT_ENDER_CHEST_COLOR_CODE;
        }

        return colorCode;
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

            if (this.isServerOperator(player)) {
                return true;
            }

            this.sendPlayerMessage(player, "You do not have permission to access this pocket dimension safe.");
            return false;
        } catch (Exception errCatch) {
            System.err.println("[EnderStorage] Failed to check pocket_DimensionSafe owner for " + locationKey + ": " + errCatch.getMessage());
            this.sendPlayerMessage(player, "Unable to verify pocket dimension safe ownership.");
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

    void registerPlacedPocketDimensionSafe(Player player, int posX, int posY, int posZ) {
        if (player == null || !this.canUseDatabase()) {
            return;
        }

        String locationKey = this.createPocketDimensionSafeLocationKey(posX, posY, posZ);

        try {
            UUID existingOwnerUuid = this.dbJsonObject.getPocketDimensionSafeOwner(locationKey);

            if (existingOwnerUuid == null) {
                this.dbJsonObject.savePocketDimensionSafeInventory(locationKey, player.getUuid(), "{}");
            }
        } catch (Exception errCatch) {
            System.err.println("[EnderStorage] Failed to register placed pocket_DimensionSafe at " + locationKey + ": " + errCatch.getMessage());
        }
    }

    boolean canModifyPocketDimensionSafe(Player player, int posX, int posY, int posZ) {
        if (player == null) {
            return false;
        }

        String locationKey = this.createPocketDimensionSafeLocationKey(posX, posY, posZ);

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
            System.err.println("[EnderStorage] Failed to check pocket_DimensionSafe modification permission for " + locationKey + ": " + errCatch.getMessage());
            return false;
        }
    }

    UUID getPocketDimensionSafeOwner(int posX, int posY, int posZ) {
        String locationKey = this.createPocketDimensionSafeLocationKey(posX, posY, posZ);

        try {
            return this.dbJsonObject.getPocketDimensionSafeOwner(locationKey);
        } catch (Exception errCatch) {
            System.err.println("[EnderStorage] Failed to get pocket_DimensionSafe owner for " + locationKey + ": " + errCatch.getMessage());
            return null;
        }
    }

    private boolean isServerOperator(Player player) {
        return player != null
                && (
                player.hasPermission("enderstorage.admin")
                        || player.hasPermission("enderstorage.safe.bypass")
        );
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

    private void savePocketDimensionSafeContainer(String locationKey, UUID ownerUuid, ItemContainer itemContainer) {
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

    private static class EnderChestBlockConfig {
        private final String colorCode;
        private final UUID ownerUuid;
        private final String ownerName;

        private EnderChestBlockConfig(String colorCode, UUID ownerUuid, String ownerName) {
            this.colorCode = colorCode;
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName;
        }
    }

    boolean isPocketDimensionSafeEmpty(int posX, int posY, int posZ) {
        String locationKey = this.createPocketDimensionSafeLocationKey(posX, posY, posZ);
        ItemContainer itemContainer = this.loadPocketDimensionSafeContainer(locationKey);

        short capacity = itemContainer.getCapacity();

        for (short slot = 0; slot < capacity; slot++) {
            ItemStack itemStack = itemContainer.getItemStack(slot);

            if (itemStack != null && !itemStack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    ItemContainer getPocketDimensionSafeContents(int posX, int posY, int posZ) {
        String locationKey = this.createPocketDimensionSafeLocationKey(posX, posY, posZ);
        return this.loadPocketDimensionSafeContainer(locationKey);
    }

    void deletePocketDimensionSafeData(int posX, int posY, int posZ) {
        String locationKey = this.createPocketDimensionSafeLocationKey(posX, posY, posZ);

        try {
            this.dbJsonObject.deletePocketDimensionSafe(locationKey);
            this.loadedPocketDimensionSafeContainers.remove(locationKey);
        } catch (Exception errCatch) {
            System.err.println("[EnderStorage] Failed to delete pocket_DimensionSafe at " + locationKey + ": " + errCatch.getMessage());
        }
    }
}
