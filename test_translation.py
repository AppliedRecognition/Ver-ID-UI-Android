import argparse
import re
import sys
from translations import strings


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify translation XML completeness")
    parser.add_argument("filename", help="XML file to test")
    args = parser.parse_args()

    try:
        with open(args.filename, "r", encoding="utf-8") as f:
            xml = f.read()
    except FileNotFoundError:
        print(f"File not found: {args.filename}", file=sys.stderr)
        return 2

    words = strings()

    missing = []
    for word in words:
        # Escape any regex metacharacters in the original string
        escaped = re.escape(word)
        present = re.search(rf"<original>{escaped}</original>", xml)
        if present is None and word not in missing:
            missing.append(word)
        else:
            empty = re.search(rf"<original>{escaped}</original>\s*<translation></translation>", xml)
            if empty is not None:
                missing.append(word)

    if len(missing) == 0:
        print("All translated")
        return 0
    else:
        print("Missing:")
        for word in missing:
            print(word)
        return 1


if __name__ == "__main__":
    sys.exit(main())
