package iu.swithana.systems.mapreduce.master.core;
import iu.swithana.systems.mapreduce.common.ResultMap;
import iu.swithana.systems.mapreduce.common.JobContext;
import iu.swithana.systems.mapreduce.util.FileManager;
import iu.swithana.systems.mapreduce.worker.WorkerRMI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class MapperTask implements Runnable {
    private static Logger logger = LoggerFactory.getLogger(MapperTask.class);
    private File file;
    private WorkerRMI worker;
    private FileManager fileManager;
    private Class mapperClass;
    private String workerID;
    private MapResultListener mapResultListener;
    private String jobID;

    public MapperTask(File file, WorkerRMI worker, Class mapperClass, FileManager fileManager, String workerID,
                      String jobID, MapResultListener mapResultListener) {
        this.file = file;
        this.worker = worker;
        this.fileManager = fileManager;
        this.mapperClass = mapperClass;
        this.workerID = workerID;
        this.mapResultListener = mapResultListener;
        this.jobID = jobID;
    }

    @Override
    public void run() {
        try {
            JobContext jobContext = new JobContext();
            jobContext.addConfig("filename", file.getName());
            jobContext.addConfig("jobid", jobID);
            ResultMap resultMap = worker.doMap(fileManager.readFile(this.file), mapperClass, jobContext);
            mapResultListener.onResult(resultMap, workerID, file);
        } catch (Exception e) {
            logger.error("Exception occurred in completing the job on worker: " + this.workerID + " " + e.getMessage(), e);
            mapResultListener.onError(e, workerID, file);
        }
    }
}
