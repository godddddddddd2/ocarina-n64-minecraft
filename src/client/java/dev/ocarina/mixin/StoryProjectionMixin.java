package dev.ocarina.mixin;
import dev.ocarina.StoryProjection;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
@Mixin(GameRenderer.class)
public abstract class StoryProjectionMixin {
 @ModifyArg(method="renderLevel",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/PerspectiveProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),index=0)
 private Matrix4f ocarina$captureWorldProjection(Matrix4f projection){StoryProjection.capture(projection);return projection;}
}
