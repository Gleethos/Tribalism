package app.models;

import app.DataBaseView;
import dal.api.DataBase;
import dal.impl.SQLiteDataBase;
import sprouts.Event;
import sprouts.Var;
import sprouts.Vars;

import javax.swing.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataBaseViewModel {

    private final DataBase db;
    private final Var<Integer> numberOfTables = Var.of(0);
    private final Var<Integer> numberUsers = Var.of(0);
    private final Var<Integer> numberCharacters = Var.of(0);
    private final Var<Integer> numberWorlds = Var.of(0);
    private final Var<Integer> numberGameMasters = Var.of(0);
    private final Var<String> sql = Var.of("");
    private final Var<String> sqlFeedback = Var.of("");
    private final Vars<String> listOfTables = Vars.of(String.class);
    private final Map<String, List<String>> resultData = new HashMap<>();

    public DataBaseViewModel(DataBase db) {
        this.db = db;
        loadFromDataBase();
    }

    public void loadFromDataBase() {
        List<String> tables = db.listOfAllTableNames();
        listOfTables.clear();
        numberOfTables.set(tables.size());
        numberUsers.set(db.select(User.class).asList().size());
        numberCharacters.set(db.select(Character.class).asList().size());
        numberWorlds.set(db.select(World.class).asList().size());
        numberGameMasters.set(db.select(GameMaster.class).asList().size());
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
        Map<String, List<String>> result = Collections.emptyMap();
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

    Event onExecuteSql = Event.of();

    public Event sqlExecuted() { return onExecuteSql; }

    public JComponent createView() { return new DataBaseView(this); }

}
