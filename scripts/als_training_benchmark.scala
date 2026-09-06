import org.apache.spark.sql.SparkSession
import org.apache.spark.ml.recommendation.ALS

val spark = SparkSession.builder.appName("HaoRanMusicAlsBenchmark").getOrCreate()
import spark.implicits._
spark.sparkContext.setLogLevel("WARN")

val sizes = Seq(10000, 50000, 100000)
val results = sizes.map { size =>
  val users = math.min(1000, math.max(20, size / 20))
  val items = math.min(2000, math.max(40, size / 10))
  val frame = spark.sparkContext
    .parallelize(0 until size, 8)
    .map(index => (index % users, (index * 37 + index / users) % items, 1.0))
    .toDF("user_id", "song_id", "rating")
    .cache()
  frame.count()
  val fitStarted = System.nanoTime()
  val model = new ALS()
    .setRank(8)
    .setMaxIter(10)
    .setRegParam(0.1)
    .setImplicitPrefs(true)
    .setAlpha(20.0)
    .setUserCol("user_id")
    .setItemCol("song_id")
    .setRatingCol("rating")
    .fit(frame)
  val fitSeconds = (System.nanoTime() - fitStarted) / 1e9
  val materializeStarted = System.nanoTime()
  val userFactors = model.userFactors.count()
  val itemFactors = model.itemFactors.count()
  val materializeSeconds = (System.nanoTime() - materializeStarted) / 1e9
  frame.unpersist()
  "{\"interactions\":%d,\"users\":%d,\"items\":%d,\"fit_seconds\":%.3f,\"materialization_seconds\":%.3f,\"user_factors\":%d,\"item_factors\":%d}".format(
    size, users, items, fitSeconds, materializeSeconds, userFactors, itemFactors)
}
println("HAORAN_ALS_BENCHMARK=[" + results.mkString(",") + "]")
spark.stop()
System.exit(0)
