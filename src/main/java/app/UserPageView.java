package app;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;

import static swingtree.UI.*;

public class UserPageView extends JPanel
{

    public UserPageView(UserPageViewModel vm) {
        FlatLightLaf.setup();
        of(this).withLayout(FILL.and(WRAP(2)))
            .withPreferredSize(625, 300)
            .add(GROW_X,
                panel(FILL_X.and(WRAP(2)), "[shrink][grow]")
                .add(label("Username"))
                .add(GROW_X,
                    textField(vm.username())
                )
                .add(label("Email"))
                .add(GROW_X,
                    textField(vm.email())
                )
            )
            .add(GROW_X.and(PUSH_X),
                panel(FILL_X.and(WRAP(2)), "[shrink][grow]")
                .add(label("Location"))
                .add(GROW_X,
                    textField(vm.location())
                )
                .add(label("Website"))
                .add(GROW_X,
                    textField(vm.website())
                )
            )
            .add(GROW.and(PUSH).and(SPAN),
                panel(FILL.and(WRAP(1)))
                .add(GROW_X,
                    label("Bio")
                )
                .add(GROW.and(PUSH),
                    textArea(vm.bio())
                )
            )
            .add(GROW_X.and(SPAN),
                panel(FILL_X.and(WRAP(1)))
                .add(GROW_X,
                    button("Save")
                    .onClick( it -> vm.save() )
                )
                .add(GROW_X,
                    label(vm.feedback())
                )
            );
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("User Page");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new UserPageView(new UserPageViewModel()));
        frame.pack();
        frame.setVisible(true);
    }

}
