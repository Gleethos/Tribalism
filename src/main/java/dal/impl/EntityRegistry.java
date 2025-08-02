package dal.impl;

import dal.api.DataBaseEntity;
import dal.api.Model;
import dal.api.Value;
import org.jspecify.annotations.NullMarked;
import sprouts.Association;
import sprouts.Tuple;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.stream.Collectors;

@NullMarked
final class EntityRegistry
{
    private Association<String, EntityTable> entityTables = Association.betweenLinked(String.class, EntityTable.class);

    private final Map<String, Map<Integer, WeakReference<ModelProxy<?>>>> modelProxies = new LinkedHashMap<>();

    public EntityRegistry() {}

    public void addTables(List<Class<? extends DataBaseEntity>> modelInterfaces)
    {
        modelInterfaces = modelInterfaces
                              .stream()
                              .filter(m -> !entityTables.containsKey(SQLiteDataBase._tableNameFromClass(m)) ) // Filter out already added interfaces
                              .collect(Collectors.toList());

        Set<Class<? extends DataBaseEntity>> distinct = new HashSet<>();
        for (var modelTable : entityTables.values())
            modelTable.entityType().ifPresent(modelInterface -> distinct.add(modelInterface));

        distinct.addAll(modelInterfaces);
        var finalModelInterfaces = (Tuple<Class<? extends DataBaseEntity>>) ((Tuple) Tuple.of(Class.class)).addAll(distinct);

        Map<String, EntityTable> newModelTables = new LinkedHashMap<>();
        for (Class<? extends DataBaseEntity> modelInterface : finalModelInterfaces) {
            if ( Model.class.isAssignableFrom(modelInterface) ) {
                EntityTable modelTable = ModelTable.of((Class<? extends Model<?>>) modelInterface, finalModelInterfaces);
                Objects.requireNonNull(modelTable, "modelTable");
                newModelTables.put(modelTable.getTableName(), modelTable);
                modelTable.getFields().forEach(
                        f -> f.getIntermediateTable().ifPresent(
                                t -> newModelTables.put(t.getTableName(), t)
                        )
                );
            } else if (Value.class.isAssignableFrom(modelInterface)) {
                ValueTable valueTable = ValueTable.of((Class<? extends Value>) modelInterface, finalModelInterfaces);
                Objects.requireNonNull(valueTable, "valueTable");
                newModelTables.put(valueTable.getTableName(), valueTable);
                valueTable.getFields().forEach(
                        f -> f.getIntermediateTable().ifPresent(
                                t -> newModelTables.put(t.getTableName(), t)
                        )
                );
            }
        }
        /*
            Now we need to check if there are any circular references
            We do this by checking if there are any cycles in the graph of the model tables
         */
        for (EntityTable modelTable : newModelTables.values()) {
            Set<EntityTable> visited = new HashSet<>();
            Set<EntityTable> currentPath = new HashSet<>();
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
        Map<Class<?>, List<Class<?>>> modelReferences = new LinkedHashMap<>();
        List<EntityTable> intermediateTables = new ArrayList<>();

        for (EntityTable modelTable : newModelTables.values()) {
            Tuple<Class<? extends DataBaseEntity>> referencedModels = modelTable.getReferencedModels();
            List<Class<?>> references = new ArrayList<>();
            for (Class<? extends DataBaseEntity> referencedModel : referencedModels) {
                if (!referencedModel.equals(modelTable.entityType().orElse(null))) {
                    references.add(referencedModel);
                }
            }
            modelTable.entityType().ifPresent(m -> modelReferences.put(m, references));
            // If it is not present then it is an intermediate table and we do not need to add it to the map
            // because it is not referenced by any other table, so it can be created at the end.
            if (modelTable instanceof IntermediateTable) {
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
            EntityTable modelTable = newModelTables.get(AbstractDataBase._tableNameFromClass(model));
            Objects.requireNonNull(modelTable, "No table found for model class '" + model + "'");
            entityTables = entityTables.put(modelTable.getTableName(), modelTable);
        }

        // Now we need to add intermediate tables
        for (EntityTable modelTable : intermediateTables) {
            entityTables = entityTables.put(modelTable.getTableName(), modelTable);
        }
        // We are done!
    }

    private boolean _hasCycle(
            EntityTable modelTable,
            Set<EntityTable> visited,
            Set<EntityTable> currentPath,
            Map<String, EntityTable> newModelTables
    ) {
        Objects.requireNonNull(modelTable, "modelTable");
        if (visited.contains(modelTable))
            return false;
        if (currentPath.contains(modelTable))
            return true;
        currentPath.add(modelTable);
        for (Class<? extends DataBaseEntity> referencedModel : modelTable.getReferencedModels()) {
            var tableName = AbstractDataBase._tableNameFromClass(referencedModel);
            var foundTable = newModelTables.get(tableName);
            if ( foundTable == null ) {
                throw new IllegalStateException(
                        "Internal error detected! Failed to find a table configuration for '" + referencedModel
                        + "' and table name '" + tableName + "'."
                    );
            }
            if (_hasCycle(foundTable, visited, currentPath, newModelTables))
                return true;
        }
        currentPath.remove(modelTable);
        visited.add(modelTable);
        return false;
    }

    public Tuple<EntityTable> getTables() {
        return entityTables.values();
    }

    public boolean hasTable(String tableName) {
        return entityTables.containsKey(tableName);
    }

    public EntityTable getTable(String tableName) {
        return entityTables.get(tableName).orElseThrow();
    }

    public boolean hasTable(Class<? extends Model<?>> modelInterface) {
        return entityTables.values().stream().anyMatch(t -> t.entityType().isPresent() && t.entityType().get().equals(modelInterface));
    }

    Optional<EntityTable> getTable(Class<? extends Model<?>> modelInterface ) {
        String tableName = AbstractDataBase._tableNameFromClass(modelInterface);
        var found1 = entityTables.get(tableName).orElse(null);
        var found2 = entityTables.values()
                                .stream()
                                .filter(t -> t.entityType().isPresent() && t.entityType().get().equals(modelInterface))
                                .findFirst()
                                .orElse(null);

        // Let's do some consistency checks
        if ( found1 != found2 )
            throw new IllegalStateException("The model table for " + modelInterface + " is not consistent!");

        return Optional.ofNullable(found1);
    }

    public Tuple<IntermediateTable> getIntermediateTables() {
        return entityTables.values().stream().filter(t -> t instanceof IntermediateTable).map(IntermediateTable.class::cast).collect(Tuple.collectorOf(IntermediateTable.class));
    }

    public Tuple<IntermediateTable> getIntermediateTableInvolving(Class<? extends Model<?>> modelInterface) {
        return getIntermediateTables().stream().filter(t -> t.getReferencedModels().contains(modelInterface)).collect(Tuple.collectorOf(IntermediateTable.class));
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
