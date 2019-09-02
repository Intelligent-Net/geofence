package net.targetr.geofence

import java.util.LinkedHashMap
import java.util.Map

class LRUCache[K, V](cacheSize: Int = 128) extends LinkedHashMap[K, V](16, 0.75f, true) {
  override protected def removeEldestEntry(eldest: Map.Entry[K, V]): Boolean =
    size > cacheSize
}
