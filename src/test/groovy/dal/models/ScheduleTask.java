package dal.models;

import dal.api.Model;
import sprouts.Var;

import java.util.concurrent.TimeUnit;

public interface ScheduleTask extends Model<ScheduleTask>
{
    Var<String>   name();
    Var<Long>     interval();
    Var<TimeUnit> timeUnit();
    Var<Boolean>  isActive();
}
