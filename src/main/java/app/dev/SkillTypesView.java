package app.dev;

import app.App;
import app.AppContext;
import com.formdev.flatlaf.FlatLightLaf;
import swingtree.UI;
import swingtree.threading.EventProcessor;

import javax.swing.*;

import static swingtree.UI.*;

public class SkillTypesView extends JPanel
{
    public SkillTypesView(SkillTypesViewModel vm) {
        of(this).withLayout(FILL.and(WRAP(2).and(INS(24))), "[grow][grow]")
        .add(ALIGN_LEFT,
            panel(FILL)
            .add(SHRINK_X, label("Search:"))
            .add(ALIGN_LEFT, textField(vm.searchKey()))
        )
        .add(ALIGN_RIGHT,
            panel(FILL)
            .add(textField(vm.newSkillTypeName()))
            .add(SHRINK, button("+").onClick(it -> vm.addNewSkillType()))
        )
        .add(SPAN.and(GROW).and(PUSH),
            panel(FILL.and(WRAP(1)))
            .add(label("Found Skill Types:"))
            .add(GROW.and(PUSH),
                scrollPanels().withPrefSize(600, 600)
                .add(vm.skillTypes(), svm ->
                        svm.getViewCache().get(()->
                        UI.panel(UI.FILL.and(UI.INS(12)))
                        .add(UI.GROW, UI.textField(svm.skillType().name()))
                        .add(UI.GROW, UI.comboBox(svm.skillType().primaryAbility(), svm.abilities()))
                        .add(UI.GROW, UI.comboBox(svm.skillType().secondaryAbility(), svm.abilities()))
                        .add(UI.GROW, UI.comboBox(svm.skillType().tertiaryAbility(), svm.abilities()))
                        .add(UI.SHRINK.and(UI.WRAP),
                            UI.button("Delete").onClick( it ->
                                UI.run( () -> {
                                    var answer = UI.confirm("Delete Skill Type", "Are you sure you want to delete this skill type?");
                                    if ( answer.isYes() ) {
                                        var confirmation = svm.delete();
                                        var reallyYes = UI.confirm(confirmation.title(), confirmation.question()).isYes();
                                        if ( reallyYes )
                                            confirmation.yes();
                                        else
                                            confirmation.no();
                                    }
                                })
                            )
                        )
                        .add(UI.SHRINK, UI.label("Description:"))
                        .add(UI.GROW.and(UI.WRAP).and(UI.SPAN), UI.textField(svm.skillType().description()))
                        .add(UI.GROW.and(UI.WRAP).and(UI.SPAN), UI.separator())
                    )
                )
            )
        );
    }



    public static void main(String... args) {
        UI.runLater(()->{
            var vm = new SkillTypesViewModel(new AppContext(new App()));
            FlatLightLaf.setup();
            var view = new SkillTypesView(vm);
            UI.show(view);
        });
        EventProcessor.DECOUPLED.join();
    }

}
