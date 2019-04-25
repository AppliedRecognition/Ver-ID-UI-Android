import argparse
import re
from translations import strings

parser = argparse.ArgumentParser()
parser.add_argument('filename',help="XML file to test")
args = parser.parse_args()

f = open(args.filename, "r")
xml = f.read()

words = strings()

missing = []
for word in words:
    match = re.search("<original>"+word+"</original>", xml)
    if match == None and word not in missing:
        missing.append(word)

if len(missing) == 0:
    print("All translated")
else:
    print("Missing:")
    for word in missing:
        print(word)
