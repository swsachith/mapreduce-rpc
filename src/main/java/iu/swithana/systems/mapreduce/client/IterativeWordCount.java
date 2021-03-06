package iu.swithana.systems.mapreduce.client;

import iu.swithana.systems.mapreduce.common.JobContext;
import iu.swithana.systems.mapreduce.common.Mapper;
import iu.swithana.systems.mapreduce.common.Reducer;
import iu.swithana.systems.mapreduce.common.ResultMap;
import iu.swithana.systems.mapreduce.config.Config;
import iu.swithana.systems.mapreduce.config.Constants;
import iu.swithana.systems.mapreduce.master.MapRedRMI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Iterator;
import java.util.stream.Stream;

public class IterativeWordCount {
    private static Logger logger = LoggerFactory.getLogger(IterativeWordCount.class);

    private static int REGISTRY_PORT;
    private static String REGISTRY_HOST;
    private static String MASTER_BIND;
    private static String INPUT_DIR;
    private static String OUTPUT_DIR;
    private static int NO_ITERATIONS;
    private static int CONVERGE_THRESHOLD;

    public static void main(String[] args) throws IOException {
        // loading the configs
        Config config = new Config();
        REGISTRY_PORT = Integer.parseInt(config.getConfig(Constants.RMI_REGISTRY_PORT));
        REGISTRY_HOST = config.getConfig(Constants.RMI_REGISTRY_HOST);
        MASTER_BIND = config.getConfig(Constants.MASTER_BIND);
        INPUT_DIR = config.getConfig(Constants.INPUT_DIR);
        OUTPUT_DIR = config.getConfig(Constants.OUTPUT_DIR);
        NO_ITERATIONS = Integer.parseInt(config.getConfig(Constants.NO_ITERATIONS));
        CONVERGE_THRESHOLD = Integer.parseInt(config.getConfig(Constants.CONVERGE_THRESHOLD));

        // start of iterations
        String result = "";
        for (int i = 0; i < NO_ITERATIONS; i++) {
            logger.info("Iteration : " + i);
            result = runIterativeWordCountProgram();
            if (testConvergence(result, "pride=", CONVERGE_THRESHOLD)) {
                logger.info("Threshold reached, hence stopping the iterations");
                break;
            }
            if (i + 1 == NO_ITERATIONS) {
                logger.info(" \"Maximum number of iterations reached.");
                break;
            }
            copyResults(result, INPUT_DIR, i);
        }
        logger.info("Final Result is: " + result);
    }

    protected static String runIterativeWordCountProgram() {
        String result = "";
        Registry lookupRegistry;
        try {
            lookupRegistry = LocateRegistry.getRegistry(REGISTRY_PORT);
            MapRedRMI mapper = (MapRedRMI) lookupRegistry.lookup(MASTER_BIND);
            logger.info("Invoking the MapReduce Job!");
            result = mapper.submitJob(WordMapper.class, WordReducer.class, INPUT_DIR, OUTPUT_DIR);
            logger.info("Result: " + result);
        } catch (AccessException e) {
            logger.error("Error accessing the registry: " + e.getMessage(), e);
        } catch (RemoteException e) {
            logger.error("Error occurred while accessing the registry: " + e.getMessage(), e);
        } catch (NotBoundException e) {
            logger.error("Error occurred while retrieving RPC bind: " + e.getMessage(), e);
        }
        return result;
    }

    public static class WordMapper implements Mapper {
        public void map(String input, ResultMap resultMap, JobContext jobContext) {
            String[] lines = input.split("\n");
            for (String line : lines) {
                if (line != null || !line.equals("")) {
                    String[] words = line.replaceAll("[^a-zA-Z0-9]", " ").split(" ");
                    for (String word : words) {
                        resultMap.write(word, "1");
                    }
                }
            }
        }
    }

    public static class WordReducer implements Reducer {
        public String reduce(String key, Iterator<String> values) {
            int result = 0;
            while (values.hasNext()) {
                result += Integer.parseInt(values.next());
            }
            return String.valueOf(result);
        }
    }

    private static void copyResults(String result, String inputDir, int iterationNumber) throws IOException {
        File resultFile = getResultsFile(result);
        Files.copy(resultFile.toPath(), Paths.get(inputDir + File.separator + resultFile.getName() + iterationNumber),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean testConvergence(String result, String key, int threshold) throws IOException {
        File resultFile = getResultsFile(result);
        try (BufferedReader br = new BufferedReader(new FileReader(resultFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(key)) {
                    String value = line.substring(line.indexOf("=") + 1);
                    if (Integer.parseInt(value) > threshold) {
                        return true;
                    }
                    break;
                }
            }
        }
        return false;
    }

    private static File getResultsFile(String result) {
        return new File(result.substring(result.indexOf("[") + 1, result.indexOf("]")));
    }
}
