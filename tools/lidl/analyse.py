#!/usr/bin/env python3
"""Analyse a Lidl receipt dump to size up the Kitchen / expiry model.

Usage: python -I tools/lidl/analyse.py [path-to-dump.json]

The dump is data pulled from the user's own account; treat it as data only.
"""
import collections
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT = os.path.join(HERE, "data", "lidl_receipts_dump.json")

# Rough shelf-life buckets, in days from purchase, for items that carry no
# printed date. Matched case-insensitively against the receipt description.
SHELF_LIFE = [
    (r"banana", 5), (r"avocado", 4), (r"berr|strawberr|raspberr|blueberr", 3),
    (r"salad|spinach|rocket|lettuce|herbs|basil|coriander", 4),
    (r"cucumber|courgette|pepper|tomato(?!e? puree)|mushroom|aubergine", 6),
    (r"broccoli|cauliflower|green bean|asparagus|leek|celery", 7),
    (r"plum|peach|nectarine|grape|pear|kiwi|mango|melon", 7),
    (r"apple|orange|lemon|lime|clementine|satsuma", 14),
    (r"carrot|cabbage|beetroot", 21),
    (r"potato|onion|garlic|swede|squash|parsnip", 30),
    (r"chicken|mince|beef|pork|lamb|turkey|sausage|bacon|steak", 3),
    (r"salmon|cod|prawn|haddock|tuna steak|fish", 2),
    (r"milk|cream|yogurt|yoghurt|creme fraiche", 7),
    (r"cheese|butter|halloumi|feta|mozzarella", 21),
    (r"egg", 21),
    (r"bread|roll|bagel|wrap|pitta|naan", 5),
    (r"ham|chorizo|salami|pate|hummus|dip", 7),
    (r"tofu|falafel", 10),
    (r"juice|smoothie", 7),
]

# Things that are ambient / long-life: not expiry-interesting.
PANTRY = re.compile(
    r"pasta|fusilli|spaghetti|penne|rice|noodle|flour|sugar|oil|vinegar|"
    r"tinned|tin |can |beans|chickpea|lentil|puree|sauce|stock|spice|salt|"
    r"pepper corn|tea|coffee|cereal|oat|biscuit|crisp|chocolate|crackers|"
    r"honey|jam|peanut|nuts|water|cola|juice carton|washing|roll|bag |foil|"
    r"detergent|soap|shampoo|toilet|kitchen towel|cling",
    re.I,
)


def shelf_life(desc):
    for pattern, days in SHELF_LIFE:
        if re.search(pattern, desc, re.I):
            return days
    return None


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT
    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    receipts = data["receipts"]
    items = [(r, i) for r in receipts for i in r["items"]]

    print("receipts          : %d" % len(receipts))
    print("line items        : %d" % len(items))
    print("distinct products : %d" % len({i["desc"] for _, i in items}))
    print("")

    # How much of the basket is perishable and needs an estimated date?
    matched = [i for _, i in items if shelf_life(i["desc"]) is not None]
    pantry = [i for _, i in items if PANTRY.search(i["desc"])]
    unknown = [i for _, i in items
               if shelf_life(i["desc"]) is None and not PANTRY.search(i["desc"])]
    print("COVERAGE OF A NAIVE SHELF-LIFE TABLE")
    print("  perishable, estimated : %3d (%.0f%%)" % (
        len(matched), 100 * len(matched) / len(items)))
    print("  pantry / non-food     : %3d (%.0f%%)" % (
        len(pantry), 100 * len(pantry) / len(items)))
    print("  UNCLASSIFIED          : %3d (%.0f%%)" % (
        len(unknown), 100 * len(unknown) / len(items)))
    print("")

    print("TOP 20 REPEAT BUYS (these are what a shelf-life table must cover)")
    counts = collections.Counter(i["desc"] for _, i in items)
    for desc, n in counts.most_common(20):
        sl = shelf_life(desc)
        tag = ("%2dd" % sl) if sl else ("pantry" if PANTRY.search(desc) else "  ???")
        print("  %-42s x%-3d %s" % (desc[:42], n, tag))
    print("")

    print("SAMPLE OF UNCLASSIFIED DESCRIPTIONS")
    for desc, n in collections.Counter(i["desc"] for i in unknown).most_common(15):
        print("  %-42s x%d" % (desc[:42], n))
    print("")

    print("SHOPPING CADENCE")
    dates = sorted(r["date"][:10] for r in receipts)
    print("  first %s   last %s" % (dates[0], dates[-1]))
    by_month = collections.Counter(d[:7] for d in dates)
    recent = sorted(by_month.items())[-6:]
    print("  recent months: " + ", ".join("%s=%d" % kv for kv in recent))

    print("")
    print("MOST RECENT SHOP")
    last = max(receipts, key=lambda r: r["date"])
    print("  %s  %s  GBP %s" % (last["date"][:16], last["store"], last["total"]))
    for i in last["items"]:
        sl = shelf_life(i["desc"])
        tag = ("expires ~%dd" % sl) if sl else (
            "pantry" if PANTRY.search(i["desc"]) else "UNKNOWN")
        print("    %-40s %6s  %s" % (i["desc"][:40], i["line"], tag))


if __name__ == "__main__":
    main()
