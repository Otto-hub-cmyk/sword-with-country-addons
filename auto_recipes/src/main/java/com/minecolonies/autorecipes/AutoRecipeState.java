package com.minecolonies.autorecipes;

import java.util.Set;

public interface AutoRecipeState {
    boolean autoRecipes$isDiscovered();

    void autoRecipes$setDiscovered(boolean discovered);

    Set<String> autoRecipes$getPersistedDoInputs();

    void autoRecipes$setPersistedDoInputs(Set<String> persistedDoInputs);
}
