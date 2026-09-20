package net.drgmes.dwm.blocks.tardis.engines;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.datagen.BlockModelDataBuilder;
import net.drgmes.dwm.datagen.ItemModelDataBuilder;
import net.drgmes.dwm.utils.builders.BlockBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.data.client.TextureKey;

import java.util.function.Supplier;

public abstract class BaseTardisEngineBlockBuilder extends BlockBuilder {
    public BaseTardisEngineBlockBuilder(String name, Supplier<BaseTardisEngineBlock> blockSupplier) {
        super(name, blockSupplier::get);
    }

    public static AbstractBlock.Settings getBlockSettings() {
        // -1.0F hardness makes it unbreakable by survival players (mining progress never completes, same trick vanilla uses for bedrock/command blocks);
        // it can still be broken instantly in creative mode, and the huge resistance also makes it immune to explosions.
        return BlockBuilder.getBlockSettings().nonOpaque().strength(-1.0F, 3600000.0F);
    }

    @Override
    public BlockModelDataBuilder getBlockModelDataBuilder() {
        return new BlockModelDataBuilder(this, BlockModelDataBuilder.BlockType.SIMPLE)
            .addBlockTexture(TextureKey.ALL, DWM.MODELS.BLOCK_INVISIBLE);
    }

    @Override
    public ItemModelDataBuilder getItemModelDataBuilder() {
        return new ItemModelDataBuilder(this.getBlockItem(), this.getId(), DWM.getIdentifier("item/block/tardis/engines/" + this.getName()), ItemModelDataBuilder.ItemType.PARENTED);
    }
}
