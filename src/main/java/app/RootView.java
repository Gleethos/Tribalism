package app;

import app.dev.BootstrapView;
import app.dev.DataBaseView;
import app.dev.ServerView;

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
                .add(tab("Server").add(new ServerView(vm.serverViewModel())))
                .add(tab("Content").add(new ContentView(vm.mainViewModel())))
                .add(tab("Database").add(new DataBaseView(vm.dataBaseViewModel())))
                .add(tab("Bootstrap").add(new BootstrapView(vm.bootstrapViewModel())))
            )
        )
        .applyIf(!vm.developerTabsAreShown(),
            it -> it.add(GROW, new ContentView(vm.mainViewModel()))
        );
    }
}
