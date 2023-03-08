package app;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;

import static swingtree.UI.*;

public class LoginView extends JPanel
{
    /*
        The login view is very similar to the one
        our users know from the web view, it is
        one big panel with a small box in the middle of the screen.
    */
    public LoginView(LoginViewModel vm) {
        of(this).withLayout(FILL)
        .add(GROW,
             panel("alignx center, aligny center, wrap 2")
            .withPrefSize(625, 300)
            .add(GROW,
                panel(FILL_X.and(WRAP(2)), "[grow][shrink]")
                .add(label("Username"))
                .add(GROW_X,
                    textField(vm.username()).isEnabledIf(vm.textFieldsEnabled())
                    .withBackground(vm.usernameBackgroundColor())
                )
                .add(label("Password"))
                .add(GROW_X,
                    passwordField(vm.password()).isEnabledIf(vm.textFieldsEnabled())
                    .withBackground(vm.passwordBackgroundColor())
                )
            )
            .add(GROW_X,
                panel(FILL_X.and(WRAP(1)))
                .add(GROW_X,
                    button("Login").isEnabledIf(vm.loginButtonEnabled())
                    .onClick( it -> vm.login() )
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
                .add(label("Don't have an account?"))
                .add(button("Switch to Register").onClick( it -> vm.switchToRegister() ))
            )
        );
    }


    // For testing:
    public static void main(String[] args) {
        FlatLightLaf.setup();
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new LoginView(new LoginViewModel(null, null)));
        frame.pack();
        frame.setVisible(true);
    }

}
