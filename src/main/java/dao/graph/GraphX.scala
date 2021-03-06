package dao.graph
import utils.parse.JavaParser
import utils.parse.ScalaParser
import java.util

import dao.graph.aggregation.AggregationList
import org.apache.spark.graphx.{Edge, EdgeRDD, Graph, VertexId, VertexRDD}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, SparkSession}

class GraphX extends Serializable {
  def graphGenerator (rdd : RDD[java.util.Map[String, Object]]) : Graph[String, String] = {
    val vertexSrc: RDD[String] = rdd.map(
      record => JavaParser.toStr(record.get("i_client_ip"))
    )
    val vertexDst: RDD[String] = rdd.map(
      record => JavaParser.toStr(record.get("i_server_ip"))
    )
    val vertexRDD: RDD[String] = vertexSrc.union(vertexDst).distinct()
    val vertex: RDD[(VertexId, String)] = vertexRDD.map(
      record => new Tuple2(JavaParser.ip2Long(record), record)
    )
    val edge: RDD[Edge[String]] = rdd.map(
      record =>
        new Edge[String](
          JavaParser.ip2Long(JavaParser.toStr(record.get("i_client_ip"))),
          JavaParser.ip2Long(JavaParser.toStr(record.get("i_server_ip"))),
          JavaParser.toStr(record.get("@timestamp"), "|" + record.get("i_server_port")
      ))
    )
    val graph: Graph[String, String] = Graph(vertex, edge)
    graph // = return graph
  }

  def graphOutDegreeAvg (graph: Graph[String, String]) : Long = {
    if (graph == null)
      return 0
    val nodes : Long = graph.vertices.count()
    val outDegree : Long = graph.outDegrees.map(line => line._2).reduce((r1, r2) => r1 + r2)
    if (nodes == 0)
      return 0
    outDegree / nodes // = return outDegree / nodes
  }

  def graphInDegreeAvg (graph : Graph[String, String]) : Long = {
    if (graph == null)
      return 0
    val nodes : Long = graph.vertices.count()
    val inDegree : Long  = graph.inDegrees.map(line => line._2).reduce((r1, r2) =>  r1 + r2)
    if (nodes == 0)
      return 0
    inDegree / nodes
  }

  private def getOutDegree (graph: Graph[String, String]) : RDD[(VertexId, Int)] = {
    graph.outDegrees
  }

  def getOutDegreeOverMap (graph: Graph[String, String], least : Long, sparkSession: SparkSession) : Unit = {
    if (graph == null)
      return
    if (least < 1)
      return
    if (sparkSession == null)
      return
    val outDegreeRDD: RDD[(VertexId, Int)] = getOutDegree(graph)
    if (outDegreeRDD == null)
      return
    import sparkSession.implicits._
    val outDegreeDataFrame : DataFrame = outDegreeRDD.map(
      record => (record._1 , record._2)
    ).toDF
    outDegreeDataFrame.createOrReplaceTempView("outDegree")

    val outDegreeResultDataFrame : DataFrame = sparkSession.sql(
      "SELECT outdegree._1, outdegree._2 FROM outDegree WHERE outdegree._2 > " + least
    )
    val resultArray : Array[Map[String, Long]] = outDegreeResultDataFrame.map(
//      record => record.getJavaMap(1)
      record => {
        List("_1", "_2").map(
          name => name -> ScalaParser.toLong(record.getAs[Long](name))
        ).toMap
      }
    ).collect()
    AggregationList.reset()
    resultArray.foreach(
      resultSingle => {
        AggregationList.usableSrcArrayAppend(ScalaParser.toLong(ScalaParser.removeSomeTag(resultSingle.get("_1"))))
        AggregationList.usableOutDegreeArrayAppend(ScalaParser.toInt(ScalaParser.removeSomeTag(resultSingle.get("_2"))))
        AggregationList.outDegreeSubAdd(ScalaParser.toInt(ScalaParser.removeSomeTag(resultSingle.get("_2"))))
      }
    )
    AggregationList.generate()
  }
}
