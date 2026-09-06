#!/usr/bin/env python3
                                                             

import argparse
import json
import time


def parse_sizes(value):
    sizes = [int(item.strip()) for item in value.split(",") if item.strip()]
    if not sizes or any(size < 1 for size in sizes):
        raise argparse.ArgumentTypeError("sizes must be positive integers")
    return sizes


def synthetic_interactions(size, user_count, item_count):
    return [
        (index % user_count, (index * 37 + index // user_count) % item_count, 1.0)
        for index in range(size)
    ]


def main(argv=None):
    parser = argparse.ArgumentParser(description="Benchmark Spark ALS fitting without publishing model artifacts.")
    parser.add_argument("--master", default="spark://192.168.153.131:7077")
    parser.add_argument("--sizes", type=parse_sizes, default=[10000, 50000, 100000])
    parser.add_argument("--rank", type=int, default=8)
    parser.add_argument("--max-iter", type=int, default=10)
    parser.add_argument("--reg-param", type=float, default=0.1)
    parser.add_argument("--alpha", type=float, default=20.0)
    parser.add_argument("--output")
    args = parser.parse_args(argv)

    from pyspark.ml.recommendation import ALS
    from pyspark.sql import SparkSession

    spark = SparkSession.builder.appName("HaoRanMusicAlsBenchmark").master(args.master).getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
    results = []
    try:
        for size in args.sizes:
            users = min(1000, max(20, size // 20))
            items = min(2000, max(40, size // 10))
            interactions = synthetic_interactions(size, users, items)
            frame = spark.createDataFrame(interactions, ["user_id", "song_id", "rating"]).repartition(8).cache()
            frame.count()
            fit_started = time.perf_counter()
            model = ALS(
                rank=args.rank,
                maxIter=args.max_iter,
                regParam=args.reg_param,
                implicitPrefs=True,
                alpha=args.alpha,
                userCol="user_id",
                itemCol="song_id",
                ratingCol="rating",
                coldStartStrategy="drop",
            ).fit(frame)
            fit_seconds = time.perf_counter() - fit_started
            started = time.perf_counter()
            user_factors = model.userFactors.count()
            item_factors = model.itemFactors.count()
            elapsed = time.perf_counter() - started
            results.append({
                "interactions": size,
                "users": users,
                "items": items,
                "fit_seconds": round(fit_seconds, 3),
                "materialization_seconds": round(elapsed, 3),
                "user_factors": user_factors,
                "item_factors": item_factors,
            })
            frame.unpersist()
        report = {
            "model": {
                "rank": args.rank,
                "max_iter": args.max_iter,
                "reg_param": args.reg_param,
                "implicit_preferences": True,
                "alpha": args.alpha,
            },
            "results": results,
        }
        if args.output:
            with open(args.output, "w", encoding="utf-8") as stream:
                json.dump(report, stream, ensure_ascii=False, indent=2)
                stream.write("\n")
        print(json.dumps(report, ensure_ascii=False))
    finally:
        spark.stop()


if __name__ == "__main__":
    main()
