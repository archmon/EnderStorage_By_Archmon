package me.archmon;

import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/**
 * Minimal public API for automation integrations.
 */
public interface EnderStorageApi {
    /**
     * Returns the default public Ender_Chest inventory used by automation.
     */
    ItemContainer getEnderChestInventoryForAutomation();

    /**
     * Persists the default public Ender_Chest automation inventory.
     */
    void saveEnderChestInventory();
}
