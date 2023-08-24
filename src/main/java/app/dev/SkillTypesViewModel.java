package app.dev;

import app.AppContext;
import app.common.StickyRef;
import app.models.AbilityType;
import app.models.SkillType;
import sprouts.Vals;
import sprouts.Var;
import sprouts.Vars;
import swingtree.UI;
import swingtree.UIForAnySwing;
import swingtree.api.mvvm.EntryViewModel;

import javax.swing.*;
import java.util.List;

/**
 *  This is the view model for the {@link app.models.SkillType} model, which is used to represent
 *  the different types of skills that a character can have.
 *  <p>
 *      This view model is used to create, edit, and delete skill types
 *      during the game master's setup phase.
 */
public class SkillTypesViewModel
{
    private final AppContext appContext;

    private final Vars<SkillTypeViewModel> skillTypes = Vars.of(SkillTypeViewModel.class);
    private final Var<String> searchKey = Var.of("");
    private final Var<String> newSkillTypeName = Var.of("");


    public SkillTypesViewModel(AppContext appContext) {
        this.appContext = appContext;
        var asModels  = appContext.db().selectAll(app.models.SkillType.class)
                                        .stream()
                                        .map(st -> new SkillTypeViewModel(this, st))
                                        .toList();
        skillTypes.addAll(asModels);
        searchKey.onAct( it -> {
            skillTypes.clear();
            skillTypes.addAll(
                        appContext.db()
                            .select(app.models.SkillType.class)
                            .where(SkillType::name)
                            .like("%" + it.get() + "%")
                            .asList()
                            .stream()
                            .map(st -> new SkillTypeViewModel(this, st))
                            .toList()
                    );
        });
    }

    public Vars<SkillTypeViewModel> skillTypes() {
        return skillTypes;
    }

    public Vals<String> abilityTypes() {
        List<String> found = appContext.db().selectAll(AbilityType.class).stream().map(at->at.name().get()).toList();
        return Vars.of(String.class).addAll(found);
    }

    public Var<String> searchKey() { return searchKey; }

    public Var<String> newSkillTypeName() { return newSkillTypeName; }

    public void addNewSkillType() {
        var newSkillType = appContext.db().create(app.models.SkillType.class);
        newSkillType.name().set(newSkillTypeName.get());
        var vm = new SkillTypeViewModel(this, newSkillType);
        skillTypes.add(vm);
    }

    public void deleteSkillType(app.models.SkillType skillType) {
        skillTypes.removeIfItem( vm -> vm.skillType() == skillType );
        appContext.db().delete(skillType);
    }

    JComponent createView() { return new SkillTypesView(this); }


    public static class SkillTypeViewModel implements EntryViewModel
    {
        private final SkillTypesViewModel parent;
        private final app.models.SkillType skillType;
        private final Var<Boolean> selected = Var.of(false);
        private final Var<Integer> position = Var.of(0);

        private final StickyRef viewCache = new StickyRef();

        public SkillTypeViewModel(SkillTypesViewModel parent, app.models.SkillType skillType) {
            this.parent = parent;
            this.skillType = skillType;
        }

        public app.models.SkillType skillType() { return skillType; }

        public void delete() {parent.deleteSkillType(skillType);}

        @Override public Var<Boolean> isSelected() { return selected; }

        @Override public Var<Integer> position() { return position; }


        public JComponent createView() {
            var abilities = parent.abilityTypes();
            return viewCache.get(()->
                    UI.panel(UI.FILL.and(UI.INS(12)))
                    .add(UI.GROW, UI.textField(skillType.name()))
                    .add(UI.GROW, UI.comboBox(skillType.primaryAbility(), abilities))
                    .add(UI.GROW, UI.comboBox(skillType.secondaryAbility(), abilities))
                    .add(UI.GROW, UI.comboBox(skillType.tertiaryAbility(), abilities))
                    .add(UI.SHRINK.and(UI.WRAP),
                        UI.button("Delete").onClick( it -> {
                            UI.run(()->{
                                boolean yes = UI.confirm("Delete Skill Type", "Are you sure you want to delete this skill type?");
                                if ( yes )
                                    delete();
                            });
                        })
                    )
                    .add(UI.SHRINK, UI.label("Description:"))
                    .add(UI.GROW.and(UI.WRAP).and(UI.SPAN), UI.textField(skillType.description()))
                    .add(UI.GROW.and(UI.WRAP).and(UI.SPAN), UI.separator())
                    .getComponent()
                );
        }
    }

}
