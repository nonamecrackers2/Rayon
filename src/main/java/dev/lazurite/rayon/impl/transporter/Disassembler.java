package dev.lazurite.rayon.impl.transporter;

import dev.lazurite.rayon.impl.mixin.client.ItemRendererAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

@Environment(EnvType.CLIENT)
public interface Disassembler {
    static Pattern getEntityPattern(Entity entity) {
        Pattern pattern = new Pattern();
        MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(entity)
                .render(entity, 0, 0, new MatrixStack(), pattern.asProvider(), 0);
        return pattern;
    }

    static Pattern getEntityPattern(EntityModel<?> model) {
        Pattern pattern = new Pattern();
        model.render(new MatrixStack(), pattern, 0, 0, 1.0f, 1.0f, 1.0f, 1.0f);
        return pattern;
    }

    static Pattern getItemPattern(ItemStack itemStack) {
        Pattern pattern = new Pattern();
        MinecraftClient.getInstance().getItemRenderer()
                .renderItem(itemStack, ModelTransformation.Mode.GROUND, 0, 0, new MatrixStack(), pattern.asProvider());
        pattern.getQuads().remove(pattern.getQuads().get(5));
        return pattern;
    }

    static Pattern getItemPattern(BakedModel model) {
        Pattern pattern = new Pattern();
        ((ItemRendererAccess) MinecraftClient.getInstance().getItemRenderer())
                .invokeRenderBakedItemModel(model, new ItemStack(Items.AIR), 0, 0, new MatrixStack(), pattern);
        return pattern;
    }

    static Pattern getBlockPattern(BlockState blockState, World world) {
        Pattern pattern = new Pattern();
        MinecraftClient.getInstance().getBlockRenderManager()
                .renderBlock(blockState, new BlockPos(0, 0, 0), world, new MatrixStack(), pattern, false, new Random());
        return pattern;
    }
}
