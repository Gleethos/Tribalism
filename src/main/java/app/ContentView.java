package app;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;

import static swingtree.UI.*;

public class ContentView extends JPanel {

    public ContentView(ContentViewModel vm) {
        of(this).withLayout(FILL.and(WRAP(1)))
        .add(GROW.and(PUSH),
            vm.content()
        );
    }

    public static void main(String[] args) {
        FlatLightLaf.setup();
        JFrame frame = new JFrame("Main");
        frame.setContentPane(new ContentView(new ContentViewModel(new AppContext(null))));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }


}
