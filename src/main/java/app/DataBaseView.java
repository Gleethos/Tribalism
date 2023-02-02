package app;

import app.models.DataBaseViewModel;
import swingtree.UI;

import javax.swing.*;

import static swingtree.UI.*;

/**
 *  Presents some backend information with respect to the database (SQLite save file).
 *  This is the view for the {@link app.models.DataBaseViewModel}.
 */
public class DataBaseView extends JPanel
{
    public DataBaseView(DataBaseViewModel vm) {
        of(this).withLayout(FILL)
        .add(GROW,
            panel(FILL_X.and(WRAP(2)))
            .add(SHRINK, label("Number of users:"))
            .add(SHRINK, label(vm.numberUsers().viewAsString()))
            .add(SHRINK, label("Number of characters:"))
            .add(SHRINK, label(vm.numberCharacters().viewAsString()))
            .add(SHRINK, label("Number of worlds:"))
            .add(SHRINK, label(vm.numberWorlds().viewAsString()))
            .add(SHRINK, label("Number of game masters:"))
            .add(SHRINK, label(vm.numberGameMasters().viewAsString()))
            .add(SHRINK, label("Tables:"))
            .add(SHRINK, label(vm.numberOfTables().viewAsString( s -> "(" + s + " total)")))
            .add(GROW.and(SPAN),
                UI.list(vm.listOfTables())
            )
        )
        .add(GROW,
            panel(FILL.and(WRAP(1)))
            .add(SHRINK, label("SQL:"))
            .add(GROW.and(PUSH_Y), textArea(vm.sql()))
        )
        .add(GROW,
            panel(FILL.and(WRAP(1)))
            .add(SHRINK, label("SQL result:"))
            .add(GROW.and(PUSH_Y), textArea(vm.sqlResult()))
        )
        .add(GROW,
            panel(FILL.and(WRAP(2)))
            .add(SHRINK, button("Execute SQL").onClick( it -> vm.executeSql() ))
        );
    }

}
