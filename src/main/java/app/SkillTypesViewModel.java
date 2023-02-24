package app;

import app.models.AbilityType;
import app.models.SkillType;
import sprouts.Vals;
import sprouts.Var;
import sprouts.Vars;

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
    private final Vars<SkillType> skillTypes = Vars.of(app.models.SkillType.class);
    private final Var<String> searchKey = Var.of("");

    public SkillTypesViewModel(AppContext appContext) {
        this.appContext = appContext;
        skillTypes.addAll(appContext.db().selectAll(app.models.SkillType.class));
        searchKey.onAct( it -> {
            skillTypes.clear();
            skillTypes.addAll(
                        appContext.db()
                            .select(app.models.SkillType.class)
                            .where(SkillType::name)
                            .like("%" + it.get() + "%")
                            .asList()
                    );
        });
    }

    public Vars<app.models.SkillType> skillTypes() {
        return skillTypes;
    }

    public Vals<String> abilityTypes() {
        List<String> found = appContext.db().selectAll(AbilityType.class).stream().map(at->at.name().get()).toList();
        return Vars.of(String.class).addAll(found);
    }

    public Var<String> searchKey() { return searchKey; }

    public void addNewSkillType() {
        skillTypes.add(appContext.db().create(app.models.SkillType.class));
    }

    public void deleteSkillType(app.models.SkillType skillType) {
        skillTypes.remove(skillType);
        appContext.db().delete(skillType);
    }

    JComponent createView() { return new SkillTypesView(this); }

}
