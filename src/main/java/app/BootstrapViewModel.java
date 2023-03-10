package app;

import sprouts.Var;

import javax.swing.*;

public class BootstrapViewModel
{
    public enum Stage { SKILL_TYPES, ABILITIES, DONE }

    private final AppContext context;

    private final Var<Stage> stage = Var.of(Stage.SKILL_TYPES);
    private final SkillTypesViewModel skillTypesViewModel;
    private final AbilityTypesViewModel abilityTypesViewModel;



    public BootstrapViewModel(AppContext context) {
        this.context = context;
        this.skillTypesViewModel = new SkillTypesViewModel(context);
        this.abilityTypesViewModel = new AbilityTypesViewModel(context);
    }

    public Var<Stage> stage() { return stage; }

    public void nextFrom(Stage clickedStage) {
        //if ( clickedStage == Stage.DONE ) return;
        //int prevStageIndex = clickedStage.ordinal() - 1;
        //if (prevStageIndex < 0) return;
        //Stage prevStage = Stage.values()[prevStageIndex];
        //if (stage.get() != prevStage) return;
        //int nextStageIndex = clickedStage.ordinal() + 1;
        //if (nextStageIndex > Stage.values().length - 1) return;
        stage.set(clickedStage);
    }

    public void saveToWorkingDir() {
        context.modelTypes().saveToWorkingDirectory(context.db());
    }

    public JComponent createView() { return new BootstrapView(this); }

    public SkillTypesViewModel skillTypesViewModel() {
        return skillTypesViewModel;
    }

    public AbilityTypesViewModel abilityTypesViewModel() {
        return abilityTypesViewModel;
    }
}
