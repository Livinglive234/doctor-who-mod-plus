package net.drgmes.dwm.blocks.tardis.consoleunits.tardisconsoleunittoyota;

import net.drgmes.dwm.blocks.tardis.consoleunits.BaseTardisConsoleUnitBlockBuilder;
import net.minecraft.data.server.recipe.RecipeExporter;
import net.minecraft.data.server.recipe.RecipeProvider;
import net.minecraft.data.server.recipe.ShapedRecipeJsonBuilder;
import net.minecraft.item.Items;
import net.minecraft.recipe.book.RecipeCategory;

public class TardisConsoleUnitToyotaBlockBuilder extends BaseTardisConsoleUnitBlockBuilder {
    public TardisConsoleUnitToyotaBlockBuilder(String name) {
        super(name, () -> new TardisConsoleUnitToyotaBlock(getBlockSettings()));
    }

    @Override
    public void registerRecipe(RecipeExporter exporter) {
        ShapedRecipeJsonBuilder.create(RecipeCategory.MISC, this.getBlock())
            .input('i', Items.IRON_INGOT)
            .input('d', Items.DIAMOND)
            .input('p', Items.PRISMARINE_SHARD)
            .input('n', Items.NETHERITE_INGOT)
            .input('r', Items.REDSTONE)
            .input('c', Items.COMPARATOR)
            .pattern("idi")
            .pattern("pnp")
            .pattern("rcr")
            .criterion("has_item", RecipeProvider.conditionsFromItem(Items.NETHERITE_INGOT))
            .offerTo(exporter);
    }
}
