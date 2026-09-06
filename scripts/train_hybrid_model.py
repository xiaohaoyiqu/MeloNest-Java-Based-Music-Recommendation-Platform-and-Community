#!/usr/bin/env python3
                       
\
\
\
\
\
\
\
\
\
\
\
\
\
\
\
\
\
\
\
\
\
\
\
\
\
\
\
\
\
\
   

import json
import os
import re
import sys
from datetime import datetime
from recommendation_model_utils import (
    build_catalog_version,
    build_model_metadata,
    write_model_artifact,
)
from pyspark.sql import SparkSession
from pyspark.ml.recommendation import ALS
try:
    from pyspark.ml.evaluation import RankingEvaluator
except ImportError:                           
    RankingEvaluator = None
from pyspark.mllib.evaluation import RankingMetrics
from pyspark.sql.functions import col, collect_list, collect_set, concat, countDistinct, current_timestamp, dense_rank, explode, lit, max as spark_max, row_number
from pyspark.sql.window import Window

                                              
         
DB_HOST = os.environ.get("HAORAN_DB_HOST", "127.0.0.1")
DB_PORT = int(os.environ.get("HAORAN_DB_PORT", "3306"))
DB_NAME = os.environ.get("HAORAN_DB_NAME", "haoranmusic_bus")
DB_USER = os.environ.get("HAORAN_DB_USER", "hdfs")
DB_PASSWORD = os.environ.get("HAORAN_DB_PASSWORD", "")

       
DATA_ROOT = os.environ.get("HAORAN_DATA_ROOT", "/sdb1/myprojoct/haoranmusic/data")
KAGGLE_DATA_PATH = os.environ.get(
    "HAORAN_KAGGLE_DATA_PATH",
    os.environ.get("KAGGLE_DATA_PATH", f"{DATA_ROOT}/kaggle/train_N.csv"),
)
EXTERNAL_CATALOG_MAP_PATH = os.environ.get("HAORAN_EXTERNAL_CATALOG_MAP_PATH", "")
PLAYABLE_CATALOG_PATH = os.environ.get("HAORAN_PLAYABLE_CATALOG_PATH", "")
        
MODEL_OUTPUT_DIR = os.environ.get(
    "HAORAN_MODEL_OUTPUT_DIR",
    "/sdb1/myprojoct/haoranmusic/models",
)
COLLABORATIVE_MODEL_PATH = f"{MODEL_OUTPUT_DIR}/recommendation_model.json"
HIVE_ENABLED = os.environ.get("HAORAN_HIVE_ENABLED", "true").strip().lower() == "true"
HIVE_DATABASE = os.environ.get("HAORAN_HIVE_DATABASE", "haoranmusic_recommend")
HIVE_METASTORE_URIS = os.environ.get(
    "HAORAN_HIVE_METASTORE_URIS",
    "thrift://192.168.153.131:9083",
)
SPARK_WAREHOUSE_DIR = os.environ.get(
    "HAORAN_SPARK_WAREHOUSE_DIR",
    "hdfs://mycluster/Datas/haoranMusicPlatform/hive/warehouse",
)
SPARK_MODEL_BASE_PATH = os.environ.get(
    "HAORAN_SPARK_MODEL_BASE_PATH",
    "hdfs://mycluster/Datas/haoranMusicPlatform/models/recommendation/als",
)

         
SPARK_APP_NAME = "HaoRanMusicHybridTraining"
SPARK_SHUFFLE_PARTITIONS = os.environ.get("HAORAN_SPARK_SHUFFLE_PARTITIONS", "16")
MIN_USER_INTERACTIONS = int(os.environ.get("HAORAN_MIN_USER_INTERACTIONS", "5"))
MIN_ITEM_INTERACTIONS = int(os.environ.get("HAORAN_MIN_ITEM_INTERACTIONS", "3"))
MIN_TRAINING_INTERACTIONS = int(os.environ.get("HAORAN_MIN_TRAINING_INTERACTIONS", "100"))
ALS_RANK = int(os.environ.get("HAORAN_ALS_RANK", "20"))
ALS_MAX_ITER = int(os.environ.get("HAORAN_ALS_MAX_ITER", "15"))
JDBC_URL = f"jdbc:mysql://{DB_HOST}:{DB_PORT}/{DB_NAME}?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8"


def jdbc_subquery(sql, alias):
    return "({}) AS {}".format(sql.strip().rstrip(";"), alias)

def create_spark_session():
                   
    builder = SparkSession.builder\
        .appName(SPARK_APP_NAME)\
        .config("spark.sql.warehouse.dir", SPARK_WAREHOUSE_DIR)\
        .config("spark.hadoop.hive.metastore.uris", HIVE_METASTORE_URIS)\
        .config("spark.sql.adaptive.enabled", "true")\
        .config("spark.sql.adaptive.coalescePartitions.enabled", "true")\
        .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")\
        .config("spark.sql.shuffle.partitions", SPARK_SHUFFLE_PARTITIONS)

                                                
                                       
    extra_packages = os.environ.get("HAORAN_SPARK_JARS_PACKAGES", "").strip()
    if extra_packages:
        builder = builder.config("spark.jars.packages", extra_packages)

    spark = builder.enableHiveSupport().getOrCreate()

    spark.sparkContext.setLogLevel("WARN")
    return spark


def spark_path_exists(spark, path):
                                                                                   
    if not path:
        return False
    hadoop_path = spark._jvm.org.apache.hadoop.fs.Path(path)
    filesystem = hadoop_path.getFileSystem(spark._jsc.hadoopConfiguration())
    return filesystem.exists(hadoop_path)


def load_kaggle_data(spark):
\
\
\
\
       
    print("=" * 60)
    print("加载Kaggle数据集...")

    if not spark_path_exists(spark, KAGGLE_DATA_PATH):
        print(f"警告: Kaggle数据文件不存在: {KAGGLE_DATA_PATH}")
        return None
    if not EXTERNAL_CATALOG_MAP_PATH or not spark_path_exists(spark, EXTERNAL_CATALOG_MAP_PATH):
        print("警告: 未提供 HAORAN_EXTERNAL_CATALOG_MAP_PATH，跳过无法映射到本站曲库的外部数据")
        return None
    if not PLAYABLE_CATALOG_PATH or not spark_path_exists(spark, PLAYABLE_CATALOG_PATH):
        print("警告: 未提供 HAORAN_PLAYABLE_CATALOG_PATH，跳过无法验证可播放资格的外部数据")
        return None

                
    kaggle_df = spark.read.csv(KAGGLE_DATA_PATH, header=True, inferSchema=True)

    print(f"Kaggle数据量: {kaggle_df.count()} 条")

    available_columns = set(kaggle_df.columns)
    if {"userId", "id"}.issubset(available_columns):
        raw_user_col = "userId"
        raw_song_col = "id"
    elif {"msno", "song_id"}.issubset(available_columns):
        raw_user_col = "msno"
        raw_song_col = "song_id"
    else:
        print(f"警告: 不支持的Kaggle列: {kaggle_df.columns}")
        return None

    print(
        f"检测到Kaggle列: user={raw_user_col}, song={raw_song_col}, "
        f"rating={'target' if 'target' in available_columns else 'implicit 1.0'}"
    )

    select_exprs = [
        col(raw_user_col).alias("raw_user_id"),
        col(raw_song_col).alias("raw_song_id"),
    ]

    if "target" in available_columns:
        kaggle_df = kaggle_df.select(
            *select_exprs,
            col("target").alias("rating")
        ).filter(col("rating") == 1)
    else:
        kaggle_df = kaggle_df.select(*select_exprs).withColumn("rating", lit(1.0))

    mapping_df = spark.read.csv(EXTERNAL_CATALOG_MAP_PATH, header=True, inferSchema=False)
    if not {"external_song_id", "song_id"}.issubset(set(mapping_df.columns)):
        raise RuntimeError("外部目录映射必须包含 external_song_id,song_id")
    mapping_df = mapping_df.select(
        col("external_song_id").cast("string").alias("mapped_external_song_id"),
        col("song_id").cast("long").alias("site_song_id"),
    ).filter(col("site_song_id") > 0)
    ambiguous_mapping_count = mapping_df.groupBy("mapped_external_song_id").agg(
        countDistinct("site_song_id").alias("site_song_count")
    ).filter(col("site_song_count") > 1).limit(1).count()
    if ambiguous_mapping_count:
        raise RuntimeError("同一 external_song_id 不得映射到多个本站歌曲")
    mapping_df = mapping_df.dropDuplicates(["mapped_external_song_id"])
    playable_catalog_df = spark.read.csv(PLAYABLE_CATALOG_PATH, header=True, inferSchema=False)
    if "song_id" not in set(playable_catalog_df.columns):
        raise RuntimeError("可播放曲库快照必须包含 song_id")
    playable_catalog_df = playable_catalog_df.select(
        col("song_id").cast("long").alias("playable_song_id")
    ).filter(col("playable_song_id") > 0).dropDuplicates(["playable_song_id"])
    if playable_catalog_df.limit(1).count() == 0:
        raise RuntimeError("可播放曲库快照为空")
    mapping_df = mapping_df.join(
        playable_catalog_df,
        mapping_df.site_song_id == playable_catalog_df.playable_song_id,
        "inner",
    ).drop("playable_song_id")

    source_positive_count = kaggle_df.count()
    kaggle_df = kaggle_df.join(
        mapping_df,
        kaggle_df.raw_song_id.cast("string") == mapping_df.mapped_external_song_id,
        "inner",
    )

                                      
    kaggle_df = kaggle_df.withColumn(
        "user_key", concat(lit("external:"), col("raw_user_id").cast("string"))
    ).withColumn("site_user_id", lit(None).cast("long"))\
                         .withColumn("site_song_id", col("site_song_id").cast("long"))\
                         .withColumn("event_time", lit(None).cast("timestamp"))\
                         .withColumn("source", lit("external_mapped"))\
                         .select(
                             "user_key", "site_user_id", "site_song_id",
                             "rating", "event_time", "source",
                         )

    mapped_count = kaggle_df.count()
    print(f"Kaggle正样本量: {source_positive_count}")
    print(f"映射到本站曲库: {mapped_count} ({mapped_count / source_positive_count if source_positive_count else 0:.2%})")
    print(f"Kaggle用户数: {kaggle_df.select('user_key').distinct().count()}")
    print(f"Kaggle歌曲数: {kaggle_df.select('site_song_id').distinct().count()}")

    return kaggle_df


def load_mysql_data(spark):
\
\
\
       
    print("=" * 60)
    print("加载MySQL真实数据...")
    if not DB_PASSWORD:
        print("警告: 未设置 HAORAN_DB_PASSWORD，跳过MySQL真实数据")
        return None

            
    query_listen = """
    SELECT
        h.user_id,
        h.song_id,
        1.0 AS rating,
        h.listen_time AS event_time
    FROM listen_history h
    INNER JOIN song s ON s.id = h.song_id
    WHERE h.deleted = 0
        AND h.user_id IS NOT NULL
        AND h.song_id IS NOT NULL
        AND s.deleted = 0
        AND s.status = 1
        AND COALESCE(NULLIF(s.url_standard, ''), NULLIF(s.url_high, ''), NULLIF(s.url_lossless, '')) IS NOT NULL
    """

            
    query_like = """
    SELECT
        sl.user_id,
        sl.song_id,
        3.0 AS rating,
        sl.create_time AS event_time
    FROM song_like sl
    INNER JOIN song s ON s.id = sl.song_id
    WHERE sl.deleted = 0
        AND sl.is_favorite = 1
        AND sl.user_id IS NOT NULL
        AND sl.song_id IS NOT NULL
        AND s.deleted = 0
        AND s.status = 1
        AND COALESCE(NULLIF(s.url_standard, ''), NULLIF(s.url_high, ''), NULLIF(s.url_lossless, '')) IS NOT NULL
    """

    try:
        df_listen = spark.read.jdbc(
            url=JDBC_URL,
            table=jdbc_subquery(query_listen, "listen_actions"),
            properties={"user": DB_USER, "password": DB_PASSWORD}
        )

        df_like = spark.read.jdbc(
            url=JDBC_URL,
            table=jdbc_subquery(query_like, "favorite_actions"),
            properties={"user": DB_USER, "password": DB_PASSWORD}
        )

                            
        mysql_df = df_listen.union(df_like)\
            .groupBy("user_id", "song_id")\
            .agg(
                spark_max("rating").alias("rating"),
                spark_max("event_time").alias("event_time"),
            )

        mysql_df = mysql_df.withColumn(
            "user_key", concat(lit("mysql:"), col("user_id").cast("string"))
        ).withColumn(
            "site_user_id", col("user_id").cast("long")
        ).withColumn(
            "site_song_id", col("song_id").cast("long")
        ).withColumn("source", lit("mysql")).select(
            "user_key", "site_user_id", "site_song_id",
            "rating", "event_time", "source",
        )

        print(f"MySQL数据量: {mysql_df.count()}")
        print(f"MySQL用户数: {mysql_df.select('site_user_id').distinct().count()}")
        print(f"MySQL歌曲数: {mysql_df.select('site_song_id').distinct().count()}")

        return mysql_df

    except Exception as e:
        print(f"警告: 加载MySQL数据失败: {e}")
        return None


def encode_als_ids(training_df):
                                                                                    
    user_window = Window.orderBy("user_key")
    song_window = Window.orderBy("site_song_id")
    user_mapping = training_df.select("user_key").distinct().withColumn(
        "user_id", (dense_rank().over(user_window) - 1).cast("integer")
    )
    song_mapping = training_df.select("site_song_id").distinct().withColumn(
        "song_id", (dense_rank().over(song_window) - 1).cast("integer")
    )
    return training_df.join(user_mapping, "user_key", "inner").join(
        song_mapping, "site_song_id", "inner"
    )


def load_and_merge_training_data(spark):
\
\
\
       
    print("=" * 60)
    print("加载训练数据...")

    kaggle_df = load_kaggle_data(spark)
    mysql_df = load_mysql_data(spark)

    if kaggle_df is None and mysql_df is None:
        print("错误: 没有可用的训练数据")
        return None

           
    if kaggle_df is not None and mysql_df is not None:
        training_df = kaggle_df.unionByName(mysql_df)
        print(f"合并后总数据量: {training_df.count()}")
    elif kaggle_df is not None:
        training_df = kaggle_df
        print("仅使用Kaggle数据")
    else:
        training_df = mysql_df
        print("仅使用MySQL数据")

    training_df = encode_als_ids(training_df)

               
    user_counts = training_df.groupBy("user_id").count().filter(
        col("count") >= MIN_USER_INTERACTIONS
    ).select("user_id")
    item_counts = training_df.groupBy("song_id").count().filter(
        col("count") >= MIN_ITEM_INTERACTIONS
    ).select("song_id")

    training_df_filtered = training_df.join(user_counts, "user_id", "inner")\
                                     .join(item_counts, "song_id", "inner")\
                                     .cache()

    print("=" * 60)
    print(f"过滤后数据量: {training_df_filtered.count()}")
    print(f"用户数: {training_df_filtered.select('user_id').distinct().count()}")
    print(f"歌曲数: {training_df_filtered.select('song_id').distinct().count()}")
    print("=" * 60)

    return training_df_filtered


def split_time_holdout(training_data):
                                                                                   
    mysql_timed = training_data.filter(
        (col("source") == "mysql") & col("event_time").isNotNull()
    )
    eligible_users = mysql_timed.groupBy("user_id").count().filter(col("count") >= 3).select("user_id")
    ranked = mysql_timed.join(eligible_users, "user_id", "inner").withColumn(
        "event_rank",
        row_number().over(
            Window.partitionBy("user_id").orderBy(col("event_time").desc(), col("song_id").desc())
        ),
    )
    test_data = ranked.filter(col("event_rank") == 1).select("user_id", "song_id")
    if test_data.count() == 0:
        return training_data, None
    return training_data.join(test_data, ["user_id", "song_id"], "left_anti"), test_data


def evaluate_top_k(model, train_data, test_data, k=20):
    if test_data is None:
        return {"evaluation_status": "insufficient_timed_mysql_users"}

    candidates = model.recommendForUserSubset(test_data.select("user_id").distinct(), max(k * 5, 50))
    exploded = candidates.select(
        "user_id",
        explode("recommendations").alias("recommendation"),
    ).select(
        "user_id",
        col("recommendation.song_id").alias("song_id"),
        col("recommendation.rating").alias("score"),
    )
    unseen = exploded.join(
        train_data.select("user_id", "song_id").distinct(),
        ["user_id", "song_id"],
        "left_anti",
    )
    ranked = unseen.withColumn(
        "recommendation_rank",
        row_number().over(
            Window.partitionBy("user_id").orderBy(col("score").desc(), col("song_id").asc())
        ),
    ).filter(col("recommendation_rank") <= k)
                                                                                        
    prediction = ranked.groupBy("user_id").agg(
        collect_list(col("song_id").cast("double")).alias("prediction")
    )
    label = test_data.groupBy("user_id").agg(
        collect_set(col("song_id").cast("double")).alias("label")
    )
    evaluation = prediction.join(label, "user_id", "inner")
    if evaluation.count() == 0:
        return {"evaluation_status": "no_rankable_holdout"}

    metrics = {"evaluation_status": "ok", "k": k, "split": "latest_mysql_interaction_per_user"}
    if RankingEvaluator is not None:
        for metric_name in ("precisionAtK", "recallAtK", "ndcgAtK"):
            evaluator = RankingEvaluator(
                predictionCol="prediction",
                labelCol="label",
                metricName=metric_name,
                k=k,
            )
            metrics[metric_name] = evaluator.evaluate(evaluation)
    else:
        prediction_and_labels = evaluation.select("prediction", "label").rdd.map(
            lambda row: (row["prediction"], row["label"])
        ).cache()
        ranking_metrics = RankingMetrics(prediction_and_labels)
        metrics["precisionAtK"] = ranking_metrics.precisionAt(k)
        metrics["recallAtK"] = prediction_and_labels.map(
            lambda pair: (
                float(len(set(pair[0][:k]).intersection(set(pair[1])))) / len(set(pair[1]))
                if pair[1] else 0.0
            )
        ).mean()
        metrics["ndcgAtK"] = ranking_metrics.ndcgAt(k)
    return metrics


def train_als_model(spark, training_data):
\
\
       
    print("=" * 60)
    print("开始训练ALS模型...")

    train_data, test_data = split_time_holdout(training_data)

             
    als = ALS(
        maxIter=ALS_MAX_ITER,
        regParam=0.1,
        rank=ALS_RANK,
        userCol="user_id",
        itemCol="song_id",
        ratingCol="rating",
        coldStartStrategy="drop",
        implicitPrefs=True,
        alpha=20.0,
    )

          
    model = als.fit(train_data)

    metrics = evaluate_top_k(model, train_data, test_data)
    print(f"Top-K评估: {metrics}")
    print("=" * 60)

    return model, metrics, train_data


def generate_recommendations(spark, model, training_data):
                                                                         
    print("=" * 60)
    print("生成推荐结果...")

    user_recs = model.recommendForAllUsers(100)

    user_recs_exploded = user_recs.select(
        col("user_id"),
        explode(col("recommendations")).alias("recommendation")
    ).select(
        col("user_id"),
        col("recommendation.song_id").alias("song_id"),
        col("recommendation.rating").alias("predicted_rating")
    )

    unseen_user_recs = user_recs_exploded.join(
        training_data.select("user_id", "song_id").distinct(),
        ["user_id", "song_id"],
        "left_anti",
    ).withColumn(
        "recommendation_rank",
        row_number().over(
            Window.partitionBy("user_id").orderBy(col("predicted_rating").desc(), col("song_id").asc())
        ),
    ).filter(col("recommendation_rank") <= 20)

    mysql_user_mapping = training_data.filter(
        (col("source") == "mysql") & col("site_user_id").isNotNull()
    ).select("user_id", "site_user_id").distinct()
    song_mapping = training_data.select("song_id", "site_song_id").distinct()
    real_user_recs = unseen_user_recs.join(
        mysql_user_mapping, "user_id", "inner"
    ).join(song_mapping, "song_id", "inner").select(
        col("site_user_id").alias("user_id"),
        col("site_song_id").alias("song_id"),
        col("predicted_rating"),
        col("recommendation_rank")
    ).filter(col("song_id") > 0)

    print(f"为真实用户生成推荐: {real_user_recs.count()} 条")
    return real_user_recs


def validate_hive_identifier(value, label):
    if not re.match(r"^[A-Za-z_][A-Za-z0-9_]*$", value or ""):
        raise ValueError("{} must be a safe Hive identifier".format(label))
    return value


def collect_catalog_song_ids(training_data):
    return [
        str(int(row["site_song_id"]))
        for row in training_data.select("site_song_id").distinct().collect()
        if int(row["site_song_id"]) > 0
    ]


def publish_hive_results(spark, training_data, user_recs, metadata, metrics, native_model_path):
                                                                                
    if not HIVE_ENABLED:
        print("警告: HAORAN_HIVE_ENABLED=false，跳过Hive离线层写入")
        return

    database = validate_hive_identifier(HIVE_DATABASE, "HAORAN_HIVE_DATABASE")
    spark.sql("CREATE DATABASE IF NOT EXISTS `{}`".format(database))
    model_version = metadata["model_version"]
    catalog_version = metadata["catalog_version"]

    interaction_snapshot = training_data.select(
        col("user_id").alias("training_user_id"),
        col("site_song_id"),
        col("rating").cast("double").alias("implicit_confidence"),
        col("event_time"),
        col("source"),
    ).filter(col("site_song_id") > 0).withColumn(
        "catalog_version", lit(catalog_version)
    ).withColumn(
        "model_version", lit(model_version)
    )
    interaction_snapshot.write.mode("append").format("parquet").partitionBy(
        "model_version"
    ).saveAsTable("{}.dws_recommend_interaction".format(database))

    recommendation_snapshot = user_recs.select(
        col("user_id").cast("long"),
        col("song_id").cast("long"),
        col("predicted_rating").cast("double").alias("score"),
        col("recommendation_rank").cast("integer").alias("rank"),
    ).withColumn(
        "catalog_version", lit(catalog_version)
    ).withColumn(
        "generated_at", current_timestamp()
    ).withColumn(
        "model_version", lit(model_version)
    )
    recommendation_snapshot.write.mode("append").format("parquet").partitionBy(
        "model_version"
    ).saveAsTable("{}.ads_user_song_recommendation".format(database))

    registry = spark.createDataFrame([(
        model_version,
        catalog_version,
        metadata["created_at"],
        json.dumps(metrics, ensure_ascii=False, sort_keys=True),
        COLLABORATIVE_MODEL_PATH,
        native_model_path,
        "SUCCESS",
    )], [
        "model_version",
        "catalog_version",
        "created_at",
        "metrics_json",
        "serving_artifact_path",
        "spark_model_path",
        "status",
    ])
    registry.write.mode("append").format("parquet").saveAsTable(
        "{}.ads_recommend_model_run".format(database)
    )
    print("Hive离线结果已写入: {}".format(database))


def save_model(training_data, metrics, user_recs, model_version, catalog_song_ids, native_model_path):
                                                                
    print("=" * 60)
    print("保存模型...")

            
    os.makedirs(MODEL_OUTPUT_DIR, exist_ok=True)

            
    user_recs_list = user_recs.collect()
    user_rec_dict = {}
    for row in user_recs_list:
        user_id = str(row["user_id"])
        if user_id not in user_rec_dict:
            user_rec_dict[user_id] = []
        user_rec_dict[user_id].append(str(row["song_id"]))

    interaction_count = training_data.count()
    item_count = len(catalog_song_ids)

              
    model_json = {
        "model_type": "als_collaborative_filtering",
        "metadata": build_model_metadata(
            "als_collaborative_filtering",
            source_counts={
                "training_interactions": interaction_count,
                "mysql_recommendation_rows": user_recs.count(),
                "total_users": len(user_rec_dict),
                "total_items": item_count,
            },
            model_params={
                "rank": ALS_RANK,
                "maxIter": ALS_MAX_ITER,
                "regParam": 0.1,
                "implicitPrefs": True,
                "alpha": 20.0,
            },
            metrics=metrics,
            output_paths={
                "collaborative_model": COLLABORATIVE_MODEL_PATH,
                "spark_native_model": native_model_path,
            },
            catalog_version=build_catalog_version(catalog_song_ids),
            model_version=model_version,
            id_mapping={
                "strategy": "dense_rank",
                "user_key": "source-qualified user identifier",
                "song_key": "site_song_id",
            },
        ),
        "training_data": {
            "training_interactions": interaction_count,
            "mysql_samples": user_recs.count(),
            "total_users": len(user_rec_dict),
            "total_items": item_count
        },
        "model_params": {
            "rank": ALS_RANK,
            "maxIter": ALS_MAX_ITER,
            "regParam": 0.1,
            "implicitPrefs": True,
            "alpha": 20.0
        },
        "performance": metrics,
        "recommendations": user_rec_dict,
        "id_mapping": {
            "strategy": "dense_rank",
            "user_key": "source-qualified user identifier",
            "song_key": "site_song_id"
        }
    }

    write_model_artifact(
        COLLABORATIVE_MODEL_PATH,
        model_json,
        coverage={
            "recommendation_user_count": len(user_rec_dict),
            "catalog_song_count": item_count,
        },
    )

    print(f"模型已保存: {COLLABORATIVE_MODEL_PATH}")
    print("=" * 60)
    return model_json


def main():
             
    spark = None
    try:
        spark = create_spark_session()
        print("Spark会话创建成功!")
        print()

                
        training_data = load_and_merge_training_data(spark)

        if training_data is None or training_data.count() < MIN_TRAINING_INTERACTIONS:
            raise RuntimeError("训练数据不足")

              
        model, metrics, fitted_training_data = train_als_model(spark, training_data)
        model_version = datetime.utcnow().strftime("%Y%m%dT%H%M%SZ")
        catalog_song_ids = collect_catalog_song_ids(fitted_training_data)
        if not catalog_song_ids:
            raise RuntimeError("训练数据未形成本站可播放曲库")

        native_model_path = "{}/{}".format(SPARK_MODEL_BASE_PATH.rstrip("/"), model_version)
        model.write().save(native_model_path)
        print("Spark ALS原生模型已保存: {}".format(native_model_path))

              
        user_recs = generate_recommendations(spark, model, fitted_training_data)

              
        model_json = save_model(
            fitted_training_data,
            metrics,
            user_recs,
            model_version,
            catalog_song_ids,
            native_model_path,
        )
        publish_hive_results(
            spark,
            fitted_training_data,
            user_recs,
            model_json["metadata"],
            metrics,
            native_model_path,
        )

        print()
        print("=" * 60)
        print("训练完成!")
        print(f"Top-K评估: {metrics}")
        print(f"模型路径: {COLLABORATIVE_MODEL_PATH}")
        print("=" * 60)

    except Exception as e:
        print(f"训练过程中发生错误: {str(e)}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
    finally:
        if spark:
            spark.catalog.clearCache()
            spark.stop()
            print("Spark会话已关闭")


if __name__ == "__main__":
    main()
