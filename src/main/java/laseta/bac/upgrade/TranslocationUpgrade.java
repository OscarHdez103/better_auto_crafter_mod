package laseta.bac.upgrade;

/**
 * An upgrade for the crafter's pull or push slot: the Translocation Upgrades (faces move to a Translocator) and the
 * Multi Translocation Upgrades (linked blocks replace the faces).
 */
public interface TranslocationUpgrade {
    /** Whether it goes into the pull slot (ingredients) or the push slot (results). */
    TranslocationMode getMode();

    /** Whether it reaches other dimensions than the crafter's. */
    boolean isDimensional();
}
