package de.uni_mannheim.informatik.wdi.utils;

import BlockBuilding.AbstractBlockingMethod;
import BlockBuilding.MemoryBased.AttributeClusteringBlocking;
import BlockBuilding.MemoryBased.TokenBlocking;
import BlockProcessing.AbstractEfficiencyMethod;
import BlockProcessing.BlockRefinement.BlockFiltering;
import BlockProcessing.ComparisonRefinement.AbstractDuplicatePropagation;
import BlockProcessing.ComparisonRefinement.BilateralDuplicatePropagation;
import DataStructures.AbstractBlock;
import DataStructures.EntityProfile;
import DataStructures.IdDuplicates;
import Experiments.ComparativeAnalysis.OnTheFlyUtilities;
import MetaBlocking.AbstractMetablocking;
import MetaBlocking.WeightingScheme;
import Utilities.BlockStatistics;
import Utilities.RepresentationModel;
import Utilities.StatisticsUtilities;
import com.opencsv.CSVReader;
import de.uni_mannheim.informatik.wdi.model.DefaultSchemaElement;
import de.uni_mannheim.informatik.wdi.model.FusableDataSet;
import de.uni_mannheim.informatik.wdi.model.Pair;
import de.uni_mannheim.informatik.wdi.usecase.events.model.Event;
import de.uni_mannheim.informatik.wdi.usecase.events.model.Location;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

/**
 * Created by Daniel on 02/02/17.
 */
public class WDI_BlockingFramework_Combiner {

    private static DateFormat df = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
    private static HashMap<String, Integer> dIDs;
    private static HashMap<String, Integer> yIDs;

    private final static int NO_OF_DATASETS = 5;

    private final static int NO_OF_BLOCKING_METHODS = 3;
    private final static int NO_OF_ITERATIONS = 10;
    private final static int NO_OF_TEST_RUNS = 10;

    //token blocking
    private final static double tbBestRatios = 0.55;
    private final static int tbBestMbMethodId = 15;

    //attribute clustering
    private final static RepresentationModel ACL_BEST_MODEL = RepresentationModel.TOKEN_BIGRAMS;
    private final static RepresentationModel[] representationModels = {RepresentationModel.CHARACTER_TRIGRAMS,
                                                                        RepresentationModel.TOKEN_BIGRAMS,
                                                                        RepresentationModel.CHARACTER_BIGRAMS,
                                                                        RepresentationModel.TOKEN_TRIGRAM_GRAPHS,
                                                                        RepresentationModel.TOKEN_BIGRAM_GRAPHS};

    private static List<EntityProfile> convertFusableDataSetToEntityProfile(FusableDataSet<Event, DefaultSchemaElement> dataSet,
                                                                            HashMap<String, Integer> ids,
                                                                            int datasetId) {
        List<EntityProfile> list = new ArrayList<>(dataSet.getSize() + 10);
        //datasetId: 0=stripedURI, 1=stripedURI+label, 2=stripedURI+same, 3=stripedURI+label+same, 4=all
        int idCounter = 0;
        for (Event event : dataSet.getRecords()) {
            String eventURI = event.getIdentifier();
            EntityProfile entityProfile = new EntityProfile(eventURI);

            entityProfile.addAttribute("stripedURI", stripPrefix(eventURI));

            if (datasetId == 1 || datasetId > 2) {
                for (String label : event.getLabels()) {
                    entityProfile.addAttribute("label", label);
                }
            }
            if (datasetId > 1) {
                for (String same : event.getSames()) {
                    entityProfile.addAttribute("same", same);
                }
            }
            if (datasetId > 3) {
                for (LocalDate date : event.getDates()) {
                    entityProfile.addAttribute("date", date.toString());
                }
                for (Pair<Double, Double> coordinatePair : event.getCoordinates()) {
                    entityProfile.addAttribute("lat", coordinatePair.getFirst().toString());
                    entityProfile.addAttribute("long", coordinatePair.getSecond().toString());
                }

                for (Location location : event.getLocations()) {
                    for (String label : location.getLabels()) {
                        entityProfile.addAttribute("locationLabel", label);
                    }
                    for (Pair<Double, Double> coordinatePair : location.getCoordinates()) {
                        entityProfile.addAttribute("locationLat", coordinatePair.getFirst().toString());
                        entityProfile.addAttribute("locationLong", coordinatePair.getSecond().toString());
                    }
                    for (String same : location.getSames()) {
                        entityProfile.addAttribute("locationSame", same);
                    }
                }
            }

            list.add(entityProfile);
            //add to ID HashMap
            ids.put(eventURI, idCounter);
            idCounter++;

        }
        return list;

    }

    /**
     * Strip URI prefix
     * @param uri
     * @return uri without prefix
     */
    private static String stripPrefix(String uri) {
        if (uri.contains("resource/")) {
            uri = uri.substring(uri.indexOf("resource/") + 9, uri.length());
        }
        return uri;
    }

    private static Set<IdDuplicates> convertGoldStandardToIdDuplicatesSet(String filePath) {
        Set<IdDuplicates> duplicatesSet = new HashSet<>();
        try {
            CSVReader reader = new CSVReader(new FileReader(new File(filePath)), '\t', '\"' , 0);

            String[] lineValues;
            //HashMap<instanceURI, HashSet<lineValues>>
            while ((lineValues = reader.readNext()) != null) {
                if (lineValues.length>2) {//check for complete line
                    if (lineValues[2].equals("true")) {//check for duplicate matches
                        String dURI = lineValues[0];
                        String yURI = lineValues[1];
                        if (dIDs.containsKey(dURI) && yIDs.containsKey(yURI)) {//check if URIs are contained in the data sets
                            IdDuplicates idDuplicates = new IdDuplicates(dIDs.get(dURI), yIDs.get(yURI));
                            duplicatesSet.add(idDuplicates);
                        }//done adding idDuplicates
                    }//"false"
                }//lineValues.length <=2
            }//processed all lines

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return duplicatesSet;
    }

    public static void runBlocking(FusableDataSet<Event, DefaultSchemaElement> dataSetD, FusableDataSet<Event, DefaultSchemaElement> dataSetY, String goldStandardFilePath) {

        try {
            String methodName = "";
            Date date = new Date();
            String fileName = "blocking_" + df.format(date) + ".csv";
            BufferedWriter writer = new BufferedWriter(new FileWriter("./out/"+fileName));
            String header = "#iD, #iY, #iD*#iY , #attr, attr, methodId, methodName, #blocks, total comparisons, avg comparisons/block, PC, PQ, RR, a (RR*PC), BlBu time, BlCl time, CoCl time";
            writer.write(header + "\n");

            for  (int datasetID = 0; datasetID < NO_OF_DATASETS; datasetID++){
                String[] dataSetAttributes = getDatasetAttributes(datasetID);

                //params
                List<Double>[] comparisons = new List[NO_OF_BLOCKING_METHODS];
                List<Double>[] pc = new List[NO_OF_BLOCKING_METHODS];
                List<Double>[] pq = new List[NO_OF_BLOCKING_METHODS];
                List<Double>[] rr = new List[NO_OF_BLOCKING_METHODS];
                List<Double>[] nBlocks = new List[NO_OF_BLOCKING_METHODS];
                List<Double>[] blbuTimes = new List[NO_OF_BLOCKING_METHODS];
                List<Double>[] blclTimes = new List[NO_OF_BLOCKING_METHODS];
                List<Double>[] coclTimes = new List[NO_OF_BLOCKING_METHODS];

                //init HashMaps with <URI, ID>
                dIDs = new HashMap<>(dataSetD.size() + 10, 1.0f);
                yIDs = new HashMap<>(dataSetY.size() + 10, 1.0f);

                //convert datasets
                List<EntityProfile>[] profiles = new List[2];
                profiles[0] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetD, dIDs, datasetID);
                profiles[1] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetY, yIDs, datasetID);
                int dSize = profiles[0].size();
                int ySize = profiles[1].size();

                //gold standard
                Set<IdDuplicates> goldStandardMatches = WDI_BlockingFramework_Combiner.convertGoldStandardToIdDuplicatesSet(goldStandardFilePath);
                AbstractDuplicatePropagation abp = new BilateralDuplicatePropagation(goldStandardMatches);


                for (int methodId = 0; methodId < NO_OF_BLOCKING_METHODS; methodId++) {
                    comparisons[methodId] = new ArrayList<>();
                    pc[methodId] = new ArrayList<>();
                    pq[methodId] = new ArrayList<>();
                    rr[methodId] = new ArrayList<>();
                    nBlocks[methodId] = new ArrayList<>();
                    blbuTimes[methodId] = new ArrayList<>();
                    blclTimes[methodId] = new ArrayList<>();
                    coclTimes[methodId] = new ArrayList<>();
                }

                for (int iterations = 0; iterations < NO_OF_ITERATIONS; iterations++) {
                    for (int methodId = 0; methodId < NO_OF_BLOCKING_METHODS; methodId++) {

                        AbstractBlockingMethod method = null;
                        AbstractEfficiencyMethod bcMethod = null;
                        AbstractEfficiencyMethod ccMethod = null;

                        switch (methodId) {
                            case 0:
                                method = new TokenBlocking(profiles);
                                methodName = "StBl";
                                break;
                            case 1:
                                method = new TokenBlocking(profiles);
                                bcMethod = new BlockFiltering(tbBestRatios);
                                methodName = "StBl+BlFi(r="+tbBestRatios+")";
                                break;
                            case 2:
                                method = new TokenBlocking(profiles);
                                bcMethod = new BlockFiltering(tbBestRatios);
                                ccMethod = OnTheFlyUtilities.getCleaningMethod(true, tbBestMbMethodId, abp);
                                methodName = "StBl+BlFi(r="+tbBestRatios+")+MeBl(" + getPruningAlgorithmName(tbBestMbMethodId) + "-" + getWeightingSchemeName(tbBestMbMethodId) +")";
                                break;
                            case 3:
                                method = new AttributeClusteringBlocking(ACL_BEST_MODEL, profiles);
                                methodName = "ACl("+ ACL_BEST_MODEL.toString() +")";
                                break;
                        }
                        //BLOCK BUILDING
                        long time1 = System.currentTimeMillis();
                        List<AbstractBlock> blocks = method.buildBlocks();
                        long time2 = System.currentTimeMillis();

                        //BLOCK CLEANING
                        if (bcMethod != null) {
                            bcMethod.applyProcessing(blocks);
                        }
                        long time3 = System.currentTimeMillis();

                        //COMPARISON CLEANING
                        if (ccMethod != null) {
                            ccMethod.applyProcessing(blocks);
                        }
                        long time4 = System.currentTimeMillis();


                        double blbuTime = time2 - time1;
                        System.out.println("BlBu time\t:\t" + blbuTime);
                        double blclTime = time3 - time2;
                        System.out.println("BlCl time\t:\t" + blclTime);
                        double coclTime = time4 - time3;
                        System.out.println("CoCl time\t:\t" + coclTime);

                        BlockStatistics bStats = new BlockStatistics(blocks, abp);
                        double[] results = bStats.applyProcessing();

                        comparisons[methodId].add(results[2]);
                        pc[methodId].add(results[0]);
                        pq[methodId].add(results[1]);
                        rr[methodId].add(results[3]);
                        nBlocks[methodId].add(results[4]);
                        blbuTimes[methodId].add(blbuTime);
                        blclTimes[methodId].add(blclTime);
                        coclTimes[methodId].add(coclTime);
                    }

                }
                for (int methodId = 0; methodId < NO_OF_BLOCKING_METHODS; methodId++) {
                    printOutputAndWriteToFile(writer, dSize, ySize, dataSetAttributes, methodName, methodId, datasetID, nBlocks, comparisons, pc, pq, rr, blbuTimes, blclTimes, coclTimes, -1.0 , -1);
                }
            } //end for NUMBER_OF_DATASETS
            closeWriter(writer, fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Analyze Block Building for Blocking:
     * 0: Standard (Token)
     * 1: Attribute Clustering
     * Test-runs and several iterations
     * @param dataSetD
     * @param dataSetY
     * @param goldStandardFilePath
     */
    public void analyzeRuntime_BlBu(int m, FusableDataSet<Event, DefaultSchemaElement> dataSetD, FusableDataSet<Event, DefaultSchemaElement> dataSetY, String goldStandardFilePath) {
        //datasets
        dIDs = new HashMap<>(dataSetD.size() + 10, 1.0f);
        yIDs = new HashMap<>(dataSetY.size() + 10, 1.0f);
        //convert datasets
        List<EntityProfile>[] profiles = new List[2];
        profiles[0] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetD, dIDs, 0);
        profiles[1] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetY, yIDs, 0);

        //method
        String methodName = "";
        AbstractBlockingMethod method = null;
        //int methodId = 0;
        if (m==0) {
            methodName = "StBl";
            method = new TokenBlocking(profiles);
        } else if (m==1) {
            //methodId = 3;
            method = new AttributeClusteringBlocking(ACL_BEST_MODEL, profiles);
            methodName = "ACl("+ ACL_BEST_MODEL.toString() + ")";
        }
        try {
            Date date = new Date();
            String fileName = "runtimeBlBu_" + methodName +  "_" + df.format(date) + ".csv";
            BufferedWriter writer = new BufferedWriter(new FileWriter("./out/"+fileName));
            String header = "#iD, #iY, #iD*#iY , #attr, attr, methodId, methodName, #blocks, total comparisons, avg comparisons/block, PC, PQ, RR, a (RR*PC), BlBu time, BlCl time, CoCl time";
            writer.write(header + "\n");

            //INIT ALL VARIABLES & test run
            String[] dataSetAttributes = getDatasetAttributes(0);
            //params
            List<Double>[] comparisons;
            List<Double>[] pc;
            List<Double>[] pq;
            List<Double>[] rr;
            List<Double>[] nBlocks;
            List<Double>[] blbuTimes;


            int dSize = 0;
            int ySize = 0;
            //gold standard
            Set<IdDuplicates> goldStandardMatches = WDI_BlockingFramework_Combiner.convertGoldStandardToIdDuplicatesSet(goldStandardFilePath);
            AbstractDuplicatePropagation abp = new BilateralDuplicatePropagation(goldStandardMatches);


            List<AbstractBlock> blocks;
            BlockStatistics bStats;
            double[] results;
            for (int i= 0; i < NO_OF_TEST_RUNS; i++) {
                blocks = method.buildBlocks();
                bStats = new BlockStatistics(blocks, abp);
                results = bStats.applyProcessing();
            }
            double blbuTime;
            long time1;
            long time2;
            System.out.println("Test run complete");

            for (int datasetID = 0; datasetID < NO_OF_DATASETS; datasetID++) {
                dataSetAttributes = getDatasetAttributes(datasetID);
                //params
                comparisons = new List[1];
                pc = new List[1];
                pq = new List[1];
                rr = new List[1];
                nBlocks = new List[1];
                blbuTimes = new List[1];
                comparisons[0] = new ArrayList<>();
                pc[0] = new ArrayList<>();
                pq[0] = new ArrayList<>();
                rr[0] = new ArrayList<>();
                nBlocks[0] = new ArrayList<>();
                blbuTimes[0] = new ArrayList<>();

                //init HashMaps with <URI, ID>
                dIDs = new HashMap<>(dataSetD.size() + 10, 1.0f);
                yIDs = new HashMap<>(dataSetY.size() + 10, 1.0f);

                //convert datasets
                profiles = new List[2];
                profiles[0] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetD, dIDs, datasetID);
                profiles[1] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetY, yIDs, datasetID);
                dSize = profiles[0].size();
                ySize = profiles[1].size();

                //gold standard
                goldStandardMatches = WDI_BlockingFramework_Combiner.convertGoldStandardToIdDuplicatesSet(goldStandardFilePath);
                abp = new BilateralDuplicatePropagation(goldStandardMatches);

                //init block building method
                if (m==0) {
                    method = new TokenBlocking(profiles);
                } else if (m==1) {
                    method = new AttributeClusteringBlocking(ACL_BEST_MODEL, profiles);
                }

                for (int iterations = 0; iterations < NO_OF_ITERATIONS; iterations++) {

                    //BLOCK BUILDING
                    time1 = System.currentTimeMillis();
                    blocks = method.buildBlocks();
                    time2 = System.currentTimeMillis();
                    blbuTime = time2 - time1;
                    //System.out.println("BlBu time\t:\t" + blbuTime);

                    bStats = new BlockStatistics(blocks, abp);
                    results = bStats.applyProcessing();

                    comparisons[0].add(results[2]);
                    pc[0].add(results[0]);
                    pq[0].add(results[1]);
                    rr[0].add(results[3]);
                    nBlocks[0].add(results[4]);
                    blbuTimes[0].add(blbuTime);

                } //end for NUMBER_OF_ITERATIONS
                printOutputAndWriteToFile(writer, dSize, ySize, dataSetAttributes, methodName, 0, datasetID, nBlocks, comparisons, pc, pq, rr, blbuTimes, null, null, -1.0, -1);
            } //end for NUMBER_OF_DATASETS
            closeWriter(writer, fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /**
     * Analyze Block Filtering ratios
     * One run for each parameter. Runtime might not be accurate (no iterations and no test runs)
     * @param dataSetD
     * @param dataSetY
     * @param goldStandardFilePath
     */
    public void getBestParameterForBlFi(int m, FusableDataSet<Event, DefaultSchemaElement> dataSetD, FusableDataSet<Event, DefaultSchemaElement> dataSetY, String goldStandardFilePath) {
        try {
            String methodName = "BlFi";
            String blbiM = getBlBiMethodName(m);
            Date date = new Date();
            String fileName = "bestParam_"+ methodName +"_BlBi_"+ blbiM +"_" + df.format(date) + ".csv";
            BufferedWriter writer = new BufferedWriter(new FileWriter("./out/"+fileName));
            String header = "#iD, #iY, #iD*#iY , #attr, attr, methodId, methodName, #blocks, total comparisons, avg comparisons/block, PC, PQ, RR, a (RR*PC), BlBu time, BlCl time, CoCl time";
            writer.write(header + "\n");

            //init all variables & test run
            String[] dataSetAttributes = getDatasetAttributes(0);
            //params
            List<Double>[] comparisons = new List[1];
            List<Double>[] pc = new List[1];
            List<Double>[] pq = new List[1];
            List<Double>[] rr = new List[1];
            List<Double>[] nBlocks = new List[1];;
            List<Double>[] blbuTimes = new List[1];
            List<Double>[] blclTimes = new List[1];

            //init HashMaps with <URI, ID>
            dIDs = new HashMap<>(dataSetD.size() + 10, 1.0f);
            yIDs = new HashMap<>(dataSetY.size() + 10, 1.0f);

            //convert datasets
            List<EntityProfile>[] profiles = new List[2];
            profiles[0] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetD, dIDs, 0);
            profiles[1] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetY, yIDs, 0);
            int dSize;
            int ySize;

            //gold standard
            Set<IdDuplicates> goldStandardMatches = WDI_BlockingFramework_Combiner.convertGoldStandardToIdDuplicatesSet(goldStandardFilePath);
            AbstractDuplicatePropagation abp = new BilateralDuplicatePropagation(goldStandardMatches);

            double r = 0.5;

            AbstractBlockingMethod method = new TokenBlocking(profiles);
            AbstractEfficiencyMethod bcMethod = new BlockFiltering(r);
            List<AbstractBlock> blocks;
            BlockStatistics bStats;
            double[] results;

            /*for (int i= 0; i < NO_OF_TEST_RUNS; i++) {
                blocks = method.buildBlocks();
                bcMethod.applyProcessing(blocks);
                bStats = new BlockStatistics(blocks, abp);
                results = bStats.applyProcessing();
            }*/

            double blbuTime;
            double blclTime;
            long time1;
            long time2;
            long time3;

            System.out.println("Test run complete.");

            for  (int datasetID = 0; datasetID < NO_OF_DATASETS; datasetID++) {
                dataSetAttributes = getDatasetAttributes(datasetID);

                //init HashMaps with <URI, ID>
                dIDs = new HashMap<>(dataSetD.size() + 10, 1.0f);
                yIDs = new HashMap<>(dataSetY.size() + 10, 1.0f);

                //convert datasets
                profiles = new List[2];
                profiles[0] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetD, dIDs, datasetID);
                profiles[1] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetY, yIDs, datasetID);
                dSize = profiles[0].size();
                ySize = profiles[1].size();

                //gold standard
                goldStandardMatches = WDI_BlockingFramework_Combiner.convertGoldStandardToIdDuplicatesSet(goldStandardFilePath);
                abp = new BilateralDuplicatePropagation(goldStandardMatches);

                if (m==0) {
                    method = new TokenBlocking(profiles);
                } else if (m==1) {
                    method = new AttributeClusteringBlocking(ACL_BEST_MODEL, profiles);
                }

                //ANALYZE BLOCK-REFINEMENT METHODS

                for (int i = 0; i < 20; i++) {
                    //init Block Filtering (BlFi) parameters
                    r = getRatio(i);
                    methodName = blbiM + "+BlFi (r=" + r + ")";
                    bcMethod = new BlockFiltering(r);

                    //params
                    comparisons = new List[1];
                    pc = new List[1];
                    pq = new List[1];
                    rr = new List[1];
                    nBlocks = new List[1];
                    blbuTimes = new List[1];
                    blclTimes = new List[1];
                    comparisons[0] = new ArrayList<>();
                    pc[0] = new ArrayList<>();
                    pq[0] = new ArrayList<>();
                    rr[0] = new ArrayList<>();
                    nBlocks[0] = new ArrayList<>();
                    blbuTimes[0] = new ArrayList<>();
                    blclTimes[0] = new ArrayList<>();

                    //for (int iterations = 0; iterations < NO_OF_ITERATIONS; iterations++) {

                    //BLOCK BUILDING
                    time1 = System.currentTimeMillis();
                    blocks = method.buildBlocks();
                    time2 = System.currentTimeMillis();
                    //BLOCK CLEANING
                    if (bcMethod != null) {
                        bcMethod.applyProcessing(blocks);
                    }
                    time3 = System.currentTimeMillis();

                    blbuTime = time2 - time1;
                    System.out.println("BlBu time\t:\t" + blbuTime);
                    blclTime = time3 - time2;
                    System.out.println("BlCl time\t:\t" + blclTime);

                    bStats = new BlockStatistics(blocks, abp);
                    results = bStats.applyProcessing();

                    comparisons[0].add(results[2]);
                    pc[0].add(results[0]);
                    pq[0].add(results[1]);
                    rr[0].add(results[3]);
                    nBlocks[0].add(results[4]);
                    blbuTimes[0].add(blbuTime);
                    blclTimes[0].add(blclTime);
                //}//end for iterations

                    printOutputAndWriteToFile(writer, dSize, ySize, dataSetAttributes, methodName, 0, datasetID, nBlocks, comparisons, pc, pq, rr, blbuTimes, blclTimes, null, r, -1);
                }
            } //end for NUMBER_OF_DATASETS
            closeWriter(writer, fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getBlBiMethodName(int m) {
        if (m==0) {
            return "StBl";
        } else if (m==1) {
            return "ACl("+ ACL_BEST_MODEL.toString() + ")";
        }
        return "null";
    }

    /**
     * Analyze Block Filtering time for best ratios (for Standard (Token) Blocking or Attribute Clustering)
     * Test-runs and several iterations
     * @param dataSetD
     * @param dataSetY
     * @param goldStandardFilePath
     */
    public void analyzeRuntimeForBlFi(int m, FusableDataSet<Event, DefaultSchemaElement> dataSetD, FusableDataSet<Event, DefaultSchemaElement> dataSetY, String goldStandardFilePath) {
        try {
            String methodName = "BlFi";
            String blBiMethod = getBlBiMethodName(m);
            Date date = new Date();
            String fileName = "runtimeBlFi_"+ blBiMethod +"_best"+ methodName + "_" + df.format(date) + ".csv";
            BufferedWriter writer = new BufferedWriter(new FileWriter("./out/"+fileName));
            String header = "#iD, #iY, #iD*#iY , #attr, attr, methodId, methodName, #blocks, total comparisons, avg comparisons/block, PC, PQ, RR, a (RR*PC), BlBu time, BlCl time, CoCl time";
            writer.write(header + "\n");

            //init all variables & test run
            String[] dataSetAttributes = getDatasetAttributes(0);
            //params
            List<Double>[] comparisons = new List[1];
            List<Double>[] pc = new List[1];
            List<Double>[] pq = new List[1];
            List<Double>[] rr = new List[1];
            List<Double>[] nBlocks = new List[1];;
            List<Double>[] blbuTimes = new List[1];
            List<Double>[] blclTimes = new List[1];

            //init HashMaps with <URI, ID>
            dIDs = new HashMap<>(dataSetD.size() + 10, 1.0f);
            yIDs = new HashMap<>(dataSetY.size() + 10, 1.0f);

            //convert datasets
            List<EntityProfile>[] profiles = new List[2];
            profiles[0] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetD, dIDs, 0);
            profiles[1] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetY, yIDs, 0);
            int dSize;
            int ySize;

            //gold standard
            Set<IdDuplicates> goldStandardMatches = WDI_BlockingFramework_Combiner.convertGoldStandardToIdDuplicatesSet(goldStandardFilePath);
            AbstractDuplicatePropagation abp = new BilateralDuplicatePropagation(goldStandardMatches);

            double r = 0.5;

            AbstractBlockingMethod method = null;
            if (m==0){
                method = new TokenBlocking(profiles);
            }  else if (m==1) {
                method = new AttributeClusteringBlocking(ACL_BEST_MODEL, profiles);
            }
            AbstractEfficiencyMethod bcMethod = new BlockFiltering(r);
            List<AbstractBlock> blocks;
            BlockStatistics bStats;
            double[] results;

            for (int i= 0; i < NO_OF_TEST_RUNS; i++) {
                blocks = method.buildBlocks();
                bcMethod.applyProcessing(blocks);
                bStats = new BlockStatistics(blocks, abp);
                results = bStats.applyProcessing();
            }

            double blbuTime;
            double blclTime;
            long time1;
            long time2;
            long time3;

            System.out.println("Test run complete.");

            for  (int datasetID = 0; datasetID < NO_OF_DATASETS; datasetID++){
                dataSetAttributes = getDatasetAttributes(datasetID);

                //init HashMaps with <URI, ID>
                dIDs = new HashMap<>(dataSetD.size() + 10, 1.0f);
                yIDs = new HashMap<>(dataSetY.size() + 10, 1.0f);

                //convert datasets
                profiles = new List[2];
                profiles[0] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetD, dIDs, datasetID);
                profiles[1] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetY, yIDs, datasetID);
                dSize = profiles[0].size();
                ySize = profiles[1].size();

                //gold standard
                goldStandardMatches = WDI_BlockingFramework_Combiner.convertGoldStandardToIdDuplicatesSet(goldStandardFilePath);
                abp = new BilateralDuplicatePropagation(goldStandardMatches);
                if (m==0) {
                    method = new TokenBlocking(profiles);
                } else if (m==1) {
                    method = new AttributeClusteringBlocking(ACL_BEST_MODEL, profiles);
                }

                //ANALYZE BLOCK-REFINEMENT METHODS


                //init Block Filtering (BlFi) parameters
                r = getBestBlFiRatio(m, datasetID);
                bcMethod = new BlockFiltering(r);
                methodName = blBiMethod + "+BlFi (r=" + r + ")";
                //params
                comparisons = new List[1];
                pc = new List[1];
                pq = new List[1];
                rr = new List[1];
                nBlocks = new List[1];
                blbuTimes = new List[1];
                blclTimes = new List[1];
                comparisons[0] = new ArrayList<>();
                pc[0] = new ArrayList<>();
                pq[0] = new ArrayList<>();
                rr[0] = new ArrayList<>();
                nBlocks[0] = new ArrayList<>();
                blbuTimes[0] = new ArrayList<>();
                blclTimes[0] = new ArrayList<>();

                for (int iterations = 0; iterations < NO_OF_ITERATIONS; iterations++) {
                    //BLOCK BUILDING
                    time1 = System.currentTimeMillis();
                    blocks = method.buildBlocks();
                    time2 = System.currentTimeMillis();
                    //BLOCK CLEANING
                    if (bcMethod != null) {
                        bcMethod.applyProcessing(blocks);
                    }
                    time3 = System.currentTimeMillis();

                    blbuTime = time2 - time1;
                    System.out.println("BlBu time\t:\t" + blbuTime);
                    blclTime = time3 - time2;
                    System.out.println("BlCl time\t:\t" + blclTime);

                    bStats = new BlockStatistics(blocks, abp);
                    results = bStats.applyProcessing();

                    comparisons[0].add(results[2]);
                    pc[0].add(results[0]);
                    pq[0].add(results[1]);
                    rr[0].add(results[3]);
                    nBlocks[0].add(results[4]);
                    blbuTimes[0].add(blbuTime);
                    blclTimes[0].add(blclTime);
                }//end for iterations
                printOutputAndWriteToFile(writer, dSize, ySize, dataSetAttributes, methodName, 0, datasetID, nBlocks, comparisons, pc, pq, rr, blbuTimes, blclTimes, null, r, -1);
            } //end for NUMBER_OF_DATASETS
            closeWriter(writer, fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Analyze Meta Blocking algorithms and weighting schemes for Standard (Token) Blocking or Attribute Clustering with best Block Filtering
     * One run for each parameter. Runtime might not be accurate (no iterations and no test runs)
     * @param m BlockBuilding method
     * @param dataSetD
     * @param dataSetY
     * @param goldStandardFilePath
     */
    public void getBestParameterForMeBl_bestBlFi(int m, FusableDataSet<Event, DefaultSchemaElement> dataSetD, FusableDataSet<Event, DefaultSchemaElement> dataSetY, String goldStandardFilePath) {
        try {
            String methodName =  "BlFi";
            String blBiMethod = getBlBiMethodName(m);
            Date date = new Date();
            String fileName = "bestParam_MeBl_preSteps_"+blBiMethod+"_best" + methodName + "_" + df.format(date) + ".csv";
            BufferedWriter writer = new BufferedWriter(new FileWriter("./out/"+fileName));
            String header = "#iD, #iY, #iD*#iY , #attr, attr, methodId, methodName, #blocks, total comparisons, avg comparisons/block, PC, PQ, RR, a (RR*PC), BlBu time, BlCl time, CoCl time";
            writer.write(header + "\n");

            //init all variables & test run
            //params
            List<Double>[] comparisons = new List[1];
            List<Double>[] pc = new List[1];
            List<Double>[] pq = new List[1];
            List<Double>[] rr = new List[1];
            List<Double>[] nBlocks = new List[1];
            List<Double>[] blbuTimes = new List[1];
            List<Double>[] blclTimes = new List[1];
            List<Double>[] coclTimes = new List[1];

            //init HashMaps with <URI, ID>
            dIDs = new HashMap<>(dataSetD.size() + 10, 1.0f);
            yIDs = new HashMap<>(dataSetY.size() + 10, 1.0f);

            //convert datasets
            List<EntityProfile>[] profiles = new List[2];
            profiles[0] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetD, dIDs, 0);
            profiles[1] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetY, yIDs, 0);
            int dSize;
            int ySize;

            //gold standard
            Set<IdDuplicates> goldStandardMatches = WDI_BlockingFramework_Combiner.convertGoldStandardToIdDuplicatesSet(goldStandardFilePath);
            AbstractDuplicatePropagation abp = new BilateralDuplicatePropagation(goldStandardMatches);

            AbstractBlockingMethod method = null;
            if (m==0) {
                method = new TokenBlocking(profiles);
            } else if (m==1) {
                method = new AttributeClusteringBlocking(ACL_BEST_MODEL, profiles);
            }
            double r = getBestBlFiRatio(m, 0);
            AbstractEfficiencyMethod bcMethod = new BlockFiltering(r);
            AbstractEfficiencyMethod ccMethod = OnTheFlyUtilities.getCleaningMethod(true, 0, abp);

            List<AbstractBlock> blocks;
            BlockStatistics bStats;
            double[] results;

            //for (int i= 0; i < NO_OF_TEST_RUNS; i++) {
                blocks = method.buildBlocks();
                bcMethod.applyProcessing(blocks);
                ccMethod.applyProcessing(blocks);
                bStats = new BlockStatistics(blocks, abp);
                results = bStats.applyProcessing();
           // }
            //get all cleaning methods once
            /*for (int i = 0; i < 40; i++) {
                ccMethod = OnTheFlyUtilities.getCleaningMethod(true, i, abp);
            }*/
            double blbuTime;
            double blclTime;
            double coclTime;
            long time1;
            long time2;
            long time3;
            long time4;

            System.out.println("Test run complete.");

            for  (int datasetID = 0; datasetID < NO_OF_DATASETS; datasetID++){
                String[] dataSetAttributes = getDatasetAttributes(datasetID);

                //params
                comparisons = new List[1];
                pc = new List[1];
                pq = new List[1];
                rr = new List[1];
                nBlocks = new List[1];
                blbuTimes = new List[1];
                blclTimes = new List[1];
                coclTimes = new List[1];

                //init HashMaps with <URI, ID>
                dIDs = new HashMap<>(dataSetD.size() + 10, 1.0f);
                yIDs = new HashMap<>(dataSetY.size() + 10, 1.0f);

                //convert datasets
                profiles = new List[2];
                profiles[0] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetD, dIDs, datasetID);
                profiles[1] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetY, yIDs, datasetID);
                dSize = profiles[0].size();
                ySize = profiles[1].size();

                //gold standard
                goldStandardMatches = WDI_BlockingFramework_Combiner.convertGoldStandardToIdDuplicatesSet(goldStandardFilePath);
                abp = new BilateralDuplicatePropagation(goldStandardMatches);
                if (m==0) {
                    method = new TokenBlocking(profiles);
                } else if (m==1) {
                    method = new AttributeClusteringBlocking(ACL_BEST_MODEL, profiles);
                }
                r = getBestBlFiRatio(m,datasetID);
                bcMethod = new BlockFiltering(r);

                //ANALYZE META BLOCKING
                //Six algorithms and five weighting schemes
                for (int i = 0; i < 40; i++) {
                    //exclude meta blocking algorithms that are not working: 0-5, 20-30,35-40
                    if ((i > 4 && i < 20) || (i > 29 && i < 35)) {
                        methodName = blBiMethod+"+BlFi(r="+r+")+MeBl(" + getPruningAlgorithmName(i) + "-" + getWeightingSchemeName(i) +")";

                        comparisons[0] = new ArrayList<>();
                        pc[0] = new ArrayList<>();
                        pq[0] = new ArrayList<>();
                        rr[0] = new ArrayList<>();
                        nBlocks[0] = new ArrayList<>();
                        blbuTimes[0] = new ArrayList<>();
                        blclTimes[0] = new ArrayList<>();
                        coclTimes[0] = new ArrayList<>();
                        //init CoCl parameters
                        ccMethod = OnTheFlyUtilities.getCleaningMethod(true, i, abp);

                        //BLOCK BUILDING
                        time1 = System.currentTimeMillis();
                        blocks = method.buildBlocks();
                        time2 = System.currentTimeMillis();
                        //BLOCK CLEANING
                        if (bcMethod != null) {
                            bcMethod.applyProcessing(blocks);
                        }
                        time3 = System.currentTimeMillis();
                        //COMPARISON CLEANING
                        if (ccMethod != null) {
                            ccMethod.applyProcessing(blocks);
                        }
                        time4 = System.currentTimeMillis();

                        blbuTime = time2 - time1;
                        System.out.println("BlBu time\t:\t" + blbuTime);
                        blclTime = time3 - time2;
                        System.out.println("BlCl time\t:\t" + blclTime);
                        coclTime = time4 - time3;
                        System.out.println("CoCl time\t:\t" + coclTime);

                        bStats = new BlockStatistics(blocks, abp);
                        results = bStats.applyProcessing();

                        comparisons[0].add(results[2]);
                        pc[0].add(results[0]);
                        pq[0].add(results[1]);
                        rr[0].add(results[3]);
                        nBlocks[0].add(results[4]);
                        blbuTimes[0].add(blbuTime);
                        blclTimes[0].add(blclTime);
                        coclTimes[0].add(coclTime);

                        printOutputAndWriteToFile(writer, dSize, ySize, dataSetAttributes, methodName, 0, datasetID, nBlocks, comparisons, pc, pq, rr, blbuTimes, blclTimes, coclTimes, r, i);
                    }
                }
            } //end for NUMBER_OF_DATASETS
            closeWriter(writer, fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Analyze best Meta Blocking algorithms and best weighting schemes for Standard (Token) Blocking with best Block Filtering
     * Test-runs and several iterations
     * @param dataSetD
     * @param dataSetY
     * @param goldStandardFilePath
     */
    public void analyzeRuntimeForMeBl_preSteps_StBl_bestBlFi(int m,FusableDataSet<Event, DefaultSchemaElement> dataSetD, FusableDataSet<Event, DefaultSchemaElement> dataSetY, String goldStandardFilePath) {
        try {
            String methodName = "MeBl";
            String blBiMethod = getBlBiMethodName(m);
            Date date = new Date();
            String fileName = "runtimeMeBl_"+blBiMethod+"_bestBlFi_best" + methodName + "_" + df.format(date) + ".csv";
            BufferedWriter writer = new BufferedWriter(new FileWriter("./out/"+fileName));
            String header = "#iD, #iY, #iD*#iY , #attr, attr, methodId, methodName, #blocks, total comparisons, avg comparisons/block, PC, PQ, RR, a (RR*PC), BlBu time, BlCl time, CoCl time";
            writer.write(header + "\n");

            //init all variables & test run
            //params
            List<Double>[] comparisons = new List[1];
            List<Double>[] pc = new List[1];
            List<Double>[] pq = new List[1];
            List<Double>[] rr = new List[1];
            List<Double>[] nBlocks = new List[1];
            List<Double>[] blbuTimes = new List[1];
            List<Double>[] blclTimes = new List[1];
            List<Double>[] coclTimes = new List[1];

            //init HashMaps with <URI, ID>
            dIDs = new HashMap<>(dataSetD.size() + 10, 1.0f);
            yIDs = new HashMap<>(dataSetY.size() + 10, 1.0f);

            //convert datasets
            List<EntityProfile>[] profiles = new List[2];
            profiles[0] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetD, dIDs, 0);
            profiles[1] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetY, yIDs, 0);
            int dSize;
            int ySize;

            //gold standard
            Set<IdDuplicates> goldStandardMatches = WDI_BlockingFramework_Combiner.convertGoldStandardToIdDuplicatesSet(goldStandardFilePath);
            AbstractDuplicatePropagation abp = new BilateralDuplicatePropagation(goldStandardMatches);

            AbstractBlockingMethod method = null;
            if (m==0) {
                method = new TokenBlocking(profiles);
            } else if (m==1) {
                method = new AttributeClusteringBlocking(ACL_BEST_MODEL, profiles);
            }
            double r = getBestBlFiRatio(m,0);
            AbstractEfficiencyMethod bcMethod = new BlockFiltering(r);
            AbstractEfficiencyMethod ccMethod = OnTheFlyUtilities.getCleaningMethod(true, 0, abp);

            List<AbstractBlock> blocks;
            BlockStatistics bStats;
            double[] results;

            for (int i= 0; i < NO_OF_TEST_RUNS; i++) {
                blocks = method.buildBlocks();
                bcMethod.applyProcessing(blocks);
                ccMethod.applyProcessing(blocks);
                bStats = new BlockStatistics(blocks, abp);
                results = bStats.applyProcessing();
            }
            //get all cleaning methods once
            for (int i = 0; i < 5; i++) {
                int bMB = getBestMeBlMethod(m, i);
                ccMethod = OnTheFlyUtilities.getCleaningMethod(true, bMB, abp);
            }
            double blbuTime;
            double blclTime;
            double coclTime;
            long time1;
            long time2;
            long time3;
            long time4;

            System.out.println("Test run complete.");

            for  (int datasetID = 0; datasetID < NO_OF_DATASETS; datasetID++){
                String[] dataSetAttributes = getDatasetAttributes(datasetID);

                //params
                comparisons = new List[1];
                pc = new List[1];
                pq = new List[1];
                rr = new List[1];
                nBlocks = new List[1];
                blbuTimes = new List[1];
                blclTimes = new List[1];
                coclTimes = new List[1];
                comparisons[0] = new ArrayList<>();
                pc[0] = new ArrayList<>();
                pq[0] = new ArrayList<>();
                rr[0] = new ArrayList<>();
                nBlocks[0] = new ArrayList<>();
                blbuTimes[0] = new ArrayList<>();
                blclTimes[0] = new ArrayList<>();
                coclTimes[0] = new ArrayList<>();

                //init HashMaps with <URI, ID>
                dIDs = new HashMap<>(dataSetD.size() + 10, 1.0f);
                yIDs = new HashMap<>(dataSetY.size() + 10, 1.0f);

                //convert datasets
                profiles = new List[2];
                profiles[0] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetD, dIDs, datasetID);
                profiles[1] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetY, yIDs, datasetID);
                dSize = profiles[0].size();
                ySize = profiles[1].size();

                //gold standard
                goldStandardMatches = WDI_BlockingFramework_Combiner.convertGoldStandardToIdDuplicatesSet(goldStandardFilePath);
                abp = new BilateralDuplicatePropagation(goldStandardMatches);

                method = new TokenBlocking(profiles);
                r = getBestBlFiRatio(m,datasetID);
                bcMethod = new BlockFiltering(r);

                //ANALYZE META BLOCKING
                //Six algorithms and five weighting schemes
                int i = getBestMeBlMethod(m, datasetID);
                //init CoCl parameters
                ccMethod = OnTheFlyUtilities.getCleaningMethod(true, i, abp);

                methodName = blBiMethod+"+BlFi(r="+r+")+MeBl(" + getPruningAlgorithmName(i) + "-" + getWeightingSchemeName(i) +")";

                for (int iterations = 0; iterations < NO_OF_ITERATIONS; iterations++) {
                    //BLOCK BUILDING
                    time1 = System.currentTimeMillis();
                    blocks = method.buildBlocks();
                    time2 = System.currentTimeMillis();
                    //BLOCK CLEANING
                    if (bcMethod != null) {
                        bcMethod.applyProcessing(blocks);
                    }
                    time3 = System.currentTimeMillis();
                    //COMPARISON CLEANING
                    if (ccMethod != null) {
                        ccMethod.applyProcessing(blocks);
                    }
                    time4 = System.currentTimeMillis();

                    blbuTime = time2 - time1;
                    System.out.println("BlBu time\t:\t" + blbuTime);
                    blclTime = time3 - time2;
                    System.out.println("BlCl time\t:\t" + blclTime);
                    coclTime = time4 - time3;
                    System.out.println("CoCl time\t:\t" + coclTime);

                    bStats = new BlockStatistics(blocks, abp);
                    results = bStats.applyProcessing();

                    comparisons[0].add(results[2]);
                    pc[0].add(results[0]);
                    pq[0].add(results[1]);
                    rr[0].add(results[3]);
                    nBlocks[0].add(results[4]);
                    blbuTimes[0].add(blbuTime);
                    blclTimes[0].add(blclTime);
                    coclTimes[0].add(coclTime);
                }

                printOutputAndWriteToFile(writer, dSize, ySize, dataSetAttributes, methodName, 0, datasetID, nBlocks, comparisons, pc, pq, rr, blbuTimes, blclTimes, coclTimes, r, i);

            } //end for NUMBER_OF_DATASETS
            closeWriter(writer, fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    private static void closeWriter(BufferedWriter writer, String fileName) throws IOException {
        writer.close();
        System.out.println("results written to " + fileName);
    }

    private static void printOutputAndWriteToFile(BufferedWriter writer, int dSize, int ySize, String[] dataSetAttributes, String methodName, int methodId, int datasetId, List<Double>[] nBlocks, List<Double>[] comparisons, List<Double>[] pc, List<Double>[] pq, List<Double>[] rr, List<Double>[] blbuTimes, List<Double>[] blclTimes, List<Double>[] coclTimes, double r, int i) throws IOException {
        System.out.println("\nCurrent method id\t:\t" + (methodId + 1));
        System.out.println("Current method\t:\t" + methodName);
        System.out.println("Current datasetID\t:\t" + datasetId);
        System.out.println("Number of Blocks\t:\t" + StatisticsUtilities.getMeanValue(nBlocks[methodId]));
        System.out.println("Total Comparisons\t:\t" + StatisticsUtilities.getMeanValue(comparisons[methodId]));
        System.out.println("Average Comparisons per Block\t:\t" + StatisticsUtilities.getMeanValue(comparisons[methodId]) / StatisticsUtilities.getMeanValue(nBlocks[methodId]));
        System.out.println("PC\t:\t" + StatisticsUtilities.getMeanValue(pc[methodId]));
        System.out.println("PQ\t:\t" + StatisticsUtilities.getMeanValue(pq[methodId]));
        System.out.println("RR\t:\t" + StatisticsUtilities.getMeanValue(rr[methodId]));
        double meanBlBuTime = StatisticsUtilities.getMeanValue(blbuTimes[methodId]);
        System.out.println("Average BlBu Time\t:\t" + meanBlBuTime);
        double meanBlClTime = -1.0;
        if (blclTimes != null) {
            meanBlClTime = StatisticsUtilities.getMeanValue(blclTimes[methodId]);
            System.out.println("Average BlCl Time\t:\t" + meanBlClTime);
        }
        double meanColTime = -1.0;
        if (coclTimes != null) {
            meanColTime = StatisticsUtilities.getMeanValue(coclTimes[methodId]);
            System.out.println("Average CoCl Time\t:\t" + meanColTime);
        }
        String line = dSize + ", " + ySize + ", " + (long) dSize * (long) ySize + ", " +
                dataSetAttributes[0] + ", " +
                dataSetAttributes[1] + ", " +
                (methodId + 1) + ", " +
                methodName + ", " +
                StatisticsUtilities.getMeanValue(nBlocks[methodId]) + ", " +
                StatisticsUtilities.getMeanValue(comparisons[methodId]) + ", " +
                StatisticsUtilities.getMeanValue(comparisons[methodId]) / StatisticsUtilities.getMeanValue(nBlocks[methodId]) + ", " +
                StatisticsUtilities.getMeanValue(pc[methodId]) + ", " +
                StatisticsUtilities.getMeanValue(pq[methodId]) + ", " +
                StatisticsUtilities.getMeanValue(rr[methodId]) + ", " +
                StatisticsUtilities.getMeanValue(pc[methodId]) * StatisticsUtilities.getMeanValue(rr[methodId]) + ", " +
                meanBlBuTime + ", " +
                meanBlClTime + ", " +
                meanColTime;
        writer.write(line + "\n");
    }

    private static String getWeightingSchemeName(int r) {
        switch (r % 5) {
            case 0:
                return "ARCS";
            case 1:
                return "CBS";
            case 2:
                return "ECBS";
            case 3:
                return "JS";
            case 4:
                return "EJS";
            default:
                return "ERROR";
        }
    }
    private static String getPruningAlgorithmName(int r) {
        if (r < 5) {
            return "WEP";//not working
            //return "WeightedEdgePruning";
        } else if (r < 10) {
            return "CEP";
            //return "CardinalityEdgePruning";
        } else if (r < 15) {
            return "rWNP";
            //return "RedefinedWeightedNodePruning";
        } else if (r < 20) {
            return "recipCNP";
            //return "ReciprocalCardinalityNodePruning";
        } else if (r < 25) {
            return "rWNP";//not working
            //return "RedefinedWeightedNodePruning";
        } else if (r < 30) {
            return "recipWNP";//not working
            //return "ReciprocalWeightedNodePruning";
        } else if (r < 35) {
            return "pCNP";
            //return "PartitionCardinalityNodePruning";
        } else if (r < 40) {
            return "pWNP";//not working
            //return "PartitionWeightedNodePruning";
        } else {
            return "Error";
        }
    }


    private static String getMethodName(boolean p, int steps, int methodId, double r) { //}, int i, List<Double>[] blclTimes, List<Double>[] coclTimes) {

        //parameter tuning
        /*if (blclTimes == null) { //BlBu analysis
            return "ACl ("+ representationModels[i].toString() +")";
        }
        if (coclTimes == null) { //BlFi analysis
            if (r >= 0.0) {
                return "StBl+BlFi (r=" + r + ")";
            }
        } else { //MeBl
            return "StBl+BlFi(r="+r+")+MeBl(" + getPruningAlgorithmName(i) + "-" + getWeightingSchemeName(i) +")";
        }

        //Runtime with best model
        switch(methodId) {
            case 0:
                return "StBl";
            case 1:
                return "StBl+BlFi";
            case 2:
                return "StBl+BlFi+MeBl(ReCNP)";
            case 3:
                return "ACl ()";
        }*/
        return "methodId not known";
    }

    private static String[] getDatasetAttributes(int datasetID) {
        //dataSetAttributes[0] = numberOfAttributes
        //dataSetAttributes[1] = listOfAttributes
        String[] dataSetAttributes = new String[2];
        switch (datasetID) {
            case 0:
                dataSetAttributes[0] = "1";
                dataSetAttributes[1] = "stripedURI";
                break;
            case 1:
                dataSetAttributes[0] = "2";
                dataSetAttributes[1] = "stripedURI+label";
                break;
            case 2:
                dataSetAttributes[0] = "2";
                dataSetAttributes[1] = "stripedURI+sameAs";
                break;
            case 3:
                dataSetAttributes[0] = "3";
                dataSetAttributes[1] = "stripedURI+label+sameAs";
                break;
            case 4:
                dataSetAttributes[0] = "10";
                dataSetAttributes[1] = "all";
                break;
            default:
                System.out.println("datasetID not known");
        }
        return dataSetAttributes;
    }

    /**
     * Get the best cleaning method: rWNP with ARCS or CBS as weighting scheme
     * @param m
     * @param datasetID
     * @return
     */
    private int getBestMeBlMethod(int m, int datasetID) {
        if (datasetID == 0 || datasetID == 1) {// || datasetID == 4) {
            return 11; //10:rWNP-ARCS, 11:rWNP-CBS, 16: ReCNP-CBS, 19:ReCNP-EJS
        } else {
            return 10;
        }
    }

    /**
     * Get the best ratio for the Block Filtering method
     * @param m
     * @param datasetID
     * @return
     */
    private double getBestBlFiRatio(int m, int datasetID) {
        switch (datasetID) {
            case 0:
                return 0.5;
            case 1:
                return 0.5;
            case 2:
                return 0.4;
            case 3:
                return 0.4;
            case 4:
                if (m==0) {
                    return 0.5;
                } else if (m==1) {
                    return 0.55;
                }
        }
        return -1.0;
    }


    /**
     * Get ratio between 0.05 and 1.00 in steps of 0.05
     * @param i
     * @return
     */
    private double getRatio(int i) {
        switch (i) {
            case 0:
                return 0.05;
            case 1:
                return 0.1;
            case 2:
                return 0.15;
            case 3:
                return 0.2;
            case 4:
                return 0.25;
            case 5:
                return 0.3;
            case 6:
                return 0.35;
            case 7:
                return 0.4;
            case 8:
                return 0.45;
            case 9:
                return 0.5;
            case 10:
                return 0.55;
            case 11:
                return 0.6;
            case 12:
                return 0.65;
            case 13:
                return 0.7;
            case 14:
                return 0.75;
            case 15:
                return 0.8;
            case 16:
                return 0.85;
            case 17:
                return 0.9;
            case 18:
                return 0.95;
            case 19:
                return 1.0;
            default:
                return -1.0;
        }
    }


    public void getBestParameterForACl(FusableDataSet<Event, DefaultSchemaElement> dataSetD, FusableDataSet<Event, DefaultSchemaElement> dataSetY, String goldStandardFilePath) {
        try {
            String methodName = "";
            Date date = new Date();
            String fileName = "bestParam_ACl_" + df.format(date) + ".csv";
            BufferedWriter writer = new BufferedWriter(new FileWriter("./out/"+fileName));
            String header = "#iD, #iY, #iD*#iY , #attr, attr, methodId, methodName, #blocks, total comparisons, avg comparisons/block, PC, PQ, RR, a (RR*PC), BlBu time, BlCl time, CoCl time";
            writer.write(header + "\n");

            //init all variables & test run
            String[] dataSetAttributes = getDatasetAttributes(0);
            //params
            List<Double>[] comparisons = new List[1];
            List<Double>[] pc = new List[1];
            List<Double>[] pq = new List[1];
            List<Double>[] rr = new List[1];
            List<Double>[] nBlocks = new List[1];;
            List<Double>[] blbuTimes = new List[1];
            List<Double>[] blclTimes = new List[1];

            //init HashMaps with <URI, ID>
            dIDs = new HashMap<>(dataSetD.size() + 10, 1.0f);
            yIDs = new HashMap<>(dataSetY.size() + 10, 1.0f);

            //convert datasets
            List<EntityProfile>[] profiles = new List[2];
            profiles[0] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetD, dIDs, 0);
            profiles[1] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetY, yIDs, 0);
            int dSize;
            int ySize;

            //gold standard
            Set<IdDuplicates> goldStandardMatches;
            AbstractDuplicatePropagation abp;

            double r = -1;

            AbstractBlockingMethod method;
            List<AbstractBlock> blocks;
            BlockStatistics bStats;
            double[] results;

            double blbuTime;
            long time1;
            long time2;

            for  (int datasetID = 0; datasetID < NO_OF_DATASETS; datasetID++) {
                dataSetAttributes = getDatasetAttributes(datasetID);

                //init HashMaps with <URI, ID>
                dIDs = new HashMap<>(dataSetD.size() + 10, 1.0f);
                yIDs = new HashMap<>(dataSetY.size() + 10, 1.0f);

                //convert datasets
                profiles = new List[2];
                profiles[0] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetD, dIDs, datasetID);
                profiles[1] = WDI_BlockingFramework_Combiner.convertFusableDataSetToEntityProfile(dataSetY, yIDs, datasetID);
                dSize = profiles[0].size();
                ySize = profiles[1].size();

                //gold standard
                goldStandardMatches = WDI_BlockingFramework_Combiner.convertGoldStandardToIdDuplicatesSet(goldStandardFilePath);
                abp = new BilateralDuplicatePropagation(goldStandardMatches);


                //ANALYZE ACl METHODS
                for (int modelID = 0; modelID < representationModels.length; modelID++) {
                    method = new AttributeClusteringBlocking(representationModels[modelID], profiles);
                    methodName = "ACl ("+ representationModels[modelID].toString() + ")";
                    //params
                    comparisons = new List[1];
                    pc = new List[1];
                    pq = new List[1];
                    rr = new List[1];
                    nBlocks = new List[1];
                    blbuTimes = new List[1];
                    blclTimes = new List[1];
                    comparisons[0] = new ArrayList<>();
                    pc[0] = new ArrayList<>();
                    pq[0] = new ArrayList<>();
                    rr[0] = new ArrayList<>();
                    nBlocks[0] = new ArrayList<>();
                    blbuTimes[0] = new ArrayList<>();

                    //for (int iterations = 0; iterations < NO_OF_ITERATIONS; iterations++) {

                    //BLOCK BUILDING
                    time1 = System.currentTimeMillis();
                    blocks = method.buildBlocks();
                    time2 = System.currentTimeMillis();

                    blbuTime = time2 - time1;
                    System.out.println("BlBu time\t:\t" + blbuTime);

                    bStats = new BlockStatistics(blocks, abp);
                    results = bStats.applyProcessing();

                    comparisons[0].add(results[2]);
                    pc[0].add(results[0]);
                    pq[0].add(results[1]);
                    rr[0].add(results[3]);
                    nBlocks[0].add(results[4]);
                    blbuTimes[0].add(blbuTime);

                    //}//end for iterations

                    printOutputAndWriteToFile(writer, dSize, ySize, dataSetAttributes, methodName, 0, datasetID, nBlocks, comparisons, pc, pq, rr, blbuTimes, null, null, r, modelID);
                }
            } //end for NUMBER_OF_DATASETS
            closeWriter(writer, fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void analyzeRuntime_ACl_BlBu(FusableDataSet<Event, DefaultSchemaElement> dataSetD, FusableDataSet<Event, DefaultSchemaElement> dataSetY, String path) {
    }
}
