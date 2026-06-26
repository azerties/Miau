package myau.mixin;

import myau.Myau;
import myau.module.modules.render.Xray;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(
    value = {BlockRendererDispatcher.class},
    priority = 9999)
public abstract class MixinBlockRendererDispatcher {
  @Inject(
      method = {"renderBlock"},
      at = {@At("HEAD")})
  private void renderBlock(
      IBlockState iBlockState,
      BlockPos blockPos,
      IBlockAccess iBlockAccess,
      WorldRenderer worldRenderer,
      CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
    // Safety: OptiFine chunk batching can pass null iBlockState
    if (iBlockState == null || blockPos == null) return;
    if (Myau.moduleManager == null) return;
    Xray xray = (Xray) Myau.moduleManager.modules.get(Xray.class);
    if (xray == null || !xray.isEnabled()) return;
    Block block = iBlockState.getBlock();
    if (block == null) return;
    if (xray.isXrayBlock(Block.getIdFromBlock(block))) {
      if (xray.checkBlock(blockPos)) {
        xray.trackedBlocks.add(new BlockPos(blockPos));
      } else {
        xray.trackedBlocks.remove(blockPos);
      }
    }
  }
}
