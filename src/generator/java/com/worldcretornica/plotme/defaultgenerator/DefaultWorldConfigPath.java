package com.worldcretornica.plotme.defaultgenerator;

/**
 * Configuration keys for a single PlotMe world. Inlined from the old
 * com.worldcretornica.plotme_abstractgenerator project; defaults are
 * still expressed in legacy "<id>[:<data>]" form because real on-disk
 * configs use those — MaterialParser handles the translation.
 */
public enum DefaultWorldConfigPath {

    PLOT_SIZE("PlotSize", 32),
    GROUND_LEVEL("RoadHeight", 64),
    PATH_WIDTH("PathWidth", 7),

    FILL_BLOCK("FillBlock", "3"),
    PLOT_FLOOR_BLOCK("PlotFloorBlock", "2"),
    ROAD_MAIN_BLOCK("RoadMainBlock", "5"),
    ROAD_ALT_BLOCK("RoadAltBlock", "5:2"),
    WALL_BLOCK("WallBlock", "44"),
    PROTECTED_WALL_BLOCK("ProtectedWallBlock", "44:4"),
    FOR_SALE_WALL_BLOCK("ForSaleWallBlock", "44:1"),
    UNCLAIMED_WALL("UnclaimedBorder", "44:7"),

    X_TRANSLATION("XTranslation", 0),
    Z_TRANSLATION("ZTranslation", 0),

    BIOME("BIOME", "PLAINS");

    private final String key;
    private final Object def;

    DefaultWorldConfigPath(String key, Object def) {
        this.key = key;
        this.def = def;
    }

    public String key() {
        return key;
    }

    public Object value() {
        return def;
    }
}
