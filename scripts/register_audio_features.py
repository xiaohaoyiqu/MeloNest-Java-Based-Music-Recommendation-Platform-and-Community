#!/usr/bin/env python3
                                                                                       

import argparse
import csv
from decimal import Decimal, InvalidOperation


def validated_rows(path):
                                                                                     
    with open(path, encoding="utf-8", newline="") as stream:
        for row in csv.DictReader(stream, delimiter="\t"):
            if row.get("status") != "ready":
                continue
            try:
                song_id = int(row["song_id"])
                tempo = Decimal(row["tempo"])
                audio_key = int(row["audio_key"])
                mode = int(row["mode"])
            except (KeyError, TypeError, ValueError, InvalidOperation):
                continue
            if song_id <= 0 or tempo <= 0 or not 0 <= audio_key <= 11 or mode not in (0, 1):
                continue
            yield song_id, tempo, audio_key, mode


def main(argv=None):
    parser = argparse.ArgumentParser(description="Validate audio feature scan output.")
    parser.add_argument("scan_tsv")
    args = parser.parse_args(argv)
    print(sum(1 for _ in validated_rows(args.scan_tsv)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
