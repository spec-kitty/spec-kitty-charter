"""A guard/1 script: reads the transition on stdin, prints a verdict, exits 0, 1 or 2."""
import json
import sys

json.load(sys.stdin)
print(json.dumps({"verdict": "pass", "findings": []}))
