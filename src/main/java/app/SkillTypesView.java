package app;

import app.models.SkillType;
import dal.api.DataBase;
import swingtree.UI;

import javax.swing.*;

import static swingtree.UI.*;

public class SkillTypesView extends JPanel
{
    private final SkillTypesViewModel viewModel;
    private final JPanel skillListPanel = new JPanel();

    public SkillTypesView(SkillTypesViewModel vm) {
        this.viewModel = vm;
        of(this).withLayout(FILL.and(WRAP(2)))
        .add(label("Search:"))
        .add(textField(vm.searchKey()))
        .add(SPAN,
            panel(FILL.and(WRAP(1)))
            .add(label("Found Skill Types:"))
            .add(scrollPane().add(skillListPanel))
        )
        .add(SPAN,
            panel(FILL.and(WRAP(1)))
            .add(label("Add New Skill Type:"))
            .add(button("Add New Skill Type").onClick(it -> vm.addNewSkillType()))
        );
        vm.skillTypes().onChange(it -> {
            skillListPanel.removeAll();
            for (SkillType skillType : viewModel.skillTypes()) {
                skillListPanel.add(
                    panel(FILL.and(WRAP(1)))
                    .add(label(skillType.name()))
                    .add(button("Delete").onClick(it2 -> vm.deleteSkillType(skillType)))
                    .getComponent()
                );
            }
            skillListPanel.revalidate();
            skillListPanel.repaint();
        });
    }

    public static void main(String... args) {
        var crud = new SkillTypesView(new SkillTypesViewModel(new AppContext(new App())));
        UI.show(crud);
    }

}
