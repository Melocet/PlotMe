package com.worldcretornica.plotme_core.flag;

/**
 * Generic per-plot flag. A flag has a unique name, a default value used when
 * the plot has not explicitly set it, and a (de)serializer pair so we can
 * round-trip the value through the per-plugin metadata table that PlotMe
 * already persists.
 *
 * Subclasses (or anonymous instances) override {@link #parse(String)} and
 * {@link #serialize(Object)}. The metadata storage layer only deals with
 * strings, so all flags must be representable as a single string.
 *
 * @param <T> the runtime type of the flag value
 */
public abstract class PlotFlag<T> {

    private final String name;
    private final T defaultValue;

    protected PlotFlag(String name, T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    /**
     * Lowercase, kebab-cased canonical name (e.g. {@code pvp},
     * {@code mob-spawning}). This is the key used both in the user-facing
     * command and in the metadata table.
     */
    public final String getName() {
        return name;
    }

    public final T getDefaultValue() {
        return defaultValue;
    }

    /**
     * Parse a user-supplied string into a typed value. Implementations should
     * throw {@link IllegalArgumentException} on bad input; callers (the flag
     * command) translate that into a usage hint for the player.
     */
    public abstract T parse(String input) throws IllegalArgumentException;

    /**
     * Serialize a typed value back to its storage form. Must be lossless with
     * respect to {@link #parse(String)} for any value this flag could produce.
     */
    public abstract String serialize(T value);

    /**
     * Optional human-readable hint shown when the user runs the flag command
     * with no value, used to describe accepted input. Defaults to the flag's
     * type-agnostic name.
     */
    public String getValueHint() {
        return "<value>";
    }

    @Override
    public String toString() {
        return "PlotFlag{" + name + " default=" + defaultValue + '}';
    }
}
