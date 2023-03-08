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
                panel("fill, alignx center, aligny center, wrap 1")
                .add(label("Types:"))
                .add(GROW_X,
                    tabbedPane()
                    .add(tab("Skill Types").add(vm.skillTypesViewModel().createView()))
                    .add(tab("Abilities"))
                )
            )
        );
    }
}
