package app;

import app.models.SkillType;
import com.formdev.flatlaf.FlatLightLaf;
import swingtree.UI;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

import static swingtree.UI.*;

public class SkillTypesView extends JPanel
{
    private final SkillTypesViewModel viewModel;
    private final JPanel skillListPanel = UI.of(new JPanel()).withLayout(FILL_X.and(INS(12))).getComponent();
    private final Map<Integer, JPanel> skillTypeViewCache = new HashMap<>();


    public SkillTypesView(SkillTypesViewModel vm) {
        this.viewModel = vm;
        of(this).withLayout(FILL.and(WRAP(2).and(INS(24))), "[grow][grow]")
        .add(ALIGN_LEFT,
            panel(FILL)
            .add(SHRINK_X, label("Search:"))
            .add(ALIGN_LEFT, textField(vm.searchKey()))
        )
        .add(ALIGN_RIGHT,
            panel(FILL)
            .add(textField())
            .add(SHRINK, button("+").onClick(it -> vm.addNewSkillType()))
        )
        .add(SPAN.and(GROW),
            panel(FILL.and(WRAP(1)))
            .add(label("Found Skill Types:"))
            .add(GROW, scrollPane().add(skillListPanel))
        );

        Runnable updateSkills = ()->{
            UI.runLater(()->{
                skillListPanel.removeAll();
                UI.of(skillListPanel)
                .apply( list -> {
                    var abilities = viewModel.abilityTypes();
                    for (SkillType skillType : viewModel.skillTypes()) {
                        if ( !skillTypeViewCache.containsKey(skillType.id().get()) )
                            skillTypeViewCache.put(skillType.id().get(),
                                     panel(FILL.and(INS(12)))
                                    .add(GROW, textField(skillType.name()))
                                    .add(GROW, comboBox(skillType.primaryAbility(), abilities))
                                    .add(GROW, comboBox(skillType.secondaryAbility(), abilities))
                                    .add(GROW, comboBox(skillType.tertiaryAbility(), abilities))
                                    .add(SHRINK.and(WRAP), button("Delete").onClick(it2 -> vm.deleteSkillType(skillType)))
                                    .add(SHRINK, label("Description:"))
                                    .add(GROW.and(WRAP).and(SPAN), textField(skillType.description()))
                                    .add(GROW.and(WRAP).and(SPAN), separator())
                                    .getComponent());

                        list.add(GROW.and(WRAP), skillTypeViewCache.get(skillType.id().get()));
                    }
                });
                skillListPanel.revalidate();
                skillListPanel.repaint();
            });
        };

        vm.skillTypes().onChange(it -> {updateSkills.run();});
        updateSkills.run();
    }



    public static void main(String... args) {
        UI.runLater(()->{
            var vm = new SkillTypesViewModel(new AppContext(new App()));
            FlatLightLaf.setup();
            var view = new SkillTypesView(vm);
            UI.show(view);
        });
        UI.joinDecoupledEventProcessor();
    }

}
