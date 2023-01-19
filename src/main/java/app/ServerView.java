package app;

import javax.swing.*;

import java.awt.*;

import static swingtree.UI.*;

public class ServerView extends JPanel
{
    ServerView(ServerViewModel vm) {
        of(this).withLayout(FILL)
        .add(GROW,
            panel("alignx center, aligny center, wrap 3")
            .withPreferredSize(625, 300)
            .add(
                textField(vm.port())
                .withBackground(vm.portIsValid().viewAs(Color.class,
                    isValid -> isValid ? Color.WHITE : new Color(255, 102, 102))
                )
            )
            .add(button(vm.buttonText()).onClick( it -> vm.buttonClicked() ))
            .add(label(vm.status().viewAsString()))
            .add("span, alignx center",
                label(vm.statusText())
                .withForeground(vm.portIsValid().viewAs(Color.class,
                    isValid -> isValid ? Color.BLACK : new Color(255, 102, 102))
                )
            )
        );
    }

}
