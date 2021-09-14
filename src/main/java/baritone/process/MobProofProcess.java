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
import baritone.api.event.events.RenderEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.process.IMobProofProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import baritone.utils.IRenderer;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.awt.*;
import java.util.Optional;

import static baritone.api.pathing.movement.ActionCosts.COST_INF;

public class MobProofProcess extends BaritoneProcessHelper implements IMobProofProcess {
    private boolean active = false;
    private float range = 0;
    private BetterBlockPos origin = null;
    private BlockPos currentTarget = null;
    private int patience;
    private List<BlockPos> blackListed = null;

    public MobProofProcess(Baritone baritone)
    {
        super(baritone);
        baritone.getGameEventHandler().registerEventListener(new AbstractGameEventListener() {
            @Override
            public void onRenderPass(RenderEvent event) {
                if(!active || origin == null)
                    return;
                IRenderer.startLines(Color.red, 0.5f, 2, true);
                IRenderer.drawAABB(new AxisAlignedBB(origin, origin.add(1, 1, 1)));
                IRenderer.endLines(true);
            }
        });
    }

    @Override
    public boolean isActive()
    {
        return active;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel)
    {
        if(Minecraft.getMinecraft().isGamePaused())
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);

        if(blackListed == null)
            blackListed = new ArrayList<BlockPos>();

        baritone.getInputOverrideHandler().clearAllKeys();
        if(currentTarget == null) {
            if(scan())
                logDirect("Target block: " + currentTarget.toString());
            else {
                logDirect("Lighting done... probably...");
                active = false;
            }
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        if(ctx.player().onGround)
        {
            BlockPos bottom = currentTarget.down();
            Optional<Rotation> rot = RotationUtils.reachable(ctx, bottom);
            if(rot.isPresent()) {
                RayTraceResult result = RayTraceUtils.rayTraceTowards(ctx.player(), rot.get(), Baritone.settings().blockReachDistance.value, true);
                if(result != null && baritone.getInventoryBehavior().throwaway(true, this::isLightSource) && isSafeToCancel) {
                    baritone.getLookBehavior().updateTarget(rot.get(), true);
                    if (result.sideHit == EnumFacing.UP) {
                        if (ctx.isLookingAt(bottom)) {
                            baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
                            if (ctx.player().isSneaking()) {
                                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                                currentTarget = null;
                            }
                        }
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                }
            }
        }

        if(calcFailed) {
            logDirect("BlackListing: "+currentTarget+"... -_-");
            blackListed.add(currentTarget);
            if(!scan()) {
                onLostControl();
                logDirect("Mob proofing has failed");
            }

            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        return new PathingCommand(new GoalBlock(currentTarget), PathingCommandType.REVALIDATE_GOAL_AND_PATH);
    }

    public boolean isLightSource(ItemStack item)
    {
        return Block.getBlockFromItem(item.getItem()).getDefaultState().getLightValue() > 13;
    }

    public boolean canMobSpawnAt(BlockPos pos, World world)
    {
        if(!world.isAirBlock(pos))
            return false;
        if (world.getLightFor(EnumSkyBlock.BLOCK, pos) >= 7)
            return false;
        BlockPos down = pos.down();
        IBlockState downState = world.getBlockState(down);
        if(!downState.isBlockNormalCube() || downState.getBlock() == Blocks.AIR || downState.getBlock() == Blocks.LEAVES)
            return false;
        if(world.isBlockFullCube(pos.up()))
            return false;
        return true;
    }

    public boolean scan()
    {
        BetterBlockPos feet = ctx.playerFeet();
        double halfRange = range*0.5;
        for (int i = 0; i <= Math.max(256, range); ++i) {
            int max = (int)Math.min(range, i);
            for (int x = -max; x <= max; ++x) {
                for (int z = -max; z <= max; ++z) {
                    for (int y = -i; y <= i; ++y) {
                        double temp = y + feet.y;
                        if (temp < 0 || temp > 256)
                            continue;
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockPos relativeToPlayer = new BlockPos(pos.getX() + feet.x, temp, pos.getZ() + feet.z);
                        if(baritone.getHomeAreaSelectionManager().selectionContainsPoint(relativeToPlayer))
                            continue;

                        double xx = relativeToPlayer.getX() - origin.x;
                        double zz = relativeToPlayer.getZ() - origin.z;
                        if (xx * xx + zz * zz > halfRange * halfRange)
                            continue;
                        if (canMobSpawnAt(relativeToPlayer, ctx.world()) && !blackListed.contains(relativeToPlayer)) {
                            currentTarget = relativeToPlayer;
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }


    @Override
    public boolean isTemporary()
    {
        return false;
    }

    @Override
    public void onLostControl()
    {
        active = false;
    }

    @Override
    public String displayName0()
    {
        return "MobProofProcess";
    }

    public void mobProof(BetterBlockPos origin, int range)
    {
        active = true;
        this.blackListed = null;
        this.currentTarget = null;
        this.range = range;
        this.origin = new BetterBlockPos(Minecraft.getMinecraft().getRenderViewEntity().getPosition());
    }
}
