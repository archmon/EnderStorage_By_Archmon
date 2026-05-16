//This class was heavily influenced by the EnderChestListener class of the original EnderChest mod by 01Kvothe10

package me.archmon;

import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;

import java.util.function.Consumer;

public class EnderStorageListener implements Consumer<PlayerInteractEvent> {

    private final EnderStorageManager manager;

    public EnderStorageListener(EnderStorageManager manager) {
        this.manager = manager;
    }

    @Override
    public void accept(PlayerInteractEvent playerInteractEvent) {
    }
}
