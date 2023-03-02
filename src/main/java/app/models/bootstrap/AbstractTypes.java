package app.models.bootstrap;

import dal.api.DataBase;

import java.io.File;

public abstract class AbstractTypes
{
    protected final String workingDirectory;
    protected final String fileName;


    public AbstractTypes(String workingDirectory, String fileName) {
        this.workingDirectory = workingDirectory;
        this.fileName = fileName;
    }

    public boolean localTypesExist() { return new File(workingDirectory + "/" + fileName ).exists(); }

    public void loadFromResources(DataBase db) {
        var location = "/app/bootstrap/" + fileName;
        loadFromLocation(location, db);
    }

    public void loadFromWorkingDir(DataBase db) {
        var location = this.workingDirectory + "/" + fileName;
        loadFromLocation(location, db);
    }

    public void saveToWorkingDir(DataBase db) {
        var path = workingDirectory + "/" + fileName;
        saveAsJSONToWorkingDirectory(path, db);
    }

    protected abstract void loadFromLocation(String location, DataBase db);

    protected abstract void saveAsJSONToWorkingDirectory(String location, DataBase db);
}
