package app.dev;

import app.AppContext;
import app.models.AbilityType;
import sprouts.Vals;
import sprouts.Var;
import sprouts.Vars;
import swingtree.UI;
import swingtree.api.mvvm.ViewableEntry;

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
        searchKey.onAct( it -> {
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

    public void deleteAbilityType(AbilityType abilityType) {
        abilityTypes.removeIfItem(vm -> vm.abilityType() == abilityType );
        appContext.db().delete(abilityType);
    }

    JComponent createView() { return new AbilityTypesView(this); }


    private static class AbilityTypeViewModel implements ViewableEntry
    {
        private final AbilityTypesViewModel parent;
        private final AbilityType abilityType;
        private final Var<Boolean> selected = Var.of(false);
        private final Var<Integer> position = Var.of(0);

        private Object view = null;

        public AbilityTypeViewModel(AbilityTypesViewModel parent, AbilityType abilityType) {
            this.parent = parent;
            this.abilityType = abilityType;
        }

        public AbilityType abilityType() { return abilityType; }

        public void delete() {parent.deleteAbilityType(abilityType);}

        @Override public Var<Boolean> isSelected() { return selected; }

        @Override public Var<Integer> position() { return position; }

        @Override
        public <V> V createView(Class<V> viewType) {

            if ( this.view != null ) return viewType.cast(view);

            view = UI.panel(UI.FILL.and(UI.INS(12)))
                    .add(UI.WIDTH(90,120,220), UI.textField(abilityType.name()))
                    .add(UI.SHRINK, UI.label("Description:"))
                    .add(UI.GROW.and(UI.PUSH), UI.textField(abilityType.description()))
                    .add(UI.SHRINK, UI.button("Delete").onClick(it2 -> delete()))
                    .add(UI.GROW.and(UI.WRAP).and(UI.SPAN), UI.separator())
                    .getComponent();

            return viewType.cast(view);
        }
    }

}
