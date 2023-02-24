package app;

import app.models.SkillType;
import sprouts.Var;
import sprouts.Vars;
import swingtree.SimpleDelegate;

import javax.swing.*;
import java.awt.event.ActionEvent;

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
    private final Var<String> nsearchKey = Var.of("");

    public SkillTypesViewModel(AppContext appContext) {
        this.appContext = appContext;
        skillTypes.addAll(appContext.db().selectAll(app.models.SkillType.class));
    }

    public Vars<app.models.SkillType> skillTypes() { return skillTypes; }

    public Var<String> searchKey() { return nsearchKey; }

    public void addNewSkillType() {
        skillTypes.add(appContext.db().create(app.models.SkillType.class));
    }

    public void deleteSkillType(app.models.SkillType skillType) {
        skillTypes.remove(skillType);
        appContext.db().delete(skillType);
    }
}
