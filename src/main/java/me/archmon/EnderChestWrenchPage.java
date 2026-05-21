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

import java.util.function.BooleanSupplier;

public class EnderChestWrenchPage extends InteractiveCustomUIPage<EnderChestWrenchPage.WrenchPageEventData> {

    private static final String PAGE_PATH = "Pages/EnderChestWrenchPage.ui";
    private static final String ADAMANTITE_INGOT_ITEM_ID = "Ingredient_Bar_Adamantite";

    private final String ownerDescription;
    private final int[] selectedColors;
    private final BooleanSupplier hasAdamantiteInLockSlot;
    private final Runnable insertAdamantiteAction;
    private final Runnable removeAdamantiteAction;

    public EnderChestWrenchPage(
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
            Ref<EntityStore> playerReference,
            UICommandBuilder commandBuilder,
            UIEventBuilder eventBuilder,
            Store<EntityStore> store
    ) {
        commandBuilder.append(PAGE_PATH);
        this.bindEvents(eventBuilder);
        this.writeState(commandBuilder);
    }

    @Override
    public void handleDataEvent(
            Ref<EntityStore> playerReference,
            Store<EntityStore> store,
            WrenchPageEventData eventData
    ) {
        if (eventData.colorOne != null) {
            this.selectedColors[0] = this.parseColorIndex(eventData.colorOne, this.selectedColors[0]);
        }

        if (eventData.colorTwo != null) {
            this.selectedColors[1] = this.parseColorIndex(eventData.colorTwo, this.selectedColors[1]);
        }

        if (eventData.colorThree != null) {
            this.selectedColors[2] = this.parseColorIndex(eventData.colorThree, this.selectedColors[2]);
        }

        if ("InsertAdamantite".equals(eventData.action) && this.insertAdamantiteAction != null) {
            this.insertAdamantiteAction.run();
        }

        if ("RemoveAdamantite".equals(eventData.action) && this.removeAdamantiteAction != null) {
            this.removeAdamantiteAction.run();
        }

        UICommandBuilder commandBuilder = new UICommandBuilder();
        this.writeState(commandBuilder);
        this.sendUpdate(commandBuilder, false);
    }

    private void bindEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#ColorOne",
                EventData.of(WrenchPageEventData.KEY_COLOR_ONE, "#ColorOne.Value"),
                false
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#ColorTwo",
                EventData.of(WrenchPageEventData.KEY_COLOR_TWO, "#ColorTwo.Value"),
                false
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#ColorThree",
                EventData.of(WrenchPageEventData.KEY_COLOR_THREE, "#ColorThree.Value"),
                false
        );
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
    }

    private void writeState(UICommandBuilder commandBuilder) {
        commandBuilder.set("#ColorOne.Value", Integer.toString(this.selectedColors[0]));
        commandBuilder.set("#ColorTwo.Value", Integer.toString(this.selectedColors[1]));
        commandBuilder.set("#ColorThree.Value", Integer.toString(this.selectedColors[2]));
        commandBuilder.set("#NetworkOwner.Text", this.ownerDescription);
        this.writeColorState(commandBuilder, "#ColorOneName", "#LeftSwatch", this.selectedColors[0]);
        this.writeColorState(commandBuilder, "#ColorTwoName", "#CenterSwatch", this.selectedColors[1]);
        this.writeColorState(commandBuilder, "#ColorThreeName", "#RightSwatch", this.selectedColors[2]);
        this.writeLockSlotState(commandBuilder);
    }

    private void writeColorState(UICommandBuilder commandBuilder, String namePath, String swatchPath, int colorIndex) {
        EnderChestColor color = EnderChestColor.byIndex(colorIndex);
        commandBuilder.set(namePath + ".Text", color.getDisplayName());
        commandBuilder.set(swatchPath + ".Background", color.getHexColor());
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

            if (colorIndex >= 0 && colorIndex < EnderChestColor.count()) {
                return colorIndex;
            }
        } catch (NumberFormatException ignored) {
        }

        return fallback;
    }

    public static class WrenchPageEventData {
        private static final String KEY_COLOR_ONE = "ColorOne";
        private static final String KEY_COLOR_TWO = "ColorTwo";
        private static final String KEY_COLOR_THREE = "ColorThree";
        private static final String KEY_ACTION = "Action";

        public static final BuilderCodec<WrenchPageEventData> CODEC = BuilderCodec
                .builder(WrenchPageEventData.class, WrenchPageEventData::new)
                .addField(new KeyedCodec<>(KEY_COLOR_ONE, new StringCodec(), false),
                        (eventData, value) -> eventData.colorOne = value,
                        eventData -> eventData.colorOne)
                .addField(new KeyedCodec<>(KEY_COLOR_TWO, new StringCodec(), false),
                        (eventData, value) -> eventData.colorTwo = value,
                        eventData -> eventData.colorTwo)
                .addField(new KeyedCodec<>(KEY_COLOR_THREE, new StringCodec(), false),
                        (eventData, value) -> eventData.colorThree = value,
                        eventData -> eventData.colorThree)
                .addField(new KeyedCodec<>(KEY_ACTION, new StringCodec(), false),
                        (eventData, value) -> eventData.action = value,
                        eventData -> eventData.action)
                .build();

        private String colorOne;
        private String colorTwo;
        private String colorThree;
        private String action;

        public WrenchPageEventData() {
        }
    }
}
