package app;

import swingtree.UI;

import javax.swing.*;

import static swingtree.UI.*;

/**
 *  Presents some backend information with respect to the database (SQLite save file).
 *  This is the view for the {@link DataBaseViewModel}.
 */
public class DataBaseView extends JPanel
{
    public DataBaseView(DataBaseViewModel vm) {
        of(this).withLayout(FILL)
        .add(GROW,
            panel(FILL_X.and(WRAP(1)))
            .add(GROW,
                panel(FILL.and(WRAP(1)))
                .add(SHRINK, label("SQL:"))
                .add(GROW.and(PUSH_Y), textArea(vm.sql()))
            )
            .add(GROW,
                panel(FILL.and(WRAP(2)))
                .add(SHRINK, button("Execute SQL").onClick( it -> vm.executeSql() ))
                .add(GROW, label(vm.sqlFeedback()))
            )
            .add(GROW,
                panel(FILL.and(WRAP(1)))
                .add(SHRINK, label("SQL result:"))
                .add(GROW.and(PUSH_Y),
                    scrollPane().add(
                        table(MapData.READ_ONLY, ()->vm.resultData())
                        .updateTableOn(vm.sqlExecuted())
                    )
                )
            )
        )
        .add(GROW,
            panel(FILL_X.and(WRAP(2)))
            .add(SHRINK, label("Users:"))
            .add(SHRINK, label(vm.numberUsers().viewAsString()))
            .add(SHRINK, label("Characters:"))
            .add(SHRINK, label(vm.numberCharacters().viewAsString()))
            .add(SHRINK, label("Worlds:"))
            .add(SHRINK, label(vm.numberWorlds().viewAsString()))
            .add(SHRINK, label("Game masters:"))
            .add(SHRINK, label(vm.numberGameMasters().viewAsString()))
            .add(GROW.and(SPAN), separator())
            .add(SHRINK, label("Tables:"))
            .add(SHRINK, label(vm.numberOfTables().viewAsString( s -> "(" + s + " total)")))
            .add(GROW.and(SPAN),
                UI.list(vm.listOfTables())
            )
        );
    }

}
