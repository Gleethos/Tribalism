package app.dev;

import app.App;
import app.AppContext;
import com.formdev.flatlaf.FlatLightLaf;
import swingtree.UI;
import swingtree.threading.EventProcessor;

import javax.swing.*;

import static swingtree.UI.*;

public class RoleTypesView extends JPanel
{
    public RoleTypesView(RoleTypesViewModel vm) {
        of(this).withLayout(FILL.and(WRAP(2).and(INS(24))), "[grow][grow]")
        .add(ALIGN_LEFT,
            panel(FILL)
            .add(SHRINK_X, label("Search:"))
            .add(ALIGN_LEFT, textField(vm.searchKey()))
        )
        .add(ALIGN_RIGHT,
            panel(FILL)
            .add(textField(vm.newRoleName()))
            .add(SHRINK, button("+").onClick(it -> vm.addNewRole()))
        )
        .add(SPAN.and(GROW).and(PUSH),
            panel(FILL.and(WRAP(1)))
            .add(label("Found Role Types:"))
            .add(GROW.and(PUSH),
                scrollPanels().withPrefSize(600, 600)
                .add(vm.roleTypes(), rtm -> UI.of(rtm.createView()))
            )
        );
    }



    public static void main(String... args) {
        UI.runLater(()->{
            var vm = new RoleTypesViewModel(new AppContext(new App()));
            FlatLightLaf.setup();
            var view = new RoleTypesView(vm);
            UI.show(view);
        });
        EventProcessor.DECOUPLED.join();
    }

}
