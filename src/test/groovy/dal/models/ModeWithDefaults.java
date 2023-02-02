package dal.models;

import dal.api.Model;
import sprouts.Var;

public interface ModeWithDefaults extends Model<ModeWithDefaults>
{
    interface Story extends Var<String> {}
    Story story();
    default boolean storyContains(String text) { return story().get().contains(text); }
}
