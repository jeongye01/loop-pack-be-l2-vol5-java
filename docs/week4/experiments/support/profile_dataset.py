"""Summarize a verified CSV dataset without connecting to or changing a DB."""
import argparse
import csv
import hashlib
import json
import math
import statistics
from collections import Counter
from pathlib import Path


def summarize(values):
    ordered = sorted(values)
    return {
        "count": len(ordered), "min": ordered[0], "max": ordered[-1],
        "mean": statistics.mean(ordered), "median": statistics.median(ordered),
        "p95NearestRank": ordered[math.ceil(len(ordered) * .95) - 1],
        "zeroCount": ordered.count(0), "distinctCount": len(set(ordered)),
    }


def profile(dataset):
    manifest_file = dataset / "manifest.json"
    manifest = json.loads(manifest_file.read_text())
    counts = {}
    for name, expected in manifest["files"].items():
        path = dataset / name
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        if digest.hexdigest() != expected["sha256"]:
            raise ValueError(f"CSV checksum mismatch: {name}")
        with path.open(encoding="utf-8", newline="") as stream:
            counts[name] = sum(1 for _ in csv.DictReader(stream))
        if counts[name] != expected["rows"]:
            raise ValueError(f"CSV row count mismatch: {name}")

    def rows(name):
        with (dataset / name).open(encoding="utf-8", newline="") as stream:
            yield from csv.DictReader(stream)

    products = list(rows("products.csv"))
    likes = Counter()
    user_likes = Counter()
    for row in rows("likes.csv"):
        if not row["deleted_at"]:
            likes[int(row["product_id"])] += 1
            user_likes[int(row["user_id"])] += 1
    likes_per_user = [user_likes[int(row["id"])] for row in rows("users.csv")]
    return {
        "dataset": dataset.name,
        "manifestSha256": hashlib.sha256(manifest_file.read_bytes()).hexdigest(),
        "source": "CSV; not a live DB snapshot",
        "generation": manifest["generation"], "rows": counts,
        "productsPerBrand": dict(sorted(Counter(row["brand_id"] for row in products).items(),
                                        key=lambda item: int(item[0]))),
        "price": summarize([int(row["price"]) for row in products]),
        "stock": summarize([int(row["quantity"]) for row in rows("stocks.csv")]),
        "activeLikesPerProduct": summarize([likes[int(row["id"])] for row in products]),
        "activeLikesPerUser": summarize(likes_per_user),
        "activeUserLikeHistogram": dict(sorted(Counter(likes_per_user).items())),
        "activeLikeHistogram": dict(sorted(Counter(likes[int(row["id"])] for row in products).items())),
        "brand7": {"products": sum(row["brand_id"] == "7" for row in products),
                   "activeLikes": sum(likes[int(row["id"])] for row in products
                                      if row["brand_id"] == "7")},
        "deletedRows": {name: sum(bool(row["deleted_at"]) for row in rows(name))
                        for name in ("brands.csv", "products.csv", "stocks.csv", "likes.csv")},
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dataset", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = profile(args.dataset)
    # Refuse to replace evidence from an earlier dataset/run.
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
