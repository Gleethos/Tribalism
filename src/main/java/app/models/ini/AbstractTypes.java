package app.models.ini;

import dal.api.DataBase;
import sprouts.Result;

import java.io.File;

/**
 *  This class is the base class for all the types classes, which determine the initial
 *  state of the games working directory and database.
 *  It contains the common functionality for loading, saving and verifying the types
 *  in the working directory and the database.
 */
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
