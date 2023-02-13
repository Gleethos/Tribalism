package app;

import javax.swing.*;

import static swingtree.UI.*;

/**
 *  The desktop view of the user page, which is available after login.
 */
public class UserView extends JPanel
{
    public UserView(UserViewModel vm) {
        of(this).withLayout(FILL)
        .add(GROW,
             panel("alignx center, aligny center, wrap 2")
            .withPreferredSize(625, 300)
            .add(GROW,
                panel(FILL_X.and(WRAP(2)), "[grow][shrink]")
                .add(label("Username"))
                .add(GROW_X,
                    textField(vm.username())
                )
                .add(label("Password"))
                .add(GROW_X,
                    passwordField("")
                )
            )
            .add(GROW_X,
                panel(FILL_X.and(WRAP(1)))
                .add(GROW_X,
                    button("Do something")
                )
            )
            .add(GROW_X.and(SPAN),
                panel(FILL_X.and(WRAP(1)))
                .add(GROW_X,
                    label("Welcome to you user page!")
                )
            )
            .add(GROW_X.and(SPAN),
                panel(FILL_X.and(WRAP(2)))
                .add(label("How are you doing?"))
            )
        );
    }
}
