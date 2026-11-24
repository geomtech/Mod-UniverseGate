package fr.geomtech.universegate;

import org.jetbrains.annotations.Nullable;

public enum WeatherSelection {
    CLEAR(0),
    RAIN(1),
    THUNDER(2),
    ACID_RAIN(3);

    private final int buttonId;

    WeatherSelection(int buttonId) {
        this.buttonId = buttonId;
    }

    public int buttonId() {
        return buttonId;
    }

    public String serializedName() {
        return switch (this) {
            case CLEAR -> "clear";
            case RAIN -> "rain";
            case THUNDER -> "thunder";
            case ACID_RAIN -> "acid_rain";
        };
    }

    @Nullable
    public static WeatherSelection fromButtonId(int buttonId) {
        for (WeatherSelection value : values()) {
            if (value.buttonId == buttonId) {
                return value;
            }
        }
        return null;
    }

    @Nullable
    public static WeatherSelection fromSerializedName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return switch (name) {
            case "clear" -> CLEAR;
            case "rain" -> RAIN;
            case "thunder" -> THUNDER;
            case "acid_rain" -> ACID_RAIN;
            default -> null;
        };
    }
}
