package app.dev;

import app.App;
import app.AppContext;
import com.formdev.flatlaf.FlatLightLaf;
import swingtree.UI;
import swingtree.threading.EventProcessor;

import javax.swing.*;

import static swingtree.UI.*;

public class AbilityTypesView extends JPanel
{
    public AbilityTypesView(AbilityTypesViewModel vm) {
        of(this).withLayout(FILL.and(WRAP(2).and(INS(24))), "[grow][grow]")
        .add(ALIGN_LEFT,
            panel(FILL)
            .add(SHRINK_X, label("Search:"))
            .add(ALIGN_LEFT, textField(vm.searchKey()))
        )
        .add(ALIGN_RIGHT,
            panel(FILL)
            .add(textField(vm.newAbilityTypeName()))
            .add(SHRINK, button("+").onClick(it -> vm.addNewAbilityType()))
        )
        .add(SPAN.and(GROW).and(PUSH),
            panel(FILL.and(WRAP(1)))
            .add(label("Found Ability Types:"))
            .add(GROW.and(PUSH),
                scrollPanels().withPrefSize(600, 600)
                .add(vm.skillTypes(), stm ->
                    stm.getViewCache().get(()->
                        UI.panel(UI.FILL.and(UI.INS(12)))
                        .add(UI.WIDTH(90,120,220), UI.textField(stm.abilityType().name()))
                        .add(UI.SHRINK, UI.label("Description:"))
                        .add(UI.GROW.and(UI.PUSH), UI.textField(stm.abilityType().description()))
                        .add(UI.SHRINK,
                            UI.button("Delete").onClick( it -> {
                                UI.run(()-> {
                                    var answer = UI.confirm("Delete Ability Type", "Are you sure you want to delete this ability type?");
                                    if ( answer.isYes() ) {
                                        var confirmation = stm.delete();
                                        boolean reallyYes = UI.confirm(confirmation.title(), confirmation.question()).isYes();
                                        if ( reallyYes )
                                            confirmation.yes();
                                        else
                                            confirmation.no();
                                    }
                                });
                            })
                        )
                        .add(UI.GROW.and(UI.WRAP).and(UI.SPAN), UI.separator())
                    )
                )
            )
        );
    }



    public static void main(String... args) {
        UI.runLater(()->{
            var vm = new AbilityTypesViewModel(new AppContext(new App()));
            FlatLightLaf.setup();
            var view = new AbilityTypesView(vm);
            UI.show(view);
        });
        EventProcessor.DECOUPLED.join();
    }

}
