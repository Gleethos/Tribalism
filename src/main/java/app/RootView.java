package app;

import static swingtree.UI.*;

/**
 *  Basically a tabbed pane with a tab for different main views.
 *  The most important ones are the content view and the server view.
 */
public class RootView extends javax.swing.JPanel {

    public RootView(RootViewModel vm) {
        of(this).withLayout(FILL)
        .withPreferredSize(800, 600)
        .add(GROW,
            tabbedPane()
            .add(tab("Server").add(vm.serverViewModel().createView()))
            .add(tab("Content").add(vm.createMainViewModel().createView()))
        );
    }
}
