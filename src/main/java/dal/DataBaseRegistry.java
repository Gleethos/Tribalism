package dal;

import java.util.HashMap;
import java.util.Map;

public class DataBaseRegistry {

    private final Map<Class<?>, DataBase.ModelSummary> _modelSummaries = new HashMap<>();
    private final Map<String, DataBase.ModelSummary> _modelSummariesByTableName = new HashMap<>();

    public void put(Class<?> modelClass, DataBase.ModelSummary modelSummary) {
        _modelSummaries.put(modelClass, modelSummary);
        _modelSummariesByTableName.put(modelSummary.getTableName(), modelSummary);
    }

    public DataBase.ModelSummary get(Class<?> modelClass) {
        return _modelSummaries.get(modelClass);
    }

    public DataBase.ModelSummary get(String tableName) {
        return _modelSummariesByTableName.get(tableName);
    }

    public boolean contains(Class<?> modelClass) {
        return _modelSummaries.containsKey(modelClass);
    }

    public boolean contains(String tableName) {
        return _modelSummariesByTableName.containsKey(tableName);
    }

    public void clear() {
        _modelSummaries.clear();
        _modelSummariesByTableName.clear();
    }
}
