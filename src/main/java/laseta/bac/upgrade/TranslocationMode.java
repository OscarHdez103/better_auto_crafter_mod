package laseta.bac.upgrade;

import java.util.Locale;

import laseta.bac.sideconfig.SideMode;

/** Which faces of the crafter a translocation upgrade moves to the linked Translocator. */
public enum TranslocationMode {
    /** Input faces (pulling ingredients). */
    PULL(SideMode.INPUT),
    /** Output faces (pushing the result). */
    PUSH(SideMode.OUTPUT);

    private final SideMode colorSource;

    TranslocationMode(SideMode colorSource) {
        this.colorSource = colorSource;
    }

    /** RGB colour, the same as the side configuration's input / output colour. */
    public int getColor() {
        return colorSource.getColor();
    }

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
