package at.petrak.hexcasting.common.casting.operators.math

import at.petrak.hexcasting.api.ConstManaOperator
import at.petrak.hexcasting.api.Operator.Companion.getChecked
import at.petrak.hexcasting.api.Operator.Companion.spellListOf
import at.petrak.hexcasting.api.SpellDatum
import at.petrak.hexcasting.common.casting.CastingContext
import net.minecraft.world.phys.Vec3

object OpConstructVec : ConstManaOperator {
    override val argc = 3
    override fun execute(args: List<SpellDatum<*>>, ctx: CastingContext): List<SpellDatum<*>> {
        val x = args.getChecked<Double>(0)
        val y = args.getChecked<Double>(1)
        val z = args.getChecked<Double>(2)
        return spellListOf(Vec3(x, y, z))
    }
}