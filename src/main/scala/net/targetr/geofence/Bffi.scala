package net.targetr.geofence

import java.util._
import java.io._
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

// Float Float Int
object Bffi
{
  val entrySize = 4 + 4 + 4

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

/*
object Bffi {
  def main(args: Array[String]): Unit = {
    val size = if (args.length > 0) args(0).toInt else 10000000
    val its = if (args.length > 1) args(1).toInt else 1

    val entrySize = 4 + 4 + 4
    //private val kv = ByteBuffer.allocateDirect(size * entrySize)
    val raf = new RandomAccessFile("mmf", "rw")
    val kv = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, size * entrySize)

    val bffi = Bffi(kv)
 
    for (i <- 0 until size) {
      bffi.set(i, i.toFloat, (i * 2.5).toFloat, i.toInt)
    }

    val t = Run.time {
      for (_ <- 0 until its) {
        for (i <- 0 until size) {
          val res = bffi.get(i)

          if (i > 0 && (i % (size / 10)) == 0)
            println(res)
            //res
        }
      }
    }

    println(s"t = ${t / its} ms")

    raf.close
  }
}
*/
