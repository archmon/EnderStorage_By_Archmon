package me.archmon;

import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/**
 * Minimal public API for automation integrations.
 */
public interface VoidStorageApi {
    /**
     * Returns the default public VoidChest inventory used by automation.
     */
    ItemContainer getVoidChestInventoryForAutomation();

    /**
     * Persists the default public VoidChest automation inventory.
     */
    void saveVoidChestInventory();
}
