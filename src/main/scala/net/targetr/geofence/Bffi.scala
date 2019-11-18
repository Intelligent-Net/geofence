package net.targetr.geofence

import java.nio.ByteBuffer

// Float Float Int
object Bffi
{
  val entrySize: Int = 4 + 4 + 4 // 2 Floats + an Int

  def set(kv: ByteBuffer, idx: Array[Long], i: Int, lat: Float, lon: Float, ts: Int): Int = {
    val pos = i * entrySize

    kv.putFloat(pos, lat)
    kv.putFloat(pos + 4, lon)
    kv.putInt(pos + 4 + 4, ts)

    val ix = ts / 60   // minute index

    if (idx(ix) == -1)  // set index of first minute occurrence
      idx(ix) = pos / entrySize

    pos
  }

  def get(kv: ByteBuffer, i: Int): (Float, Float, Int) = {
    val pos = i * entrySize

    (kv.getFloat(pos), kv.getFloat(pos + 4), kv.getInt(pos + 4 + 4))
  }
}
