package net.drgmes.dwm.blocks.tardis.exteriors;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.datagen.BlockLootDataBuilder;
import net.drgmes.dwm.datagen.BlockModelDataBuilder;
import net.drgmes.dwm.datagen.ItemModelDataBuilder;
import net.drgmes.dwm.utils.builders.BlockBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.data.client.TextureKey;
import net.minecraft.registry.tag.BlockTags;

import java.util.function.Supplier;

public abstract class BaseTardisExteriorBlockBuilder extends BlockBuilder {
    public BaseTardisExteriorBlockBuilder(String name, Supplier<BaseTardisExteriorBlock<?>> blockSupplier) {
        super(name, blockSupplier::get);
    }

    public static AbstractBlock.Settings getBlockSettings() {
        // nonOpaque(): the "is this a solid opaque cube" flag Minecraft caches per blockstate is computed once at
        // registration from these settings, separately from the live getOutlineShape/getCollisionShape we already
        // go empty on when cloaked - without this, neighbor blocks would still have their touching face culled
        // (light-blocking, face-culling) as if against a full block even while cloaked and walk-through.
        return AbstractBlock.Settings.copy(Blocks.BEDROCK).nonOpaque().luminance((blockState) -> (
            blockState.get(BaseTardisExteriorBlock.HALF) == DoubleBlockHalf.UPPER && blockState.get(BaseTardisExteriorBlock.LIT) ? 15 : 0
        ));
    }

    @Override
    public BlockLootDataBuilder getBlockLootDataBuilder() {
        return null;
    }

    @Override
    public BlockModelDataBuilder getBlockModelDataBuilder() {
        return new BlockModelDataBuilder(this, BlockModelDataBuilder.BlockType.SIMPLE)
            .addBlockTexture(TextureKey.ALL, DWM.MODELS.BLOCK_INVISIBLE);
    }

    @Override
    public ItemModelDataBuilder getItemModelDataBuilder() {
        return new ItemModelDataBuilder(this.getBlockItem(), this.getId(), DWM.getIdentifier("item/block/tardis/exteriors/" + this.getName()), ItemModelDataBuilder.ItemType.PARENTED);
    }

    @Override
    public void registerTags() {
        super.registerTags();
        this.tags.add(BlockTags.DRAGON_IMMUNE);
    }
}
