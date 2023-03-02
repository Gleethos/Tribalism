package app;

import static swingtree.UI.*;

/**
 *  Basically a tabbed pane with a tab for different main views.
 *  The most important ones are the content view and the server view.
 */
public class RootView extends javax.swing.JPanel
{
    public RootView(RootViewModel vm) {
        of(this).withLayout(FILL)
        .withPrefSize(800, 600)
        .applyIf(vm.developerTabsAreShown(), it ->
            it.add(GROW,
                tabbedPane()
                .add(tab("Server").add(vm.serverViewModel().createView()))
                .add(tab("Content").add(vm.mainViewModel().createView()))
                .add(tab("Database").add(vm.dataBaseViewModel().createView()))
                .add(tab("Bootstrap").add(vm.bootstrapViewModel().createView()))
            )
        )
        .applyIf(!vm.developerTabsAreShown(),
            it -> it.add(GROW, vm.mainViewModel().createView())
        );
    }
}
