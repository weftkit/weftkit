package org.weftkit.examples.warps.storage;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.bukkit.Location;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.Wired;

@Wired
@Singleton
public final class WarpStore {

    private final Map<String, Location> warps = new TreeMap<>();

    public WarpStore() {}

    public void set(String name, Location location) {
        warps.put(name, location.clone());
    }

    public Location find(String name) {
        Location warp = warps.get(name);
        return warp == null ? null : warp.clone();
    }

    public Set<String> names() {
        return new TreeSet<>(warps.keySet());
    }
}
