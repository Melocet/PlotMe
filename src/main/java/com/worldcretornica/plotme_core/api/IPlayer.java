package com.worldcretornica.plotme_core.api;

public interface IPlayer extends ICommandSender, IEntity, IOfflinePlayer {

    IItemStack getItemInHand();

    /** Play a named sound at the player. Soft-fail if the sound name is unknown. */
    default void playSound(String soundName, float volume, float pitch) {}
}
