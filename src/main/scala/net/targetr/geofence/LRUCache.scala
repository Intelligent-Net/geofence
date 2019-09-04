package net.targetr.geofence

import java.util

class LRUCache[K, V](cacheSize: Int = 128) extends util.LinkedHashMap[K, V](16, 0.75f, true) {
  override protected def removeEldestEntry(eldest: util.Map.Entry[K, V]): Boolean =
    size > cacheSize
}
