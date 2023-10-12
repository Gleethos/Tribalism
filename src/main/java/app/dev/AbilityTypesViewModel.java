package app.dev;

import app.AppContext;
import app.common.StickyRef;
import app.models.AbilityType;
import sprouts.From;
import sprouts.Vals;
import sprouts.Var;
import sprouts.Vars;
import swingtree.UI;
import swingtree.api.mvvm.EntryViewModel;

import javax.swing.*;
import java.util.List;

/**
 *  This is the view model for the {@link AbilityType} model, which is used to represent
 *  the different types of abilities that a character can have.
 *  <p>
 *      This view model is used to create, edit, and delete ability types
 *      during the game master's setup phase.
 */
public class AbilityTypesViewModel
{
    private final AppContext appContext;

    private final Vars<AbilityTypeViewModel> abilityTypes = Vars.of(AbilityTypeViewModel.class);
    private final Var<String> searchKey = Var.of("");
    private final Var<String> newAbilityTypeName = Var.of("");


    public AbilityTypesViewModel(AppContext appContext) {
        this.appContext = appContext;
        var asModels  = appContext.db().selectAll(AbilityType.class)
                                        .stream()
                                        .map(st -> new AbilityTypeViewModel(this, st))
                                        .toList();
        abilityTypes.addAll(asModels);
        searchKey.onChange(From.VIEW, it -> {
            abilityTypes.clear();
            abilityTypes.addAll(
                        appContext.db()
                            .select(AbilityType.class)
                            .where(AbilityType::name)
                            .like("%" + it.get() + "%")
                            .asList()
                            .stream()
                            .map(st -> new AbilityTypeViewModel(this, st))
                            .toList()
                    );
        });
    }

    public Vars<AbilityTypeViewModel> skillTypes() {
        return abilityTypes;
    }

    public Vals<String> abilityTypes() {
        List<String> found = appContext.db().selectAll(AbilityType.class).stream().map(at->at.name().get()).toList();
        return Vars.of(String.class).addAll(found);
    }

    public Var<String> searchKey() { return searchKey; }

    public Var<String> newAbilityTypeName() { return newAbilityTypeName; }

    public void addNewAbilityType() {
        var newAbilityType = appContext.db().create(AbilityType.class);
        newAbilityType.name().set(newAbilityTypeName.get());
        var vm = new AbilityTypeViewModel(this, newAbilityType);
        abilityTypes.add(vm);
    }

    public Confirmation deleteAbilityType(AbilityType abilityType) {
        return new Confirmation() {
            @Override public String title() { return "Delete Ability Type"; }
            @Override public String question() {
                return "Are you sure you want to delete the ability type: " + abilityType.name().get() + "?" +
                       "This will also delete all abilities associated with this type!";
            }
            @Override
            public void yes() {
                abilityTypes.removeIfItem(vm -> vm.abilityType() == abilityType );
                var db = appContext.db();
                var foundAbilities = db.select(app.models.Ability.class)
                                           .where(app.models.Ability::type)
                                           .is(abilityType)
                                           .asList();
                db.delete(abilityType);
                db.delete(foundAbilities);
            }
        };
    }

    JComponent createView() { return new AbilityTypesView(this); }


    public static class AbilityTypeViewModel implements EntryViewModel
    {
        private final AbilityTypesViewModel parent;
        private final AbilityType abilityType;
        private final Var<Boolean> selected = Var.of(false);
        private final Var<Integer> position = Var.of(0);

        private final StickyRef viewCache = new StickyRef();

        public AbilityTypeViewModel(AbilityTypesViewModel parent, AbilityType abilityType) {
            this.parent = parent;
            this.abilityType = abilityType;
        }

        public StickyRef getViewCache() { return viewCache; }

        public AbilityType abilityType() { return abilityType; }

        public Confirmation delete() { return parent.deleteAbilityType(abilityType); }

        @Override public Var<Boolean> isSelected() { return selected; }

        @Override public Var<Integer> position() { return position; }
    }

}
