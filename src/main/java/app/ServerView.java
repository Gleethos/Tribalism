package app;

import javax.swing.*;

import java.awt.*;

import static swingtree.UI.*;

public class ServerView extends JPanel
{
    ServerView(ServerViewModel vm) {
        of(this).withLayout(FILL.and(WRAP(1)))
        .add(GROW,
            panel("alignx center, aligny center, wrap 4")
            .withPrefSize(625, 300)
            .add(
                label("Port:")
            )
            .add(
                textField(vm.port())
                .withBackground(vm.portIsValid().viewAs(Color.class,
                    isValid -> isValid ? Color.WHITE : new Color(255, 102, 102))
                )
            )
            .add(button(vm.buttonText()).onClick( it -> vm.buttonClicked() ))
            .add(label(vm.status().viewAsString()))
            .add("alignx center, wrap",
                label(vm.statusText())
                .withForeground(vm.portIsValid().viewAs(Color.class,
                    isValid -> isValid ? Color.BLACK : new Color(255, 102, 102))
                )
            )
            .add(GROW.and(SPAN),
                panel("fill, alignx center, aligny center, wrap 1")
                .add(label("Web portal location:"))
                .add(GROW_X,
                    textField(vm.webPortalLocation())
                    .withBackground(Color.WHITE)
                )
            )
        )
        .add("span, alignx center, aligny top",
            button("Open web portal").isEnabledIf(vm.status().viewAs(Boolean.class, it -> it == ServerViewModel.Status.ONLINE))
            .onClick( it -> vm.openWebPortal() )
        );
    }

}
