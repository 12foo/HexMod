package at.petrak.hexcasting.api

import at.petrak.hexcasting.common.casting.CastException
import at.petrak.hexcasting.hexmath.EulerPathFinder
import at.petrak.hexcasting.hexmath.HexDir
import at.petrak.hexcasting.hexmath.HexPattern
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.saveddata.SavedData
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentMap

/**
 * Register your patterns and associate them with spells here.
 *
 * Most patterns are matches to their operators solely by their *angle signature*.
 * This is a record of all the angles in the pattern, independent of the direction the player started drawing in.
 * It's written in shorthand, where `w` is straight ahead, `q` is left, `a` is left-back, `d` is right-back,
 * and `e` is right.
 *
 * For example, the signature for a straight line of 3 segments is `"ww"`. The signatures for the "get caster"
 * operator (the diamond) are `"qaq"` and `"ede"`.
 */
object PatternRegistry {
    private val operatorLookup = ConcurrentHashMap<ResourceLocation, Operator>()
    private val specialHandlers: ConcurrentLinkedDeque<SpecialHandler> = ConcurrentLinkedDeque()

    // Map signatures to the "preferred" direction they start in and their operator ID.
    private val regularPatternLookup: ConcurrentMap<String, RegularEntry> =
        ConcurrentHashMap()

    private val perWorldPatternLookup: ConcurrentMap<ResourceLocation, PerWorldEntry> =
        ConcurrentHashMap()

    /**
     * Associate a given angle signature with a SpellOperator.
     */
    @JvmStatic
    @JvmOverloads
    @Throws(RegisterPatternException::class)
    fun mapPattern(pattern: HexPattern, id: ResourceLocation, operator: Operator, isPerWorld: Boolean = false) {
        this.operatorLookup[id]?.let {
            throw RegisterPatternException("The operator with id `$id` was already registered to: $it")
        }

        this.operatorLookup[id] = operator
        if (isPerWorld) {
            this.perWorldPatternLookup[id] = PerWorldEntry(pattern, id)
        } else {
            this.regularPatternLookup[pattern.anglesSignature()] = RegularEntry(pattern.startDir, id)
        }

    }


    /**
     * Add a special handler, to take an arbitrary pattern and return whatever kind of operator you like.
     */
    @JvmStatic
    fun addSpecialHandler(handler: SpecialHandler) {
        this.specialHandlers.add(handler)
    }

    /**
     * Internal use only.
     */
    @JvmStatic
    fun matchPattern(pat: HexPattern, overworld: ServerLevel): Operator {
        // Pipeline:
        // patterns are registered here every time the game boots
        // when we try to look
        for (handler in specialHandlers) {
            val op = handler.handlePattern(pat)
            if (op != null) return op
        }

        // Is it global?
        val sig = pat.anglesSignature()
        this.regularPatternLookup[sig]?.let {
            return this.operatorLookup[it.opId] ?: throw CastException(CastException.Reason.INVALID_PATTERN, pat)
        }

        // Look it up in the world?
        val ds = overworld.dataStorage
        val perWorldPatterns: Save =
            ds.computeIfAbsent(Save.Companion::load, { Save.create(overworld.seed) }, TAG_SAVED_DATA)
        perWorldPatterns.lookup[sig]?.let { return this.operatorLookup[it.first]!! }

        throw CastException(CastException.Reason.INVALID_PATTERN, pat)
    }

    /**
     * Internal use only.
     */
    @JvmStatic
    fun getPerWorldPatterns(overworld: ServerLevel): Map<String, Pair<ResourceLocation, HexDir>> {
        val ds = overworld.dataStorage
        val perWorldPatterns: Save =
            ds.computeIfAbsent(Save.Companion::load, { Save.create(overworld.seed) }, TAG_SAVED_DATA)
        return perWorldPatterns.lookup
    }

    /**
     * Internal use only.
     */
    @JvmStatic
    fun lookupPattern(opId: ResourceLocation): PatternEntry {
        this.perWorldPatternLookup[opId]?.let {
            return PatternEntry(it.prototype, this.operatorLookup[it.opId]!!, true)
        }
        for ((sig, entry) in this.regularPatternLookup) {
            if (entry.opId == opId) {
                val pattern = HexPattern.FromAnglesSig(sig, entry.preferredStart)
                return PatternEntry(pattern, this.operatorLookup[entry.opId]!!, false)
            }
        }

        throw IllegalArgumentException("could not find a pattern for $opId")
    }

    /**
     * Special handling of a pattern. Before checking any of the normal angle-signature based patterns,
     * a given pattern is run by all of these special handlers patterns. If none of them return non-null,
     * then its signature is checked.
     *
     * In the base mod, this is used for number patterns.
     */
    fun interface SpecialHandler {
        fun handlePattern(pattern: HexPattern): Operator?
    }

    class RegisterPatternException(msg: String) : java.lang.Exception(msg)

    private data class RegularEntry(val preferredStart: HexDir, val opId: ResourceLocation)
    private data class PerWorldEntry(val prototype: HexPattern, val opId: ResourceLocation)

    // Fake class we pretend to use internally
    data class PatternEntry(val prototype: HexPattern, val operator: Operator, val isPerWorld: Boolean)

    /**
     * Maps angle sigs to resource locations and their preferred start dir so we can look them up in the main registry
     */
    class Save(val lookup: MutableMap<String, Pair<ResourceLocation, HexDir>>) : SavedData() {
        override fun save(tag: CompoundTag): CompoundTag {
            for ((sig, rhs) in this.lookup) {
                val (id, startDir) = rhs
                val entry = CompoundTag()
                entry.putString(TAG_OP_ID, id.toString())
                entry.putInt(TAG_START_DIR, startDir.ordinal)
                tag.put(sig, entry)
            }
            return tag
        }

        companion object {
            fun load(tag: CompoundTag): Save {
                val map = HashMap<String, Pair<ResourceLocation, HexDir>>()
                for (sig in tag.allKeys) {
                    val entry = tag.getCompound(sig)
                    val opId = ResourceLocation.tryParse(entry.getString(TAG_OP_ID))!!
                    val startDir = HexDir.values()[entry.getInt(TAG_START_DIR)]
                    map[sig] = Pair(opId, startDir)
                }
                return Save(map)
            }

            @JvmStatic
            fun create(seed: Long): Save {
                val map = mutableMapOf<String, Pair<ResourceLocation, HexDir>>()
                for ((opId, entry) in PatternRegistry.perWorldPatternLookup) {
                    // waugh why doesn't kotlin recursively destructure things
                    val scrungled = EulerPathFinder.findAltDrawing(entry.prototype, seed)
                    map[scrungled.anglesSignature()] = Pair(opId, scrungled.startDir)
                }
                val save = Save(map)
                save.setDirty()
                return save
            }
        }
    }

    const val TAG_SAVED_DATA = "hex.per-world-patterns"
    private const val TAG_OP_ID = "op_id"
    private const val TAG_START_DIR = "start_dir"
}