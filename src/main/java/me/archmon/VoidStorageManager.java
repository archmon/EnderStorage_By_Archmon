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
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.BsonUtil;
import org.bson.BsonDocument;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;


class VoidStorageManager implements VoidStorageApi {

    private static final short INVENTORY_SLOT_COUNT = 54;
    private static final short VOID_CHEST_LOCK_SLOT_COUNT = 1;
    private static final String DEFAULT_VOID_CHEST_COLOR_CODE = "0:0:0";
    private static final String VOID_CHEST_ITEM_ID = "VoidChest";
    private static final String VOID_WRENCH_ITEM_ID = "VoidWrench";
    private static final String ADAMANTITE_INGOT_ITEM_ID = "Ingredient_Bar_Adamantite";
    private static final String VOID_CHEST_PUBLIC_VISUAL_STATE = "PublicNetwork";
    private static final String VOID_CHEST_PRIVATE_VISUAL_STATE = "PrivateNetwork";

    private final Map<String, ItemContainer> loadedPocketDimensionSafeContainers = new ConcurrentHashMap<>();
    private final Map<String, ItemContainer> loadedVoidChestContainers = new ConcurrentHashMap<>();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final DatabaseHandler dbJsonObject;

    VoidStorageManager(JsonObject dbConfigFile) {
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
            System.err.println("[VoidStorage] Failed to connect to database: " + databaseFailedConnect.getMessage());
        }
    }

    boolean isVoidChestBlock(BlockType blockType) {
        return blockType != null
                && blockType.getId() != null
                && blockType.getId().contains("VoidChest");
    }

    boolean isPocket_DimensionSafeBlock(BlockType blockType) {
        return blockType != null
                && blockType.getId() != null
                && blockType.getId().contains("pocket_DimensionSafe");
    }

    boolean isVoidWrenchItem(ItemStack itemStack) {
        return itemStack != null
                && !itemStack.isEmpty()
                && VOID_WRENCH_ITEM_ID.equals(itemStack.getItemId());
    }

    ItemStack createVoidChestItemStack() {
        return new ItemStack(VOID_CHEST_ITEM_ID, 1);
    }

    private void sendPlayerMessage(Player player, String message) {
        if (player != null) {
            player.sendMessage(Message.raw(message));
        }
    }

    private PlayerRef getPlayerRef(Player player) {
        if (player == null) {
            return null;
        }

        Ref<EntityStore> playerReference = player.getReference();

        if (playerReference == null) {
            return null;
        }

        return playerReference.getStore().getComponent(playerReference, PlayerRef.getComponentType());
    }

    private UUID getPlayerUuid(Player player) {
        PlayerRef playerRef = this.getPlayerRef(player);
        return playerRef == null ? null : playerRef.getUuid();
    }

    private ItemContainer getPlayerHotbarFirstInventory(Player player) {
        if (player == null) {
            return null;
        }

        Ref<EntityStore> playerReference = player.getReference();

        if (playerReference == null || !playerReference.isValid()) {
            return null;
        }

        return InventoryComponent.getCombined(
                playerReference.getStore(),
                playerReference,
                InventoryComponent.HOTBAR_FIRST
        );
    }

    void openPocketDimensionSafe(Player player, int posX, int posY, int posZ, int rotationIndex, BlockType blockType) {
        if (player == null) {
            return;
        }

        String locationKey = this.createPocketDimensionSafeLocationKey(posX, posY, posZ);

        if (!this.canAccessPocketDimensionSafe(player, locationKey)) {
            return;
        }

        UUID ownerUuid = this.getOrCreatePocketDimensionSafeOwner(locationKey, this.getPlayerUuid(player));

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

    void openSharedVoidChest(Player player, int posX, int posY, int posZ, int rotationIndex, BlockType blockType) {
        if (player == null) {
            return;
        }

        VoidChestBlockConfig blockConfig = this.getOrCreateVoidChestBlockConfig(posX, posY, posZ);
        ItemContainer itemContainer = this.getVoidChestContainer(blockConfig.colorCode, blockConfig.ownerUuid);

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
                    this.saveVoidChestContainer(blockConfig.colorCode, blockConfig.ownerUuid, itemContainer);
                    this.syncVoidChestVisualState(player, posX, posY, posZ, blockConfig);
                }
        );
    }

    void openVoidChestWrenchWindow(Player player, int posX, int posY, int posZ) {
        if (player == null) {
            return;
        }

        VoidChestBlockConfig blockConfig = this.getOrCreateVoidChestBlockConfig(posX, posY, posZ);
        SimpleItemContainer lockContainer = new SimpleItemContainer(VOID_CHEST_LOCK_SLOT_COUNT);
        lockContainer.setSlotFilter(
                FilterActionType.ADD,
                (short) 0,
                (actionType, itemContainer, slot, itemStack) -> {
                    ItemStack currentItemStack = itemContainer.getItemStack(slot);
                    return this.isAdamantiteIngot(itemStack)
                            && (currentItemStack == null || currentItemStack.isEmpty());
                }
        );

        if (blockConfig.ownerUuid != null) {
            lockContainer.setItemStackForSlot((short) 0, new ItemStack(ADAMANTITE_INGOT_ITEM_ID, 1));
        }

        String ownerDescription = this.describeVoidChestOwner(player, blockConfig);

        this.openVoidChestWrenchPage(
                player,
                lockContainer,
                blockConfig,
                ownerDescription,
                selectedColorCode -> this.applyVoidChestLockWindow(
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

    private ItemContainer getSharedVoidChestContainer() {
        return this.getVoidChestContainer(DEFAULT_VOID_CHEST_COLOR_CODE, null);
    }

    //this is to be used for other mods to hook into the voidchest inventory
    @Override
    public ItemContainer getVoidChestInventoryForAutomation() {
        return this.getSharedVoidChestContainer();
    }

    //this is to be used for other mods to hook into the Voidchest inventory
    @Override
    public void saveVoidChestInventory() {
        ItemContainer itemContainer = this.getSharedVoidChestContainer();
        this.saveVoidChestContainer(DEFAULT_VOID_CHEST_COLOR_CODE, null, itemContainer);
    }

    ItemStack getVoidChestBlockLockItem(int posX, int posY, int posZ) {
        VoidChestBlockConfig blockConfig = this.getOrCreateVoidChestBlockConfig(posX, posY, posZ);

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

    void deleteVoidChestBlockConfig(int posX, int posY, int posZ) {
        String locationKey = this.createVoidChestLocationKey(posX, posY, posZ);

        try {
            this.dbJsonObject.deleteVoidChestBlockConfig(locationKey);
        } catch (Exception errCatch) {
            System.err.println("[VoidStorage] Failed to delete VoidChest config at " + locationKey + ": " + errCatch.getMessage());
        }
    }

    /*ItemContainer removePocketDimensionSafeAndReturnContents(int posX, int posY, int posZ) {
        String locationKey = this.createPocketDimensionSafeLocationKey(posX, posY, posZ);
        ItemContainer itemContainer = this.loadPocketDimensionSafeContainer(locationKey);

        try {
            this.dbJsonObject.deletePocketDimensionSafe(locationKey);
            this.loadedPocketDimensionSafeContainers.remove(locationKey);
        } catch (Exception errCatch) {
            System.err.println("[VoidStorage] Failed to delete pocket_DimensionSafe at " + locationKey + ": " + errCatch.getMessage());
        }

        return itemContainer;
    }

    boolean canDestroyPocketDimensionSafe(Player player, int posX, int posY, int posZ) {
        if (player == null) {
            return false;
        }

        String locationKey = this.createPocketDimensionSafeLocationKey(posX, posY, posZ);
        return this.canAccessPocketDimensionSafe(player, locationKey);
    }*/

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
                System.err.println("[VoidStorage] Failed to save pocket_DimensionSafe at " + locationKey + ": " + errCatch.getMessage());
            }
        }

        for (Map.Entry<String, ItemContainer> keyValue : this.loadedVoidChestContainers.entrySet()) {
            String voidChestStorageKey = keyValue.getKey();
            VoidChestKey parsedKey = VoidChestKey.fromStorageKey(voidChestStorageKey);
            ItemContainer itemContainer = keyValue.getValue();

            this.saveVoidChestContainer(parsedKey.colorCode, parsedKey.playerUuid, itemContainer);
        }

        this.dbJsonObject.close();
    }

    void setTickSystem(VoidStorageTickSystem tick) {
        // Kept for compatibility with VoidStoragePlugin.
        // Recipe-removal timing is handled through VoidStorageTickSystem.
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

    private ItemContainer getVoidChestContainer(String colorCode, UUID optionalPlayerUuid) {
        String voidChestStorageKey = VoidChestKey.toStorageKey(colorCode, optionalPlayerUuid);
        ItemContainer itemContainer = this.loadedVoidChestContainers.get(voidChestStorageKey);

        if (itemContainer != null && itemContainer.getCapacity() == INVENTORY_SLOT_COUNT) {
            return itemContainer;
        }

        if (itemContainer != null) {
            this.saveVoidChestContainer(colorCode, optionalPlayerUuid, itemContainer);
            this.loadedVoidChestContainers.remove(voidChestStorageKey);
        }

        itemContainer = this.loadVoidChestContainer(colorCode, optionalPlayerUuid);
        this.loadedVoidChestContainers.put(voidChestStorageKey, itemContainer);

        //removed cause duplication glitch
        //final ItemContainer finalItemContainer = itemContainer;
        //itemContainer.registerChangeEvent((_) -> this.saveVoidChestContainer(colorCode, playerUuid, finalItemContainer));

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

    private void openVoidChestWrenchPage(
            Player player,
            ItemContainer lockContainer,
            VoidChestBlockConfig blockConfig,
            String ownerDescription,
            Consumer<String> closeAction
    ) {
        Ref<EntityStore> playerReference = player.getReference();

        if (playerReference == null) {
            return;
        }

        PlayerRef playerRef = this.getPlayerRef(player);

        if (playerRef == null) {
            return;
        }

        Store<EntityStore> playerReferenceStore = playerReference.getStore();
        VoidChestWrenchPage wrenchPage = new VoidChestWrenchPage(
                playerRef,
                blockConfig.colorCode,
                ownerDescription,
                () -> this.hasAdamantiteInLockSlot(lockContainer),
                () -> this.insertAdamantiteIntoLockSlot(player, lockContainer),
                () -> this.removeAdamantiteFromLockSlot(player, lockContainer)
        );
        ContainerWindow lockWindow = new ContainerWindow(lockContainer);
        lockWindow.registerCloseEvent((_) -> closeAction.accept(wrenchPage.getSelectedColorCode()));

        player.getPageManager().openCustomPageWithWindows(
                playerReference,
                playerReferenceStore,
                wrenchPage,
                lockWindow
        );
    }

    private String describeVoidChestOwner(Player player, VoidChestBlockConfig blockConfig) {
        if (blockConfig == null || blockConfig.ownerUuid == null) {
            return "Public network";
        }

        if (blockConfig.ownerName != null && !blockConfig.ownerName.isBlank()) {
            return blockConfig.ownerName;
        }

        if (player != null && blockConfig.ownerUuid.equals(this.getPlayerUuid(player))) {
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
            this.sendPlayerMessage(player, "The VoidChest lock slot is already occupied.");
            return;
        }

        ItemStack adamantiteIngot = new ItemStack(ADAMANTITE_INGOT_ITEM_ID, 1);

        ItemContainer playerInventory = this.getPlayerHotbarFirstInventory(player);

        if (playerInventory == null || !playerInventory.canRemoveItemStack(adamantiteIngot, true, true)) {
            this.sendPlayerMessage(player, "You do not have an adamantite ingot to insert.");
            return;
        }

        playerInventory.removeItemStack(adamantiteIngot, true, true);
        lockContainer.setItemStackForSlot((short) 0, adamantiteIngot);
        this.sendPlayerMessage(player, "Inserted one adamantite ingot into the VoidChest lock slot.");
    }

    private void removeAdamantiteFromLockSlot(Player player, ItemContainer lockContainer) {
        ItemStack lockItem = lockContainer.getItemStack((short) 0);

        if (lockItem == null || lockItem.isEmpty()) {
            this.sendPlayerMessage(player, "The VoidChest lock slot is empty.");
            return;
        }

        lockContainer.removeItemStackFromSlot((short) 0);
        this.returnItemToPlayer(player, lockItem);
        this.sendPlayerMessage(player, "Removed the adamantite ingot from the VoidChest lock slot.");
    }

    private String createPocketDimensionSafeLocationKey(int posX, int posY, int posZ) {
        return posX + ":" + posY + ":" + posZ;
    }

    private String createVoidChestLocationKey(int posX, int posY, int posZ) {
        return posX + ":" + posY + ":" + posZ;
    }

    private VoidChestBlockConfig getOrCreateVoidChestBlockConfig(int posX, int posY, int posZ) {
        String locationKey = this.createVoidChestLocationKey(posX, posY, posZ);

        try {
            DatabaseHandler.VoidChestBlockConfig savedConfig = this.dbJsonObject.getVoidChestBlockConfig(locationKey);

            if (savedConfig != null) {
                return new VoidChestBlockConfig(
                        this.normalizeColorCode(savedConfig.colorCode),
                        savedConfig.ownerUuid,
                        savedConfig.ownerName
                );
            }

            this.dbJsonObject.saveVoidChestBlockConfig(locationKey, DEFAULT_VOID_CHEST_COLOR_CODE, null, null);
        } catch (Exception errCatch) {
            System.err.println("[VoidStorage] Failed to load VoidChest config for " + locationKey + ": " + errCatch.getMessage());
        }

        return new VoidChestBlockConfig(DEFAULT_VOID_CHEST_COLOR_CODE, null, null);
    }

    private void saveVoidChestBlockConfig(int posX, int posY, int posZ, VoidChestBlockConfig blockConfig) {
        if (!this.canUseDatabase()) {
            return;
        }

        String locationKey = this.createVoidChestLocationKey(posX, posY, posZ);

        try {
            this.dbJsonObject.saveVoidChestBlockConfig(
                    locationKey,
                    this.normalizeColorCode(blockConfig.colorCode),
                    blockConfig.ownerUuid,
                    blockConfig.ownerName
            );
        } catch (Exception errCatch) {
            System.err.println("VoidStorage] Failed to save VoidChest config for " + locationKey + ": " + errCatch.getMessage());
        }
    }

    private void applyVoidChestLockWindow(
            Player player,
            int posX,
            int posY,
            int posZ,
            VoidChestBlockConfig previousConfig,
            ItemContainer lockContainer,
            String selectedColorCode
    ) {
        ItemStack lockItem = lockContainer.getItemStack((short) 0);
        String colorCode = this.normalizeColorCode(selectedColorCode);

        if (lockItem == null || lockItem.isEmpty()) {
            VoidChestBlockConfig newConfig = new VoidChestBlockConfig(colorCode, null, null);
            this.saveVoidChestBlockConfig(posX, posY, posZ, newConfig);
            this.syncVoidChestVisualState(player, posX, posY, posZ, newConfig);
            this.sendPlayerMessage(player, "VoidChest set to public network " + colorCode + ".");
            return;
        }

        if (!ADAMANTITE_INGOT_ITEM_ID.equals(lockItem.getItemId())) {
            this.returnItemToPlayer(player, lockItem);
            VoidChestBlockConfig newConfig = new VoidChestBlockConfig(colorCode, null, null);
            this.saveVoidChestBlockConfig(posX, posY, posZ, newConfig);
            this.syncVoidChestVisualState(player, posX, posY, posZ, newConfig);
            this.sendPlayerMessage(player, "Only an adamantite ingot can lock an VoidChest. Invalid item returned.");
            return;
        }

        if (lockItem.getQuantity() > 1) {
            this.returnItemToPlayer(player, lockItem.withQuantity(lockItem.getQuantity() - 1));
        }

        UUID playerUuid = this.getPlayerUuid(player);

        if (previousConfig.ownerUuid == null && playerUuid == null) {
            this.returnItemToPlayer(player, lockItem);
            this.sendPlayerMessage(player, "Unable to identify player for VoidChest ownership.");
            return;
        }

        UUID ownerUuid = previousConfig.ownerUuid == null ? playerUuid : previousConfig.ownerUuid;
        String ownerName = this.resolveOwnerName(player, previousConfig, ownerUuid);
        VoidChestBlockConfig newConfig = new VoidChestBlockConfig(colorCode, ownerUuid, ownerName);
        this.saveVoidChestBlockConfig(posX, posY, posZ, newConfig);
        this.syncVoidChestVisualState(player, posX, posY, posZ, newConfig);
        this.sendPlayerMessage(player, "VoidChest set to private network " + colorCode + " owned by " + ownerName + ".");
    }

    private void syncVoidChestVisualState(
            Player player,
            int posX,
            int posY,
            int posZ,
            VoidChestBlockConfig blockConfig
    ) {
        if (player == null || player.getWorld() == null || blockConfig == null) {
            return;
        }

        try {
            BlockType currentBlockType = player.getWorld().getBlockType(posX, posY, posZ);

            if (!this.isVoidChestBlock(currentBlockType)) {
                return;
            }

            String visualState = blockConfig.ownerUuid == null
                    ? VOID_CHEST_PUBLIC_VISUAL_STATE
                    : VOID_CHEST_PRIVATE_VISUAL_STATE;

            assert currentBlockType != null;
            player.getWorld().setBlockInteractionState(
                    new Vector3i(posX, posY, posZ),
                    currentBlockType,
                    visualState
            );
        } catch (Exception errCatch) {
            System.err.println("[VoidStorage] Failed to sync VoidChest visual state at "
                    + posX + ":" + posY + ":" + posZ + ": " + errCatch.getMessage());
        }
    }

    private String resolveOwnerName(Player player, VoidChestBlockConfig previousConfig, UUID ownerUuid) {
        if (previousConfig.ownerName != null && !previousConfig.ownerName.isBlank()) {
            return previousConfig.ownerName;
        }

        if (player != null && ownerUuid != null && ownerUuid.equals(this.getPlayerUuid(player))) {
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
            return DEFAULT_VOID_CHEST_COLOR_CODE;
        }

        return colorCode;
    }

    private boolean canAccessPocketDimensionSafe(Player player, String locationKey) {
        try {
            UUID ownerUuid = this.dbJsonObject.getPocketDimensionSafeOwner(locationKey);

            if (ownerUuid == null) {
                return true;
            }

            if (ownerUuid.equals(this.getPlayerUuid(player))) {
                return true;
            }

            if (this.isServerOperator(player)) {
                return true;
            }

            this.sendPlayerMessage(player, "You do not have permission to access this pocket dimension safe.");
            return false;
        } catch (Exception errCatch) {
            System.err.println("[VoidStorage] Failed to check pocket_DimensionSafe owner for " + locationKey + ": " + errCatch.getMessage());
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
            System.err.println("[VoidStorage] Failed to create pocket_DimensionSafe owner for " + locationKey + ": " + errCatch.getMessage());
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
                UUID playerUuid = this.getPlayerUuid(player);

                if (playerUuid != null) {
                    this.dbJsonObject.savePocketDimensionSafeInventory(locationKey, playerUuid, "{}");
                }
            }
        } catch (Exception errCatch) {
            System.err.println("[VoidStorage] Failed to register placed pocket_DimensionSafe at " + locationKey + ": " + errCatch.getMessage());
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

            if (ownerUuid.equals(this.getPlayerUuid(player))) {
                return true;
            }

            return this.isServerOperator(player);
        } catch (Exception errCatch) {
            System.err.println("[VoidStorage] Failed to check pocket_DimensionSafe modification permission for " + locationKey + ": " + errCatch.getMessage());
            return false;
        }
    }

    /*UUID getPocketDimensionSafeOwner(int posX, int posY, int posZ) {
        String locationKey = this.createPocketDimensionSafeLocationKey(posX, posY, posZ);

        try {
            return this.dbJsonObject.getPocketDimensionSafeOwner(locationKey);
        } catch (Exception errCatch) {
            System.err.println("[VoidStorage] Failed to get pocket_DimensionSafe owner for " + locationKey + ": " + errCatch.getMessage());
            return null;
        }
    }*/

    private boolean isServerOperator(Player player) {
        return player != null
                && (
                player.hasPermission("voidstorage.admin")
                        || player.hasPermission("voidstorage.safe.bypass")
        );
    }

    private ItemContainer loadPocketDimensionSafeContainer(String locationKey) {
        try {
            String inventoryJson = this.dbJsonObject.getPocketDimensionSafeInventory(locationKey);
            return this.itemContainerFromJson(inventoryJson, "pocket_DimensionSafe " + locationKey);
        } catch (Exception errCatch) {
            System.err.println("[VoidStorage] Error loading pocket_DimensionSafe data for " + locationKey + ": " + errCatch.getMessage());
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
            System.err.println("[VoidStorage] Error saving pocket_DimensionSafe data for " + locationKey + ": " + errCatch.getMessage());
        }
    }

    private ItemContainer loadVoidChestContainer(String colorCode, UUID playerUuid) {
        try {
            String inventoryJson = this.dbJsonObject.getVoidChestInventory(colorCode, playerUuid);
            return this.itemContainerFromJson(inventoryJson, "VoidChest " + colorCode);
        } catch (Exception errCatch) {
            System.err.println("[VoidStorage] Error loading VoidChest data for " + colorCode + ": " + errCatch.getMessage());
            return new SimpleItemContainer(INVENTORY_SLOT_COUNT);
        }
    }

    private void saveVoidChestContainer(String colorCode, UUID optionalPlayerUuid, ItemContainer itemContainer) {
        if (!this.canUseDatabase()) {
            return;
        }

        try {
            String inventoryJson = this.itemContainerToJson(itemContainer);
            this.dbJsonObject.saveVoidChestInventory(colorCode, optionalPlayerUuid, inventoryJson);
        } catch (Exception errCatch) {
            System.err.println("[VoidStorage] Error saving VoidChest data for " + colorCode + ": " + errCatch.getMessage());
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
                        System.err.println("[VoidStorage] Failed to parse metadata for " + debugName + " slot " + inventorySlot);
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

    private static class VoidChestKey {
        private final String colorCode;
        private final UUID playerUuid;

        private VoidChestKey(String colorCode, UUID playerUuid) {
            this.colorCode = colorCode;
            this.playerUuid = playerUuid;
        }

        private static String toStorageKey(String colorCode, UUID playerUuid) {
            if (playerUuid == null) {
                return "public:" + colorCode;
            }

            return "player:" + playerUuid + ":" + colorCode;
        }

        private static VoidChestKey fromStorageKey(String storageKey) {
            if (storageKey.startsWith("player:")) {
                String remaining = storageKey.substring("player:".length());
                int uuidEndIndex = remaining.indexOf(':');

                if (uuidEndIndex > 0) {
                    UUID playerUuid = UUID.fromString(remaining.substring(0, uuidEndIndex));
                    String colorCode = remaining.substring(uuidEndIndex + 1);
                    return new VoidChestKey(colorCode, playerUuid);
                }
            }

            if (storageKey.startsWith("public:")) {
                return new VoidChestKey(storageKey.substring("public:".length()), null);
            }

            return new VoidChestKey(storageKey, null);
        }
    }

    private static class VoidChestBlockConfig {
        private final String colorCode;
        private final UUID ownerUuid;
        private final String ownerName;

        private VoidChestBlockConfig(String colorCode, UUID ownerUuid, String ownerName) {
            this.colorCode = colorCode;
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName;
        }
    }

    /*boolean isPocketDimensionSafeEmpty(int posX, int posY, int posZ) {
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
    }*/

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
            System.err.println("[VoidStorage] Failed to delete pocket_DimensionSafe at " + locationKey + ": " + errCatch.getMessage());
        }
    }
}
