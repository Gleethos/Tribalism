package app;

import com.formdev.flatlaf.FlatLightLaf;
import swingtree.UI;

import javax.swing.*;

import static swingtree.UI.*;

/**
 *  When the application is started in the development mode, this view is shown
 *  under the "Content" tab which is where the user content of the application
 *  is shown!
 *  In the user mode (production mode), the content view is the root view of the
 *  application, all the development views are not shown.
 */
public class ContentView extends JPanel {

    public ContentView(ContentViewModel vm) {
        of(this).withLayout(FILL.and(WRAP(1)))
        .add(GROW.and(PUSH),
            vm.content(), content -> UI.of(content.createView(JComponent.class))
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
