package app;

import static swingtree.UI.*;

/**
 *  Basically a tabbed pane with a tab for different main views.
 *  The most important ones are the content view which is the main view of the application
 *  which is the only one shown in the user mode (production mode).
 *  The root view is only shown in the development mode.
 *  To start the application in the development mode, run the application with the
 *  "--show-dev-views true" argument.
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
