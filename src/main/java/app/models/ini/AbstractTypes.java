package app.models.ini;

import dal.api.DataBase;
import sprouts.Result;

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

    public final void loadFromResources(DataBase db) {
        var location = "/app/ini/" + fileName;
        loadFromLocation(location, db);
    }

    public final void loadFromWorkingDir(DataBase db) {
        var location = this.workingDirectory + "/" + fileName;
        loadFromLocation(location, db);
    }

    public final void saveToWorkingDir(DataBase db) {
        var path = workingDirectory + "/" + fileName;
        saveAsJSONToWorkingDirectory(path, db);
    }

    protected abstract void loadFromLocation(String location, DataBase db);

    protected abstract void saveAsJSONToWorkingDirectory(String location, DataBase db);

    protected abstract Result<Boolean> isDataBaseStateMatchingWorkingDirectory(DataBase db);
}
