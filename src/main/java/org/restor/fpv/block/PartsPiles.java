package org.restor.fpv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.restor.fpv.block.entity.PartsPileBlockEntity;

/** Setting a part down on a block starts a pile there; further parts stack into that same pile. */
public final class PartsPiles {
    private PartsPiles() {}

    public static InteractionResult placeOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPlaceContext place = new BlockPlaceContext(ctx);
        BlockPos pos = place.getClickedPos();
        ItemStack held = ctx.getItemInHand();
        boolean creative = ctx.getPlayer() != null && ctx.getPlayer().getAbilities().instabuild;

        if (level.getBlockEntity(pos) instanceof PartsPileBlockEntity pile) {
            if (!pile.accepts(held)) return InteractionResult.PASS;
            if (!level.isClientSide) {
                pile.insert(creative ? held.copy() : held);
                playSet(level, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!place.canPlace()) return InteractionResult.PASS;
        BlockState state = ModBlocks.PARTS_PILE.get().getStateForPlacement(place);
        if (state == null || !state.canSurvive(level, pos)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (!level.setBlock(pos, state, 3)) return InteractionResult.PASS;
            if (level.getBlockEntity(pos) instanceof PartsPileBlockEntity pile) {
                pile.insert(creative ? held.copy() : held, 1);
            }
            playSet(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void playSet(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7f, 1.4f);
    }
}
