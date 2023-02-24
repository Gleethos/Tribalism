package app;

import app.models.SkillType;
import com.formdev.flatlaf.FlatLightLaf;
import swingtree.EventProcessor;
import swingtree.UI;

import javax.swing.*;

import static swingtree.UI.*;

public class SkillTypesView extends JPanel
{
    private final SkillTypesViewModel viewModel;
    private final JPanel skillListPanel = UI.of(new JPanel()).withLayout(FILL_X).getComponent();

    public SkillTypesView(SkillTypesViewModel vm) {
        this.viewModel = vm;
        of(this).withLayout(FILL.and(WRAP(2)), "[grow][grow]")
        .add(label("Search:"))
        .add(textField(vm.searchKey()))
        .add(SPAN.and(GROW),
            panel(FILL.and(WRAP(1)))
            .add(label("Found Skill Types:"))
            .add(GROW, scrollPane().add(skillListPanel))
        )
        .add(SPAN,
            panel(FILL.and(WRAP(1)))
            .add(label("Add New Skill Type:"))
            .add(button("Add New Skill Type").onClick(it -> vm.addNewSkillType()))
        );

        Runnable updateSkills = ()->{
            UI.runLater(()->{
                skillListPanel.removeAll();
                UI.of(skillListPanel).apply( list -> {
                    var abilities = viewModel.abilityTypes();
                    for (SkillType skillType : viewModel.skillTypes())
                        list.add(GROW, textField(skillType.name()))
                                .add(GROW, comboBox(skillType.primaryAbility(), abilities))
                                .add(GROW, comboBox(skillType.secondaryAbility(), abilities))
                                .add(GROW, comboBox(skillType.tertiaryAbility(), abilities))
                                .add(SHRINK.and(WRAP), button("Delete").onClick(it2 -> vm.deleteSkillType(skillType)))
                                .add(SHRINK, label("Description:"))
                                .add(GROW.and(WRAP).and(SPAN), textField(skillType.description()))
                                .add(GROW.and(WRAP).and(SPAN), separator());
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
            JFrame frame = new JFrame();
            new UI.TestWindow( () -> frame,view );
            // We set the size to fit the component:
            frame.setSize(view.getPreferredSize());
            // We center the frame on the screen:
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
        UI.joinDecoupledEventProcessor();
    }

}
