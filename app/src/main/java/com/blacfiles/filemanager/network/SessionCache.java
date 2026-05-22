package com.blacfiles.filemanager.network;

import com.blacfiles.filemanager.model.FileItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory LRU-style cache for remote directory listings.
 *
 * Keyed by "protocol:host:port:path".  Cache entries are invalidated
 * when a mutating operation (delete, rename, mkdir, upload) is performed
 * on the same connection.
 *
 * Not thread-safe on its own — callers synchronise on the driver-level lock.
 */
public class SessionCache {

    private static final int MAX_ENTRIES = 64;

    private final Map<String, List<FileItem>> cache =
            new HashMap<String, List<FileItem>>();

    // Insertion-order tracking for simple LRU eviction
    private final java.util.LinkedList<String> order = new java.util.LinkedList<String>();

    public List<FileItem> get(String key) {
        return cache.get(key);
    }

    public void put(String key, List<FileItem> items) {
        if (cache.containsKey(key)) {
            order.remove(key);
        } else if (cache.size() >= MAX_ENTRIES) {
            String oldest = order.removeFirst();
            cache.remove(oldest);
        }
        cache.put(key, items);
        order.addLast(key);
    }

    /** Removes all entries whose key contains the given path prefix. */
    public void invalidate(String pathPrefix) {
        java.util.Iterator<String> it = cache.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            if (key.contains(pathPrefix)) {
                it.remove();
                order.remove(key);
            }
        }
    }

    /** Clears all cached entries. */
    public void clear() {
        cache.clear();
        order.clear();
    }
}
