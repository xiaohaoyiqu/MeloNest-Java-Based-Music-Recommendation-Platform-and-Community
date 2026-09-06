#!/usr/bin/env python3
                                                                       

import argparse
import json

from pyspark.ml.recommendation import ALSModel
from pyspark.sql import SparkSession
from pyspark.sql.functions import col

from recommendation_model_utils import verify_model_artifact


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact", required=True)
    args = parser.parse_args()

    model, manifest = verify_model_artifact(
        args.artifact, expected_model_type="als_collaborative_filtering"
    )
    model_version = manifest["model_version"]
    native_model_path = model["metadata"]["output_paths"]["spark_native_model"]

    spark = (
        SparkSession.builder.appName("HaoRanMusicRecommendationVerification")
        .config("spark.hadoop.hive.metastore.uris", "thrift://192.168.153.131:9083")
        .config(
            "spark.sql.warehouse.dir",
            "hdfs://mycluster/Datas/haoranMusicPlatform/hive/warehouse",
        )
        .enableHiveSupport()
        .getOrCreate()
    )
    spark.sparkContext.setLogLevel("WARN")
    try:
        database = "haoranmusic_recommend"
        interactions = spark.table(database + ".dws_recommend_interaction").filter(
            col("model_version") == model_version
        ).count()
        recommendations = spark.table(database + ".ads_user_song_recommendation").filter(
            col("model_version") == model_version
        ).count()
        registry = spark.table(database + ".ads_recommend_model_run").filter(
            col("model_version") == model_version
        )
        registry_rows = registry.count()
        statuses = sorted(row["status"] for row in registry.select("status").collect())
        native_model = ALSModel.load(native_model_path)
        user_factors = native_model.userFactors.count()
        item_factors = native_model.itemFactors.count()
        payload = {
            "artifact_sha256": manifest["artifact_sha256"],
            "catalog_version": manifest["catalog_version"],
            "hive_interactions": interactions,
            "hive_recommendations": recommendations,
            "hive_registry_rows": registry_rows,
            "hive_statuses": statuses,
            "native_item_factors": item_factors,
            "native_model_path": native_model_path,
            "native_user_factors": user_factors,
            "model_version": model_version,
            "success": (
                interactions > 0
                and recommendations > 0
                and registry_rows == 1
                and statuses == ["SUCCESS"]
                and user_factors > 0
                and item_factors > 0
            ),
        }
        print("HAORAN_RECOMMENDATION_VERIFY=" + json.dumps(payload, sort_keys=True))
        if not payload["success"]:
            raise RuntimeError("recommendation training verification failed")
    finally:
        spark.stop()


if __name__ == "__main__":
    main()
