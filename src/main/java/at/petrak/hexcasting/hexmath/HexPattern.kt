package at.petrak.hexcasting.hexmath

import at.petrak.hexcasting.client.RenderLib
import net.minecraft.nbt.ByteArrayTag
import net.minecraft.nbt.ByteTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec2
import kotlin.math.max
import kotlin.math.min

/**
 * Sequence of angles to define a pattern traced.
 */
@JvmRecord
data class HexPattern(val startDir: HexDir, val angles: MutableList<HexAngle> = arrayListOf()) {
    /**
     * @return True if it successfully appended, false if not.
     */
    fun tryAppendDir(newDir: HexDir): Boolean {
        // Two restrictions:
        // - No adding a pos/dir pair we previously added
        // - No backtracking
        // TODO this doesn't seem to work very well
        val linesSeen = mutableSetOf<Pair<HexCoord, HexDir>>()

        var compass = this.startDir
        var cursor = HexCoord.Origin
        for (a in this.angles) {
            linesSeen.add(Pair(cursor, compass))
            // Line from here to there also blocks there to here
            linesSeen.add(Pair(cursor + compass, compass.rotatedBy(HexAngle.BACK)))
            cursor += compass
            compass *= a
        }
        cursor += compass

        val potentialNewLine = Pair(cursor, newDir)
        if (potentialNewLine in linesSeen) return false
        val nextAngle = newDir - compass
        if (nextAngle == HexAngle.BACK) return false

        this.angles.add(nextAngle)
        return true
    }

    @JvmOverloads
    fun positions(start: HexCoord = HexCoord.Origin): List<HexCoord> {
        val out: ArrayList<HexCoord> = ArrayList(this.angles.size + 2)
        out.add(start)
        var compass: HexDir = this.startDir
        var cursor = start
        for (a in this.angles) {
            cursor += compass
            out.add(cursor)
            compass *= a
        }
        out.add(cursor + compass)
        return out
    }

    fun finalDir(): HexDir =
        this.angles.fold(this.startDir) { acc, angle -> acc * angle }


    fun serializeToNBT(): CompoundTag {
        val out = CompoundTag()
        out.put(TAG_START_DIR, ByteTag.valueOf(this.startDir.ordinal.toByte()))
        val anglesTag = ByteArrayTag(this.angles.map { it.ordinal.toByte() })
        out.put(TAG_ANGLES, anglesTag)
        return out
    }

    // Terrible shorthand method for easy matching
    fun anglesSignature(): String {
        return buildString {
            for (a in this@HexPattern.angles) {
                append(
                    when (a) {
                        HexAngle.FORWARD -> "w"
                        HexAngle.RIGHT -> "e"
                        HexAngle.RIGHT_BACK -> "d"
                        HexAngle.BACK -> "s"
                        HexAngle.LEFT_BACK -> "a"
                        HexAngle.LEFT -> "q"
                    }
                )
            }
        }
    }

    /**
     * Return the "center of mass" of the pattern.
     * Drawing the pattern with the returned vector as the origin will center the pattern around it.
     */
    @JvmOverloads
    fun getCenter(hexRadius: Float, origin: HexCoord = HexCoord.Origin): Vec2 {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        val poses = this.positions()
        val vecOrigin = RenderLib.coordToPx(origin, hexRadius, Vec2.ZERO)
        for (pos in poses) {
            val px = RenderLib.coordToPx(pos, hexRadius, vecOrigin)
            minX = min(minX, px.x)
            minY = min(minY, px.y)
            maxX = max(maxX, px.x)
            maxY = max(maxY, px.y)
        }
        return Vec2(
            (minX + maxX) / 2f,
            (minY + maxY) / 2f
        )
    }


    /**
     * Convert a hex pattern into a sequence of straight linePoints spanning its points.
     */
    fun toLines(hexSize: Float, origin: Vec2): List<Vec2> =
        this.positions().map { RenderLib.coordToPx(it, hexSize, origin) }

    override fun toString(): String = buildString {
        append("HexPattern[")
        append(this@HexPattern.startDir)
        append(", ")
        append(this@HexPattern.anglesSignature())
        append("]")
    }

    companion object {
        const val TAG_START_DIR = "start_dir"
        const val TAG_ANGLES = "angles"

        @JvmStatic
        fun DeserializeFromNBT(tag: CompoundTag): HexPattern {
            val startDir = HexDir.values()[tag.getByte(TAG_START_DIR).toInt()]
            val angles = tag.getByteArray(TAG_ANGLES).map { HexAngle.values()[it.toInt()] }
            return HexPattern(startDir, angles.toMutableList())
        }

        @JvmStatic
        fun FromAnglesSig(signature: String, startDir: HexDir): HexPattern {
            val out = HexPattern(startDir)
            var compass = startDir

            for ((idx, c) in signature.withIndex()) {
                val angle = when (c) {
                    'w' -> HexAngle.FORWARD
                    'e' -> HexAngle.RIGHT
                    'd' -> HexAngle.RIGHT_BACK
                    // for completeness ...
                    's' -> HexAngle.BACK
                    'a' -> HexAngle.LEFT_BACK
                    'q' -> HexAngle.LEFT
                    else -> throw IllegalArgumentException("Cannot match $c at idx $idx to a direction")
                }
                compass *= angle
                val success = out.tryAppendDir(compass)
                if (!success) {
                    throw IllegalStateException("Adding the angle $c at index $idx made the pattern invalid by looping back on itself")
                }
            }
            return out
        }

    }
}