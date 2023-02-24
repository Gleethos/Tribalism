package app;

import javax.swing.*;

public class BootstrapViewModel
{
    private final AppContext context;
    private final SkillTypesViewModel skillTypesViewModel;


    public BootstrapViewModel(AppContext context) {
        this.context = context;
        this.skillTypesViewModel = new SkillTypesViewModel(context);
    }

    public JComponent createView() { return new BootstrapView(this); }

    public SkillTypesViewModel skillTypesViewModel() {
        return skillTypesViewModel;
    }
}
