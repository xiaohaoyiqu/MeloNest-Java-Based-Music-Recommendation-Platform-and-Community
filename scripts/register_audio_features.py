#!/usr/bin/env python3
                                                                                   

import argparse
import csv
from decimal import Decimal, InvalidOperation


def decimal_in_range(value, lower, upper):
    parsed = Decimal(value)
    if not lower <= parsed <= upper:
        raise InvalidOperation()
    return parsed


def validated_rows(path):
    seen = set()
    with open(path, "r", encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle, delimiter="\t"):
            if row.get("status") != "ready":
                continue
            song_id = int(row["song_id"])
            if song_id <= 0 or song_id in seen:
                raise ValueError("invalid or duplicate song_id")
            seen.add(song_id)
            tempo = decimal_in_range(row["tempo"], Decimal("40"), Decimal("240"))
            audio_key = int(row["audio_key"])
            mode = int(row["mode"])
            if audio_key not in range(12) or mode not in (0, 1):
                raise ValueError("invalid key or mode for song_id=%d" % song_id)
            yield song_id, tempo, audio_key, mode


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("results")
    args = parser.parse_args()
    rows = list(validated_rows(args.results))
    print("START TRANSACTION;")
    print("SET @audio_feature_changed_rows=0;")
    for song_id, tempo, audio_key, mode in rows:
        print(
            "UPDATE song SET "
            "tempo=COALESCE(tempo,{tempo}), "
            "audio_key=COALESCE(audio_key,{audio_key}), "
            "mode=COALESCE(mode,{mode}), "
            "audio_features_updated=NOW() "
            "WHERE id={song_id} AND status=1 AND deleted=0 "
            "AND (tempo IS NULL OR audio_key IS NULL OR mode IS NULL);".format(
                song_id=song_id, tempo=tempo, audio_key=audio_key, mode=mode
            )
        )
        print("SET @audio_feature_changed_rows=@audio_feature_changed_rows+ROW_COUNT();")
    print("SELECT @audio_feature_changed_rows AS changed_rows;")
    print("COMMIT;")


if __name__ == "__main__":
    main()
