package mode.learn;

import dao.ElasticSearch;
import dao.Spark;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import dao.graph.GraphX;
import org.apache.spark.graphx.Graph;
import util.Time;

import java.util.HashMap;
import java.util.Map;

public class LearnCore {
    public void execute() {
        Spark spark = new Spark();
        ElasticSearch elasticSearch = new ElasticSearch(spark.javaSparkContext());
        JavaPairRDD<String, Map<String, Object>> esData = elasticSearch.search(0L, Time.now());
        JavaRDD<Map<String, Object>> esDataRDD = esData.values();
        GraphX graphX = new GraphX();
        Graph<String, String> graph = graphX.graphGenerator(esDataRDD.rdd());
//        long outDegreeAvg = graphX.graphOutDegreeAvg(graph);
//        System.out.println(outDegreeAvg);
//        System.out.println(inDegreeAvg);
//        graphX.test(graph);
        HashMap<Object, Object> hashMap1 = new HashMap<>();
        graphX.getOutDegreeOverList(graph, 5, hashMap1, spark.session());
        if (hashMap1.size() == 0)
            System.out.println("ERROR ERROR");
        HashMap<Long, Integer> hashMap = new HashMap<>();
        for (Object recordKey : hashMap1.keySet()) {
            hashMap.put((Long) recordKey, (int) hashMap1.get(recordKey));
            System.out.println(hashMap.get((Long) recordKey));
        }
    }

}
