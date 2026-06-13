package com.worldcretornica.plotme_core;

public record PlotId(int x, int z) {

    public PlotId(String id) throws NumberFormatException {
        this(
                Integer.parseInt(id.substring(0, id.indexOf(';'))),
                Integer.parseInt(id.substring(id.indexOf(';') + 1))
        );
    }

    /**
     * Check if the string is in the plot id format
     *
     * @param id id value to be checked
     * @return true if the id is valid, false otherwise
     */
    public static boolean isValidID(String id) {
        String[] coords = id.split(";");
        if (coords.length == 2) {
            try {
                Integer.parseInt(coords[0]);
                Integer.parseInt(coords[1]);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    public String getID() {
        return x + ";" + z;
    }

    @Override
    public String toString() {
        return getID();
    }
}
