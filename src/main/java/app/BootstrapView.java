package app;

import javax.swing.*;

import static swingtree.UI.*;

public class BootstrapView extends JPanel
{
    public BootstrapView(BootstrapViewModel vm)
    {
        of(this).withLayout(FILL.and(WRAP(1)))
        .add(GROW,
            panel("alignx center, aligny center, wrap 4")
            .withPrefSize(625, 300)
            .add(GROW.and(SPAN),
                panel("fill, alignx center, aligny center")
                .add(WRAP.and(ALIGN_RIGHT),
                    button("Save To Working Directory")
                    .onMouseClick( it -> vm.saveToWorkingDir() )
                )
                .add(SPAN, separator())
                .add(GROW_X.and(SPAN),
                    tabbedPane()
                    .add(
                        tab("Abilities")
                        .onMouseClick( it -> vm.nextFrom(BootstrapViewModel.Stage.ABILITIES))
                        .isEnabledIf(BootstrapViewModel.Stage.ABILITIES, vm.stage())
                        .isSelectedIf(BootstrapViewModel.Stage.ABILITIES, vm.stage())
                        .add(vm.abilityTypesViewModel().createView())
                    )
                    .add(
                        tab("Skill Types")
                        .onMouseClick( it -> vm.nextFrom(BootstrapViewModel.Stage.SKILL_TYPES))
                        .isEnabledIf(BootstrapViewModel.Stage.SKILL_TYPES, vm.stage())
                        .isSelectedIf(BootstrapViewModel.Stage.SKILL_TYPES, vm.stage())
                        .add(vm.skillTypesViewModel().createView())
                    )
                )
            )
        );
    }
}
