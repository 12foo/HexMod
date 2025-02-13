package at.petrak.hexcasting.common.command;

import at.petrak.hexcasting.api.PatternRegistry;
import at.petrak.hexcasting.hexmath.HexPattern;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;

public class PatternResLocArgument extends ResourceLocationArgument {
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_PATTERN = new DynamicCommandExceptionType(
        (errorer) ->
            new TranslatableComponent("hexcasting.pattern.unknown", errorer)
    );

    public static PatternResLocArgument id() {
        return new PatternResLocArgument();
    }

    public static HexPattern getPattern(
        CommandContext<CommandSourceStack> ctx, String pName) throws CommandSyntaxException {
        var targetId = ctx.getArgument(pName, ResourceLocation.class);
        var lookup = PatternRegistry.getPerWorldPatterns(ctx.getSource().getLevel());
        HexPattern foundPat = null;
        for (var key : lookup.keySet()) {
            var rhs = lookup.get(key);
            if (rhs.getFirst().equals(targetId)) {
                foundPat = HexPattern.FromAnglesSig(key, rhs.getSecond());
                break;
            }
        }

        if (foundPat == null) {
            throw ERROR_UNKNOWN_PATTERN.create(targetId);
        } else {
            return foundPat;
        }
    }
}
