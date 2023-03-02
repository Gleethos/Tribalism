package app;

import javax.swing.*;

import java.awt.*;

import static swingtree.UI.*;

public class RegisterView extends JPanel
{
    public RegisterView( RegisterViewModel vm ) {
        of(this).withLayout(FILL)
        .add(GROW,
            panel("alignx center, aligny center, wrap 1")
            .withPrefSize(625, 300)
            .add(GROW,
                panel(FILL_X.and(WRAP(2)), "[shrink][grow]")
                .add(label("Username"))
                .add(GROW_X,
                    textField(vm.username()).isEnabledIfNot(vm.allInputsDisabled())
                    .withBackground(vm.usernameBackgroundColor())
                )
                .add(label("Password"))
                .add(GROW_X,
                    passwordField(vm.password()).isEnabledIfNot(vm.allInputsDisabled())
                    .withBackground(vm.passwordBackgroundColor())
                )
            )
            .add(GROW_X,
                panel(FILL_X.and(WRAP(1)))
                .add(GROW_X,
                    button("Register").isEnabledIfNot(vm.allInputsDisabled())
                    .onClick( it -> vm.register() )
                )
            )
            .add(GROW_X.and(SPAN),
                panel(FILL_X.and(WRAP(1)))
                .add(GROW_X,
                    label(vm.feedback().view( f -> String.format("<html><div WIDTH=%d>%s</div></html>", 475, f) ))
                    .withForeground(vm.feedbackColor())
                )
            )
            .add(GROW_X.and(SPAN),
                panel(FILL_X.and(WRAP(2)))
                .add(label("Already have an account?"))
                .add(button("Switch to Login").onClick( it -> vm.switchToLogin() ))
            )
        );
    }

}
