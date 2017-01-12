import org.apache.jena.query.*;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;

import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;

/**
 * Created by Daniel on 06/12/16.
 */
public class createDatasetsMain {
    public static void main(String[] args) {
        // PARAMETERS
        boolean testing = false;

        boolean dbpedia = true;
        boolean yago = true;
        boolean wikidata = false;
        int k; //0 for DBpedia, 1 for YAGO, 2 for Wikidata

        boolean secondOrderP = true; //second order can only be specified if getOptionalP is true

        String fileName;
        List<String> secondOrderFileNames = new ArrayList<>();
        String header;
        String secondOrderHeader = "uri\tlabel\tlat\tlong\tsame";//"uri\ttype\tlat\tlong\tsame";

        //configure log4j
        org.apache.log4j.BasicConfigurator.configure();
        LogManager.getRootLogger().setLevel(Level.OFF); //set console logger off


        int lineProgress = 500;
        //HashSet<String> xInstancesWithProperties;

        if (dbpedia) {
            k=0;
            QueryObject dQ = new QueryObject("http://dbpedia.org/sparql");

            if (dQ.testConnection()) {
                //specify fileNames and csv header
                fileName = "out/dbpedia-1.tsv";
                header = "uri\tlabel\tdate\tlat\tlong\tsame\tplace";//\tlocation\tcity\tterritory";
                //secondOrderHeader = "uri\ttype\tlat\tlong\tsame";
                secondOrderFileNames.add("out/dbpedia-2-place.tsv");
                //secondOrderFileNames.add("out/dbpedia-2-location.tsv");
                //secondOrderFileNames.add("out/dbpedia-2-city.tsv");
                //secondOrderFileNames.add("out/dbpedia-2-territory.tsv");


                KGVariableNames dbpediaVarNames = new KGVariableNames(k);
                //get event instances from DBpedia

                HashSet<String> eventInstances = getEventInstances(k, dQ, dbpediaVarNames, testing);
                System.out.println(eventInstances.size() + " distinct instances received from " + dQ.getService());
                //HashSet<String> eventInstances = getEventInstancesFromDBpediaTable();
                //System.out.println(eventInstances.size() + " distinct instances read from DBpedia table");


                // 1 get and write event instances properties
                getAndWriteEventInstancePropertiesToFile(k, dQ, dQ.getService(), dbpediaVarNames, eventInstances, fileName, header, lineProgress);

                // 2 get and write second order file
                if (secondOrderP) {
                    getAndWritePlaceInstancePropertiesToFile(k, dQ, dQ.getService(), dbpediaVarNames, secondOrderFileNames, secondOrderHeader, lineProgress, testing);
                }
            }
        }
        secondOrderFileNames.clear();
        if (yago) {
            k = 1;

            QueryObject yQ = new QueryObject("https://linkeddata1.calcul.u-psud.fr/sparql");
            if (yQ.testConnection()) {
                //specify fileNames and csv header
                header = "uri\tlabel\tdate\tlat\tlong\tsame\tplace";
                fileName = "out/yago-1.tsv";

                secondOrderFileNames.add("out/yago-2-happenedIn.tsv");
                //secondOrderFileNames.add("out/yago-2-isLocatedIn.tsv");

                KGVariableNames yagoVarNames = new KGVariableNames(k);
                //get event instances from YAGO
                HashSet<String> eventInstances = getEventInstances(k, yQ, yagoVarNames, testing);
                System.out.println(eventInstances.size() + " distinct instances received from " + yQ.getService());

                // 1 get event instances properties
                getAndWriteEventInstancePropertiesToFile(k, yQ, yQ.getService(), yagoVarNames, eventInstances, fileName, header, lineProgress);

                // 2 get and write second order file
                if (secondOrderP) {
                    getAndWritePlaceInstancePropertiesToFile(k, yQ, yQ.getService(), yagoVarNames, secondOrderFileNames, secondOrderHeader, lineProgress, testing);
                }


            }


        }

        /*if (wikidata) {
            k = 2;
            service = "https://query.wikidata.org/sparql";
            dbIsUp = testConnection(service);
            if (dbIsUp) {

            }
        }*/

    }

    private static HashSet<String> getEventInstancesFromDBpediaTable() {
        String fileName = "/Users/curtis/MT/DBpediaTables/EventInstanceURIs.csv";
        HashSet<String> eventInstanceURIs = new HashSet<>();
        try {
            Scanner scanner = new Scanner(new FileReader(fileName));
            while(scanner.hasNext()){
                eventInstanceURIs.add(scanner.next());
            }
        } catch (IOException e){
            System.out.println("error wile reading " + fileName);
        }

    return eventInstanceURIs;
    }

    /**
     * get and write the place instance properties to file
     * @param k 0:DBpedia, 1:YAGO, 2:Wikidata
     * @param queryObject
     * @param service sparql url
     * @param varNames
     * @param secondOrderFileNames
     * @param secondOrderHeader
     * @param lineProgress print message after lineProgress lines
     */
    private static void getAndWritePlaceInstancePropertiesToFile(int k, QueryObject queryObject, String service, KGVariableNames varNames, List<String> secondOrderFileNames, String secondOrderHeader, int lineProgress, boolean testing) {

        for (String secondOrderFileName : secondOrderFileNames) {
            String propertyName = getPropertyName(secondOrderFileName);
            HashSet<String> placeInstances = getPlaceInstances(k, queryObject, varNames, propertyName, testing);
            System.out.println(placeInstances.size() + " distinct "+  propertyName + " instances received from " + service);
            int lineCounter = 0;
            int writtenLines = 0;
            try {
                PrintWriter writer2 = new PrintWriter(secondOrderFileName, "UTF-8");
                //write header
                writer2.println(secondOrderHeader);

                HashSet<String> placeInstancesProperties = new HashSet<>();
                for (String placeInstance : placeInstances) {
                    placeInstancesProperties.addAll(getPlaceInstanceProperties(k, queryObject, varNames, placeInstance));
                    //write lines to file
                    placeInstancesProperties.forEach(writer2::println);
                    writtenLines += placeInstancesProperties.size();
                    placeInstancesProperties.clear();
                    lineCounter = increaseLineCounter(lineCounter, lineProgress);
                }
                writer2.close();
                System.out.println(writtenLines + " lines written to " + secondOrderFileName);

            } catch (IOException e) {
                System.out.println("error while writing to file " + secondOrderFileName);
            }
            placeInstances.clear();
        }
    }

    private static String getPropertyName(String secondOrderFileName) {
        //secondOrderFileNames.add("out/dbpedia-2-territory.tsv");
        //secondOrderFileNames.add("out/yago-2-happenedIn.tsv");

        return secondOrderFileName.substring(
                secondOrderFileName.indexOf("2-")+2,
                secondOrderFileName.indexOf(".tsv"));
    }

    /**
     * get and write the event instance properties to file
     * @param k 0:DBpedia, 1:YAGO, 2:Wikidata
     * @param queryObject
     * @param service sparql url
     * @param varNames
     * @param eventInstances URIs of all distinct event instances
     * @param fileName
     * @param header
     * @param lineProgress print message after lineProgress lines
     */
    private static void getAndWriteEventInstancePropertiesToFile(int k, QueryObject queryObject, String service, KGVariableNames varNames, HashSet<String> eventInstances, String fileName, String header, int lineProgress) {
        int lineCounter = 0;
        int writtenLines = 0;
        try {
            PrintWriter writer = new PrintWriter(fileName, "UTF-8");
            //write header
            writer.println(header);
            //get the properties for each event instance
            HashSet<String> eventInstanceProperties = new HashSet<>();
            for (String instance : eventInstances) {
                //get all properties for the instance
                if (!instance.contains("\"")) {
                    eventInstanceProperties.addAll(getInstanceProperties(k, queryObject, varNames, instance));
                    //write all lines to file
                    eventInstanceProperties.forEach(writer::println);
                    writtenLines += eventInstanceProperties.size();
                    //clear set of lines that have been written to file
                    eventInstanceProperties.clear();
                    lineCounter = increaseLineCounter(lineCounter, lineProgress);
                } else {
                    System.out.println("Instance contains quote: " + instance);
                }
            }
            writer.close();
            System.out.println(writtenLines + " lines written to " + fileName);
        } catch (IOException e) {
            System.out.println("error while writing to file " + fileName);
        }

    }
    /**
     * Increment the lineCounter and print a console log if lineProgress is reached
     * @param lineProgress
     * @return incremented lineCounter value
     */
    private static int increaseLineCounter(int lineCounter, int lineProgress) {
        int newLineCounterValue = lineCounter + 1;
        if (newLineCounterValue % lineProgress == 0) {
            System.out.println(newLineCounterValue + " instances processed.");
        }
        return newLineCounterValue;
    }

    /**
     * Get all place instance properties. Might (optional) be the following:
     * geo:lat, geo:long, owl:sameAs.
     * @param k 0:DBpedia, 1:YAGO, 2:Wikidata
     * @param queryObject
     * @param varNames
     *@param placeInstance URI of the instance  @return HashSet<String> including all instance properties
     */
    private static HashSet<String> getPlaceInstanceProperties(int k, QueryObject queryObject, KGVariableNames varNames, String placeInstance) {
        //String geoNamesURL = "http://sws.geonames.org";
        //String otherKgURL = varNames.getOtherKgURL();

        HashSet<String> resultSet = new HashSet<>();
        String queryString = getQueryPrefix(k);
        queryString = queryString +
                "SELECT ?label ?lat ?long ?same WHERE {\n" + //type, country, city, ...
                " OPTIONAL { <" + placeInstance +"> rdfs:label ?label }\n"+
                //" OPTIONAL { <" + placeInstance +"> a ?type }\n"+
                " OPTIONAL { <" + placeInstance + "> " + varNames.getLatVar() + " ?lat }\n" +
                " OPTIONAL { <" + placeInstance +"> " + varNames.getLongVar() + " ?long }\n"+
                " OPTIONAL { <" + placeInstance +"> owl:sameAs ?same }\n"+
                " FILTER langMatches( lang(?label), \'" + varNames.getEnVar() + "\' ) }";
        ResultSet results = queryObject.queryEndpoint(queryString);

        //ResultSetFormatter.outputAsXML(System.out, results);


        List<String> properties = new ArrayList<>();
        properties.add("label");
       // properties.add("type");
        properties.add("lat");
        properties.add("long");
        properties.add("same");
        String resultString = "";
        boolean oneLineAdded = false;
        while (results.hasNext()) {
            QuerySolution qs = results.next();
            resultString =  placeInstance + getAvailableOptionalProperties(qs, properties);

            //only add sameAs if they link to yago, dbpedia, or geonames BUT add at least one line with lat&long if no sameAs link goes to yago, dbpedia, or geonames
            /*if (qs.contains("same")) {
                String sameAs = qs.get("same").toString();
                if (sameAs.startsWith(geoNamesURL) || sameAs.startsWith(otherKgURL)) {
                    resultSet.add(resultString);
                    oneLineAdded = true;
                }

            }  else {
                resultSet.add(resultString);
                oneLineAdded = true;
            }*/
            resultSet.add(resultString);
            oneLineAdded = true;
        }
        //check if at least one line was added
        if (!oneLineAdded) {
            resultSet.add(resultString);
        }
        return resultSet;
    }

    /**
     * Get all distinct place instances that appear as place object for an event instance.
     * @param k 0:DBpedia, 1:YAGO, 2:Wikidata
     * @param queryObject
     * @param varNames
     * @param propertyName
     * @return HashSet<String> including all place instance URIs
     */
    private static HashSet<String> getPlaceInstances(int k, QueryObject queryObject, KGVariableNames varNames, String propertyName, boolean testing) {
        HashSet<String> instanceSet = new HashSet<>();
        String queryString = getQueryPrefix(k);
        queryString = queryString +
                "SELECT DISTINCT ?place WHERE {\n" +
                " ?event a " + varNames.getEventClass() + " .\n" +
                " ?event rdfs:label ?label .\n" +
                " ?event " + varNames.getDateVar() + " ?date .\n" +
                //comment if optional
                " ?event " + varNames.getLatVar() + "?lat .\n" +
                " ?event " + varNames.getLongVar() + " ?long .\n" +
                " ?event " + varNames.getPlaceVar(propertyName) + " ?place .\n" +
                " FILTER langMatches( lang(?label), \'" + varNames.getEnVar() + "\' )\n" +
                "}";
        if (testing) {
            queryString = queryString + " LIMIT 10";
        }

        ResultSet results = queryObject.queryEndpoint(queryString);
        while (results.hasNext()) {
            QuerySolution qs = results.next();
            if (qs.get("place").isURIResource()) {
                instanceSet.add(qs.get("place").toString());
                //System.out.println(qs.get("event").toString());
            }
            //System.out.println(qs.get("count").toString());
        }

        return instanceSet;
    }

    /**
     * Get all Instance properties
     * DBpedia: rdfs:label, dbo:date, and optionally: geo:lat, geo:long, owl:sameAs, dbo:place
     * @param k 0:DBpedia, 1:YAGO, 2:Wikidata
     * @param queryObject
     * @param varNames
     */
    private static HashSet<String> getInstanceProperties(int k, QueryObject queryObject, KGVariableNames varNames, String instance) {
        //get variable names
        List<String> optionalProperties = new ArrayList<>();
        //optionalProperties.add("lat");
        //optionalProperties.add("long");
        optionalProperties.add("same");
        optionalProperties.add("place");
        //optionalProperties.add("location");

        HashSet<String> resultSet = new HashSet<>();
        String queryString = getQueryPrefix(k);

        queryString = queryString +
                "SELECT ?label ?date ?lat ?long ?same ?place";// ?location";

        /*if (k==0) {
            queryString = queryString + " ?city ?territory";
            optionalProperties.add("city");
            optionalProperties.add("territory");
        }*/

        queryString = queryString +
                " WHERE {\n" +
                " <" + instance + "> rdfs:label ?label .\n" +
                " <" + instance + "> " + varNames.getDateVar() + " ?date .\n" +
                " <" + instance + "> " + varNames.getLatVar() + " ?lat .\n" +
                " <" + instance + "> " + varNames.getLongVar() + " ?long .\n"+
                //" OPTIONAL { <" + instance + "> " + varNames.getLatVar() + " ?lat }\n" +
                //" OPTIONAL { <" + instance + "> " + varNames.getLongVar() + " ?long }\n"+
                " OPTIONAL { <" + instance + "> owl:sameAs ?same }\n"+
                " OPTIONAL { <" + instance + "> " + varNames.getPlaceVar() + " ?place }\n";
               // " OPTIONAL { <" + instance + "> " + varNames.getPlace2Var() + " ?location }\n";

      /*  if (k==0) {
            queryString = queryString +
                    " OPTIONAL { <" + instance + "> dbo:city ?city }\n"+
                    " OPTIONAL { <" + instance + "> dbo:territory ?territory }\n";
        }*/
        queryString = queryString +
                " FILTER langMatches( lang(?label), \'" + varNames.getEnVar() + "\' )\n" +
                "}";
        //System.out.println(instance);
        ResultSet results = queryObject.queryEndpoint(queryString);

        String resultString;

        while (results.hasNext()) {
            QuerySolution qs = results.next();
            //resultString = instance + "\t" + qs.get("label").toString() + "\t" + qs.get("date").toString();
            resultString = instance + "\t" + qs.get("label").toString() + "\t" + qs.get("date").toString() + "\t" + qs.get("lat").toString() + "\t" + qs.get("long").toString();
            resultString = resultString + getAvailableOptionalProperties(qs, optionalProperties);

            resultSet.add(resultString);

        }

        return resultSet;
    }

    /**
     * Get the optional properties of the QuerySolution.
     * Check if the QuerySolution has the provided properties. Insert "null" otherwise.
     * @param qs
     * @param optionalProperties to check
     * @return String (tab-separated with property values or "null" if property value is not available)
     */
    private static String getAvailableOptionalProperties(QuerySolution qs, List<String> optionalProperties) {
        String oP = "";
        for (String p : optionalProperties) {
            //get value for property or add "null" is property is null
            oP = (qs.contains(p)) ? oP + "\t" + qs.get(p).toString() : oP + "\tnull";
        }
        return oP;
    }

    /**
     * Get all distinct event instances that have the following attributes:
     * rdfs:label, dbo:date, geo:lat, geo:long
     * @param k 0:DBpedia, 1:YAGO, 2:Wikidata
     * @param queryObject
     * @param varNames
     * @return HashSet<String> including all instance URIs
     */
    private static HashSet<String> getEventInstances(int k, QueryObject queryObject, KGVariableNames varNames, boolean testing) {
        HashSet<String> instanceSet = new HashSet<>();
        String queryString = getQueryPrefix(k);

        queryString = queryString +
                    //"SELECT (COUNT(DISTINCT ?event) as ?count) WHERE {\n" +
                    "SELECT DISTINCT ?event WHERE {\n" +
                    " ?event a " + varNames.getEventClass() + " .\n" +
                    " ?event rdfs:label ?label .\n" +
                    " ?event " + varNames.getDateVar() +" ?date .\n" +
                //comment if properties should be OPTIONAL
                    " ?event " + varNames.getLatVar() + "?lat .\n" +
                    " ?event " + varNames.getLongVar() + " ?long .\n" +
                    " FILTER langMatches( lang(?label), \'" + varNames.getEnVar() + "\' )\n" +
                    "}";

        if (testing) {
            queryString = queryString + " LIMIT 10";
        }

        ResultSet results = queryObject.queryEndpoint(queryString);

        while (results.hasNext()) {
            QuerySolution qs = results.next();
            if (qs.get("event").isURIResource()) {
                instanceSet.add(qs.get("event").toString());
                //System.out.println(qs.get("event").toString());
            }
            //System.out.println(qs.get("count").toString());
        }

        return instanceSet;
    }
    /**
     * Get query prefix
     * @param k 0:DBpedia, 1:YAGO, 2:Wikidata
     * @return String of the query prefix
     */
    private static String getQueryPrefix(int k) {
        String p = "";
        if (k==0) {
            p =     "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
                    "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                    //"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"+
                    "PREFIX dbo: <http://dbpedia.org/ontology/>\n" +
                    "PREFIX geo: <http://www.w3.org/2003/01/geo/wgs84_pos#>\n" +
                    // "PREFIX georss: <http://www.georss.org/georss/>\n"+
                    "PREFIX foaf: <http://xmlns.com/foaf/0.1/>\n";

        } else if (k==1) {
            p =     "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
                    "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                    //"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"+
                    "PREFIX yago: <http://yago-knowledge.org/resource/>\n"+
                    "PREFIX skos: <http://www.w3.org/2004/02/skos/core#>\n";
        } else if (k==2) {
            p =     "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                    //"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"+
                    "PREFIX wdt: <http://www.wikidata.org/prop/direct/>\n" +
                    "PREFIX wd: <http://www.wikidata.org/entity/>\n" +
                    "PREFIX skos: <http://www.w3.org/2004/02/skos/core#>\n";
        }
        return p;
    }


}
