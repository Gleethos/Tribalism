package app.dev;

import app.AppContext;
import app.models.AbilityType;
import app.models.Role;
import app.models.SkillType;
import sprouts.Vals;
import sprouts.Var;
import sprouts.Vars;
import swingtree.UI;
import swingtree.api.mvvm.ViewableEntry;

import javax.swing.*;
import java.util.List;

/**
 *  This is the view model for the {@link Role} model, which is used to represent
 *  the different types of skills that a character can have.
 *  <p>
 *      This view model is used to create, edit, and delete skill types
 *      during the game master's setup phase.
 */
public class RoleTypesViewModel
{
    private final AppContext appContext;

    private final Vars<RoleTypeViewModel> Roles = Vars.of(RoleTypeViewModel.class);
    private final Var<String> searchKey = Var.of("");
    private final Var<String> newRoleName = Var.of("");


    public RoleTypesViewModel(AppContext appContext) {
        this.appContext = appContext;
        var asModels  = appContext.db().selectAll(Role.class)
                                        .stream()
                                        .map(st -> new RoleTypeViewModel(this, st))
                                        .toList();
        Roles.addAll(asModels);
        searchKey.onAct( it -> {
            Roles.clear();
            Roles.addAll(
                    appContext.db()
                        .select(Role.class)
                        .where(Role::name)
                        .like("%" + it.get() + "%")
                        .asList()
                        .stream()
                        .map(st -> new RoleTypeViewModel(this, st))
                        .toList()
                );
        });
    }

    public Vars<RoleTypeViewModel> roleTypes() {
        return Roles;
    }

    public Var<String> searchKey() { return searchKey; }

    public Var<String> newRoleName() { return newRoleName; }

    public void addNewRole() {
        var newRole = appContext.db().create(Role.class);
        newRole.name().set(newRoleName.get());
        var vm = new RoleTypeViewModel(this, newRole);
        Roles.add(vm);
    }

    public void deleteRole(Role Role) {
        Roles.removeIfItem( vm -> vm.Role() == Role );
        appContext.db().delete(Role);
    }

    JComponent createView() { return new RoleTypesView(this); }


    private static class RoleTypeViewModel implements ViewableEntry
    {
        private final RoleTypesViewModel parent;
        private final Role role;
        private final Var<Boolean> selected = Var.of(false);
        private final Var<Integer> position = Var.of(0);
        private final Vars<SkillViewModel> skillViewModels = Vars.of(SkillViewModel.class);

        private Object view = null;

        public RoleTypeViewModel(RoleTypesViewModel parent, Role Role) {
            this.parent = parent;
            this.role = Role;
            var skillVMList  = Role.skills()
                                .stream()
                                .map(SkillViewModel::new)
                                .toList();

            skillViewModels.addAll(skillVMList);
        }

        public Role Role() { return role; }

        public void delete() {parent.deleteRole(role);}

        @Override public Var<Boolean> isSelected() { return selected; }

        @Override public Var<Integer> position() { return position; }

        @Override
        public <V> V createView(Class<V> viewType) {
            if ( this.view != null ) return viewType.cast(view);

            view = UI.panel(UI.FILL.and(UI.INS(12)))
                    .add(UI.WIDTH(90,120,220), UI.textField(role.name()))
                    .add(UI.SHRINK, UI.label("Description:"))
                    .add(UI.GROW.and(UI.PUSH), UI.textField(role.description()))
                    .add(UI.SHRINK.and(UI.WRAP), UI.button("Delete").onClick(it2 -> delete()))
                    .add(UI.GROW.and(UI.WRAP).and(UI.SPAN), UI.scrollPanels().add(skillViewModels))
                    .add(UI.GROW.and(UI.WRAP).and(UI.SPAN), UI.separator())
                    .getComponent();

            return viewType.cast(view);
        }
    }


    private static class SkillViewModel implements ViewableEntry
    {
        private final app.models.Skill skill;
        private final Var<Boolean> selected = Var.of(false);
        private final Var<Integer> position = Var.of(0);

        private Object view = null;

        public SkillViewModel( app.models.Skill skill ) {
            this.skill = skill;
        }

        public app.models.Skill skill() { return skill; }

        @Override public Var<Boolean> isSelected() { return selected; }

        @Override public Var<Integer> position() { return position; }

        @Override
        public <V> V createView(Class<V> viewType) {

            if ( this.view != null ) return viewType.cast(view);

            SkillType type = skill.type().get();
            view = UI.panel(UI.FILL.and(UI.INS(12)))
                    .add(UI.GROW, UI.textField(type.name()))
                    .add(UI.GROW, UI.label(type.primaryAbility()))
                    .add(UI.GROW, UI.label(type.secondaryAbility()))
                    .add(UI.GROW, UI.label(type.tertiaryAbility()))
                    .add(UI.SHRINK, UI.checkBox("Proficient:", skill.isProficient()))
                    .add(UI.SHRINK, UI.label("Learnability:"))
                    .add(UI.SHRINK, UI.spinner(skill.learnability()).withStepSize(0.5))
                    .add(UI.SHRINK, UI.label("Level:"))
                    .add(UI.SHRINK.and(UI.WRAP), UI.spinner(skill.level()))
                    .add(UI.SHRINK, UI.label("Description:"))
                    .add(UI.GROW.and(UI.WRAP).and(UI.SPAN), UI.textField(type.description()))
                    .add(UI.GROW.and(UI.WRAP).and(UI.SPAN), UI.separator())
                    .getComponent();

            return viewType.cast(view);
        }
    }


}
