package net.drgmes.dwm.blocks.tardis.consoleunits.tardisconsoleunitimperial;

import net.drgmes.dwm.blocks.tardis.consoleunits.BaseTardisConsoleUnitBlockBuilder;
import net.minecraft.data.server.recipe.RecipeExporter;
import net.minecraft.data.server.recipe.RecipeProvider;
import net.minecraft.data.server.recipe.ShapedRecipeJsonBuilder;
import net.minecraft.item.Items;
import net.minecraft.recipe.book.RecipeCategory;

public class TardisConsoleUnitImperialBlockBuilder extends BaseTardisConsoleUnitBlockBuilder {
    public TardisConsoleUnitImperialBlockBuilder(String name) {
        super(name, () -> new TardisConsoleUnitImperialBlock(getBlockSettings()));
    }

    @Override
    public void registerRecipe(RecipeExporter exporter) {
        ShapedRecipeJsonBuilder.create(RecipeCategory.MISC, this.getBlock())
            .input('g', Items.GOLD_INGOT)
            .input('d', Items.DIAMOND)
            .input('q', Items.QUARTZ)
            .input('n', Items.NETHERITE_INGOT)
            .input('a', Items.AMETHYST_SHARD)
            .input('c', Items.COMPARATOR)
            .pattern("gdg")
            .pattern("qnq")
            .pattern("aca")
            .criterion("has_item", RecipeProvider.conditionsFromItem(Items.NETHERITE_INGOT))
            .offerTo(exporter);
    }
}
