package me.archmon;

enum EnderChestColor {
    WHITE("White", "#f9fffe"),
    LIGHT_GRAY("Light Gray", "#9d9d97"),
    GRAY("Gray", "#474f52"),
    BLACK("Black", "#1d1d21"),
    BROWN("Brown", "#835432"),
    RED("Red", "#b02e26"),
    ORANGE("Orange", "#f9801d"),
    YELLOW("Yellow", "#fed83d"),
    LIME("Lime", "#80c71f"),
    GREEN("Green", "#5e7c16"),
    CYAN("Cyan", "#169c9c"),
    LIGHT_BLUE("Light Blue", "#3ab3da"),
    BLUE("Blue", "#3c44aa"),
    PURPLE("Purple", "#8932b8"),
    MAGENTA("Magenta", "#c74ebd"),
    PINK("Pink", "#f38baa");

    private static final EnderChestColor[] VALUES = values();

    private final String displayName;
    private final String hexColor;

    EnderChestColor(String displayName, String hexColor) {
        this.displayName = displayName;
        this.hexColor = hexColor;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getHexColor() {
        return this.hexColor;
    }

    public static int count() {
        return VALUES.length;
    }

    public static EnderChestColor byIndex(int index) {
        if (index < 0 || index >= VALUES.length) {
            return WHITE;
        }

        return VALUES[index];
    }
}
