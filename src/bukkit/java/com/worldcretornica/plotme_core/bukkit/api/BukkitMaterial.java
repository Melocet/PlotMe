package com.worldcretornica.plotme_core.bukkit.api;

import com.worldcretornica.plotme_core.api.IMaterial;
import org.bukkit.Material;

public class BukkitMaterial implements IMaterial {

    private final Material material;

    public BukkitMaterial(Material mat) {
        material = mat;
    }

    public Material getMaterial() {
        return material;
    }

    @Override
    public String getId() {
        // Material.getId() (numeric ID) is removed in modern Paper. The
        // IMaterial.getId() contract is consumed nowhere inside the plugin
        // today, so returning the enum/namespaced name is the only sane
        // forward-compatible value. Callers expecting a numeric ID would
        // already have been broken on 1.13+.
        return material.name();
    }
}
