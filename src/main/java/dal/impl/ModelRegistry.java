package dal.impl;

import dal.api.Model;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.stream.Collectors;

class ModelRegistry
{
    private final Map<String, ModelTable> modelTables = new LinkedHashMap<>();

    private final Map<String, Map<Integer, WeakReference<ModelProxy<?>>>> modelProxies = new LinkedHashMap<>();

    public ModelRegistry() {}

    public void addTables(List<Class<? extends Model<?>>> modelInterfaces) {

        Set<Class<? extends Model<?>>> distinct = new HashSet<>();
        for (var modelTable : modelTables.values())
            modelTable.getModelInterface().ifPresent(modelInterface -> distinct.add(modelInterface));

        distinct.addAll(modelInterfaces);
        modelInterfaces = new ArrayList<>(distinct);

        Map<String, ModelTable> newModelTables = new LinkedHashMap<>();
        for (Class<? extends Model<?>> modelInterface : modelInterfaces) {
            ModelTable modelTable = new DefaultModelTable(modelInterface, modelInterfaces);
            newModelTables.put(modelTable.getTableName(), modelTable);
            modelTable.getFields().forEach(
                    f -> f.getIntermediateTable().ifPresent(
                            t -> newModelTables.put(t.getTableName(), t)
                    )
            );
        }
        /*
            Now we need to check if there are any circular references
            We do this by checking if there are any cycles in the graph of the model tables
         */
        for (ModelTable modelTable : newModelTables.values()) {
            Set<ModelTable> visited = new HashSet<>();
            Set<ModelTable> currentPath = new HashSet<>();
            if (_hasCycle(modelTable, visited, currentPath, newModelTables))
                throw new IllegalArgumentException(
                        "The model " + modelTable.getTableName() + " has a circular reference!"
                );
        }

        /*
            Now we need to determine the order in which we create the tables
            We will do this by creating a map between all models as keys and their references
            as values.
            If they are referencing themselves we treat it as no reference.
            We will then fill a list with the models that have no references
            and then remove them from the map and repeat the process until the map is empty.
        */
        List<Class<?>> sortedModels = new ArrayList<>();
        Map<Class<?>, List<Class<?>>> modelReferences = new HashMap<>();
        List<ModelTable> intermediateTables = new ArrayList<>();

        for (ModelTable modelTable : newModelTables.values()) {
            List<Class<? extends Model<?>>> referencedModels = modelTable.getReferencedModels();
            List<Class<?>> references = new ArrayList<>();
            for (Class<? extends Model<?>> referencedModel : referencedModels) {
                if (!referencedModel.equals(modelTable.getModelInterface().orElse(null))) {
                    references.add(referencedModel);
                }
            }
            modelTable.getModelInterface().ifPresent(m -> modelReferences.put(m, references));
            // If it is not present then it is an intermediate table and we do not need to add it to the map
            // because it is not referenced by any other table, so it can be created at the end.
            if (modelTable.getModelInterface().isEmpty()) {
                intermediateTables.add(modelTable);
            }
        }

        while (!modelReferences.isEmpty()) {
            List<Class<?>> modelsWithoutReferences = new ArrayList<>();
            for (Map.Entry<Class<?>, List<Class<?>>> entry : modelReferences.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    modelsWithoutReferences.add(entry.getKey());
                }
            }
            if (modelsWithoutReferences.isEmpty()) {
                throw new IllegalArgumentException(
                        "There are circular references in the model interfaces!"
                );
            }
            for (Class<?> modelWithoutReference : modelsWithoutReferences) {
                modelReferences.remove(modelWithoutReference);
                sortedModels.add(modelWithoutReference);
            }
            for (Map.Entry<Class<?>, List<Class<?>>> entry : modelReferences.entrySet()) {
                entry.getValue().removeAll(modelsWithoutReferences);
            }
        }

        for (Class<?> model : sortedModels) {
            ModelTable modelTable = newModelTables.get(AbstractDataBase._tableNameFromClass(model));
            modelTables.put(modelTable.getTableName(), modelTable);
        }

        // Now we need to add intermediate tables
        for (ModelTable modelTable : intermediateTables) {
            modelTables.put(modelTable.getTableName(), modelTable);
        }
        // We are done!
    }

    private boolean _hasCycle(
            ModelTable modelTable,
            Set<ModelTable> visited,
            Set<ModelTable> currentPath,
            Map<String, ModelTable> newModelTables
    ) {
        if (visited.contains(modelTable))
            return false;
        if (currentPath.contains(modelTable))
            return true;
        currentPath.add(modelTable);
        for (Class<? extends Model<?>> referencedModel : modelTable.getReferencedModels()) {
            if (_hasCycle(newModelTables.get(AbstractDataBase._tableNameFromClass(referencedModel)), visited, currentPath, newModelTables))
                return true;
        }
        currentPath.remove(modelTable);
        visited.add(modelTable);
        return false;
    }

    public List<ModelTable> getTables() {
        return new ArrayList<>(modelTables.values());
    }

    public List<String> getCreateTableStatements() {
        List<String> statements = new ArrayList<>();
        for (ModelTable modelTable : modelTables.values()) {
            statements.add(modelTable.createTableStatement());
        }
        return statements;
    }

    public boolean hasTable(String tableName) {
        return modelTables.containsKey(tableName);
    }

    public ModelTable getTable(String tableName) {
        return modelTables.get(tableName);
    }

    public boolean hasTable(Class<? extends Model<?>> modelInterface) {
        return modelTables.values().stream().anyMatch(t -> t.getModelInterface().isPresent() && t.getModelInterface().get().equals(modelInterface));
    }

    public ModelTable getTable(Class<? extends Model<?>> modelInterface) {
        return modelTables.values().stream().filter(t -> t.getModelInterface().isPresent() && t.getModelInterface().get().equals(modelInterface)).findFirst().orElse(null);
    }

    public List<ModelTable> getIntermediateTables() {
        return modelTables.values().stream().filter(t -> t.getModelInterface().isEmpty()).collect(Collectors.toList());
    }

    public List<ModelTable> getIntermediateTableInvolving(Class<? extends Model<?>> modelInterface) {
        return getIntermediateTables().stream().filter(t -> t.getReferencedModels().contains(modelInterface)).collect(Collectors.toList());
    }

    public Optional<ModelProxy<?>> findModelProxy(String tableName, int id) {
        var proxies = this.modelProxies.get(tableName);
        if (proxies == null) {
            return Optional.empty();
        }
        var found = proxies.get(id);
        if (found == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(found.get());
    }

    public void addModelProxy(ModelProxy<?> modelProxy) {
        var proxies = this.modelProxies.computeIfAbsent(modelProxy.getTableName(), k -> new HashMap<>());
        proxies.put(modelProxy.getId(), new WeakReference<>(modelProxy));
    }

    public void removeModelProxy(ModelProxy<?> modelProxy) {
        var proxies = this.modelProxies.get(modelProxy.getTableName());
        if (proxies == null) {
            return;
        }
        proxies.remove(modelProxy.getId());
        if (proxies.isEmpty()) {
            this.modelProxies.remove(modelProxy.getTableName());
        }
    }

    public void removeModelProxy(String tableName, int id ) {
        var proxies = this.modelProxies.get(tableName);
        if (proxies == null) {
            return;
        }
        proxies.remove(id);
        if (proxies.isEmpty()) {
            this.modelProxies.remove(tableName);
        }
    }

}
