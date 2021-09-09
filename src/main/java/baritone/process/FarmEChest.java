/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.process;

import baritone.Baritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.process.IFarmEChest;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.cache.WorldProvider;
import baritone.cache.WorldScanner;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


///SOMEONE DIDN'T TOUCHA MAH SPAGHETTA
public class FarmEChest extends BaritoneProcessHelper implements IFarmEChest {

    boolean active = false;
    boolean placedLastTick = false;
    BetterBlockPos startingPos;
    public FarmEChest(Baritone baritone) {
        super(baritone);
    }

    @Override
    public boolean isActive()
    {
        return active;
    }

    @Override
    public void mine()
    {
        startingPos = ctx.playerFeet();
        active = true;
    }

    private boolean isEChest(ItemStack itemStack)
    {
        return Item.getItemFromBlock(Blocks.ENDER_CHEST) == itemStack.getItem() && !itemStack.isEmpty();
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel)
    {
        if(Minecraft.getMinecraft().isGamePaused())
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        BetterBlockPos playerFeet = startingPos; //discord mods will sniff on these, hide this code piece from them

        if(!playerFeet.equals(ctx.playerFeet())) {
            //logDirect("I hate trannies with even more passion "+playerFeet.toString()+"!=" +ctx.playerFeet().toString());
            return new PathingCommand(new GoalBlock(playerFeet), PathingCommandType.SET_GOAL_AND_PATH);
        }
        List<BlockPos> toPlace = new ArrayList<>();
        for(int x = -1; x <= 1; ++x)
        for(int z = -1; z <= 1; ++z)
        if(Math.abs(x)+Math.abs(z) == 1)
            toPlace.add(playerFeet.add(x,0,z));
        List<Goal> goalz = new ArrayList<>();

        baritone.getInputOverrideHandler().clearAllKeys();
        if(ctx.player().onGround) {
            for (BlockPos pos : toPlace) {
                IBlockState block = ctx.world().getBlockState(pos);
                if(block.getBlock() == Blocks.AIR)
                {
                    goalz.add(new GoalBlock(pos));
                    BlockPos bottom = pos.add(0,-1,0);
                    Optional<Rotation> rot = RotationUtils.reachable(ctx, bottom);
                    if (rot.isPresent() && isSafeToCancel && baritone.getInventoryBehavior().throwaway(true, this::isEChest)) {
                        //logDirect("I hate trannies with passion");
                        baritone.getLookBehavior().updateTarget(rot.get(), true);
                        if (ctx.isLookingAt(bottom)) {
                            baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
                            if(ctx.player().isSneaking()) {
                                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                                if (block.getBlock() != Blocks.ENDER_CHEST) {
                                    baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_BACK, true);
                                }
                            }

                            placedLastTick = true;
                        }
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                    continue;
                }
                else if(!placedLastTick)
                {
                    goalz.add(new BuilderProcess.GoalBreak(pos));
                    Optional<Rotation> rot = RotationUtils.reachable(ctx, pos);
                    if (rot.isPresent() && isSafeToCancel) {
                        //logDirect("I hate trannies with burning passion OwO");
                        MovementHelper.switchToBestToolFor(ctx, block);
                        baritone.getLookBehavior().updateTarget(rot.get(), true);
                        if (ctx.isLookingAt(pos)) {
                            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                        }
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                    continue;
                }
            }
        }
        else
        {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        placedLastTick = false;

        if(calcFailed || !baritone.getInventoryBehavior().throwaway(false, this::isEChest)) {
            logDirect("EChest process failed");
            onLostControl();
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        return new PathingCommand(new GoalComposite(goalz.toArray(new Goal[0])), PathingCommandType.SET_GOAL_AND_PATH);
    }

    @Override
    public void onLostControl()
    {
        active = false;
    }

    @Override
    public String displayName0(){
        return "EChestFarmer";
    }
}
