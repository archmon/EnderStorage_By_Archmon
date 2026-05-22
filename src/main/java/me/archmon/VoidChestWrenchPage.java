package me.archmon;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import java.util.function.BooleanSupplier;

class VoidChestWrenchPage extends InteractiveCustomUIPage<VoidChestWrenchPage.WrenchPageEventData> {

    private static final String PAGE_PATH = "Pages/VoidChestWrenchPage.ui";
    private static final String ADAMANTITE_INGOT_ITEM_ID = "Ingredient_Bar_Adamantite";

    private final String ownerDescription;
    private final int[] selectedColors;
    private final BooleanSupplier hasAdamantiteInLockSlot;
    private final Runnable insertAdamantiteAction;
    private final Runnable removeAdamantiteAction;

    VoidChestWrenchPage(
            PlayerRef playerRef,
            String colorCode,
            String ownerDescription,
            BooleanSupplier hasAdamantiteInLockSlot,
            Runnable insertAdamantiteAction,
            Runnable removeAdamantiteAction
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, WrenchPageEventData.CODEC);
        this.ownerDescription = ownerDescription;
        this.selectedColors = this.parseColorCode(colorCode);
        this.hasAdamantiteInLockSlot = hasAdamantiteInLockSlot;
        this.insertAdamantiteAction = insertAdamantiteAction;
        this.removeAdamantiteAction = removeAdamantiteAction;
    }

    @Override
    public void build(
            @NonNull Ref<EntityStore> playerReference,
            UICommandBuilder commandBuilder,
            @NonNull UIEventBuilder eventBuilder,
            @NonNull Store<EntityStore> store
    ) {
        commandBuilder.append(PAGE_PATH);
        this.bindEvents(eventBuilder);
        this.writeState(commandBuilder);
    }

    @Override
    public void handleDataEvent(
            @NonNull Ref<EntityStore> playerReference,
            @NonNull Store<EntityStore> store,
            WrenchPageEventData eventData
    ) {
        if ("InsertAdamantite".equals(eventData.action) && this.insertAdamantiteAction != null) {
            this.insertAdamantiteAction.run();
        }

        if ("RemoveAdamantite".equals(eventData.action) && this.removeAdamantiteAction != null) {
            this.removeAdamantiteAction.run();
        }

        if ("ColorOnePrevious".equals(eventData.action)) {
            this.cycleColor(0, -1);
        }

        if ("ColorOneNext".equals(eventData.action)) {
            this.cycleColor(0, 1);
        }

        if ("ColorTwoPrevious".equals(eventData.action)) {
            this.cycleColor(1, -1);
        }

        if ("ColorTwoNext".equals(eventData.action)) {
            this.cycleColor(1, 1);
        }

        if ("ColorThreePrevious".equals(eventData.action)) {
            this.cycleColor(2, -1);
        }

        if ("ColorThreeNext".equals(eventData.action)) {
            this.cycleColor(2, 1);
        }

        UICommandBuilder commandBuilder = new UICommandBuilder();
        this.writeState(commandBuilder);
        this.sendUpdate(commandBuilder, false);
    }

    public String getSelectedColorCode() {
        return this.selectedColors[0] + ":" + this.selectedColors[1] + ":" + this.selectedColors[2];
    }

    private void bindEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#InsertAdamantite",
                EventData.of(WrenchPageEventData.KEY_ACTION, "InsertAdamantite"),
                false
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RemoveAdamantite",
                EventData.of(WrenchPageEventData.KEY_ACTION, "RemoveAdamantite"),
                false
        );
        this.bindAction(eventBuilder, "#ColorOnePrevious", "ColorOnePrevious");
        this.bindAction(eventBuilder, "#ColorOneNext", "ColorOneNext");
        this.bindAction(eventBuilder, "#ColorTwoPrevious", "ColorTwoPrevious");
        this.bindAction(eventBuilder, "#ColorTwoNext", "ColorTwoNext");
        this.bindAction(eventBuilder, "#ColorThreePrevious", "ColorThreePrevious");
        this.bindAction(eventBuilder, "#ColorThreeNext", "ColorThreeNext");
    }

    private void bindAction(UIEventBuilder eventBuilder, String selector, String action) {
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                EventData.of(WrenchPageEventData.KEY_ACTION, action),
                false
        );
    }

    private void writeState(UICommandBuilder commandBuilder) {
        commandBuilder.set("#NetworkOwner.Text", this.ownerDescription);
        this.writeColorState(commandBuilder, "#ColorOneName", "#LeftSwatch", this.selectedColors[0]);
        this.writeColorState(commandBuilder, "#ColorTwoName", "#CenterSwatch", this.selectedColors[1]);
        this.writeColorState(commandBuilder, "#ColorThreeName", "#RightSwatch", this.selectedColors[2]);
        this.writeLockSlotState(commandBuilder);
    }

    private void writeColorState(UICommandBuilder commandBuilder, String namePath, String swatchPath, int colorIndex) {
        VoidChestColor color = VoidChestColor.byIndex(colorIndex);
        commandBuilder.set(namePath + ".Text", color.getDisplayName());
        commandBuilder.set(swatchPath + ".Background", color.getHexColor());
    }

    private void cycleColor(int colorPosition, int direction) {
        int colorCount = VoidChestColor.count();
        this.selectedColors[colorPosition] = Math.floorMod(this.selectedColors[colorPosition] + direction, colorCount);
    }

    private void writeLockSlotState(UICommandBuilder commandBuilder) {
        boolean isLocked = this.hasAdamantiteInLockSlot != null && this.hasAdamantiteInLockSlot.getAsBoolean();
        commandBuilder.set("#LockPreviewIcon.Visible", isLocked);
        commandBuilder.set("#LockPreviewIcon.ItemId", isLocked ? ADAMANTITE_INGOT_ITEM_ID : "");
    }

    private int[] parseColorCode(String colorCode) {
        int[] colorIndexes = new int[]{0, 0, 0};

        if (colorCode == null || colorCode.isBlank()) {
            return colorIndexes;
        }

        String[] parts = colorCode.split(":");

        for (int index = 0; index < Math.min(parts.length, colorIndexes.length); index++) {
            colorIndexes[index] = this.parseColorIndex(parts[index], 0);
        }

        return colorIndexes;
    }

    private int parseColorIndex(String value, int fallback) {
        try {
            int colorIndex = Integer.parseInt(value);

            if (colorIndex >= 0 && colorIndex < VoidChestColor.count()) {
                return colorIndex;
            }
        } catch (NumberFormatException ignored) {
        }

        return fallback;
    }

    public static class WrenchPageEventData {
        private static final String KEY_ACTION = "Action";

        public static final BuilderCodec<WrenchPageEventData> CODEC = BuilderCodec
                .builder(WrenchPageEventData.class, WrenchPageEventData::new)
                .addField(new KeyedCodec<>(KEY_ACTION, new StringCodec(), false),
                        (eventData, value) -> eventData.action = value,
                        eventData -> eventData.action)
                .build();

        private String action;

        public WrenchPageEventData() {
        }
    }
}
