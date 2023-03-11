package app.dev;

import app.models.Character;
import app.models.GameMaster;
import app.models.User;
import app.models.World;
import dal.api.DataBase;
import dal.impl.SQLiteDataBase;
import sprouts.Event;
import sprouts.Var;
import sprouts.Vars;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *  This is the view model for the {@link DataBaseView} which is mostly intended for debugging purposes
 *  by the developer or game master. It exposes direct access to the database tables.
 *  Be careful when using this view model, as it can easily corrupt the TopSoil {@link DataBase} layer (our ORM).
 */
public class DataBaseViewModel {

    private final DataBase db;
    private final Var<Integer> numberOfTables    = Var.of(0);
    private final Var<Integer> numberUsers       = Var.of(0);
    private final Var<Integer> numberCharacters  = Var.of(0);
    private final Var<Integer> numberWorlds      = Var.of(0);
    private final Var<Integer> numberGameMasters = Var.of(0);
    private final Var<String> sql                = Var.of("");
    private final Var<String> sqlFeedback        = Var.of("");
    private final Vars<String> listOfTables      = Vars.of(String.class);
    private final Map<String, List<String>> resultData = new HashMap<>();
    private final Event onExecuteSql = Event.create();


    public DataBaseViewModel(DataBase db) {
        this.db = db;
        loadFromDataBase();
    }

    public void loadFromDataBase() {
        List<String> tables = db.listOfAllTableNames();
        listOfTables.clear();
        numberOfTables.set(tables.size());
        numberUsers.set(db.select(User.class).count());
        numberCharacters.set(db.select(Character.class).count());
        numberWorlds.set(db.select(World.class).count());
        numberGameMasters.set(db.select(GameMaster.class).count());
        listOfTables.addAll(tables);
    }

    public Var<Integer> numberOfTables() { return numberOfTables; }
    public Var<Integer> numberUsers() { return numberUsers; }
    public Var<Integer> numberCharacters() { return numberCharacters; }
    public Var<Integer> numberWorlds() { return numberWorlds; }
    public Var<Integer> numberGameMasters() { return numberGameMasters; }
    public Var<String> sql() { return sql; }
    public Var<String> sqlFeedback() { return sqlFeedback; }

    public Vars<String> listOfTables() { return listOfTables; }
    public Map<String, List<String>> resultData() { return resultData; }

    public void executeSql() {
        resultData.clear();
        Map<String, List<String>> result;
        try {
            result = ((SQLiteDataBase) db).query(sql.get());
        } catch (Exception e) {
            sqlFeedback.set("Error: \n" + e.getMessage());
            return;
        }
        sqlFeedback.set("Success! \n" + result.size() + " rows returned.");
        resultData.putAll(result);
        onExecuteSql.fire();
    }

    public Event sqlExecuted() { return onExecuteSql; }

    public JComponent createView() { return new DataBaseView(this); }

}
