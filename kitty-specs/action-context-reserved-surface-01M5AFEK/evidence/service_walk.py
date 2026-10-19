"""WP06 / T028: the service's getActionContext answers for every action at depths 0, 1, 2.

Usage (service already running, see walk-comparison.md for the bootRun command):
    python3 -I service_walk.py core-walk.json http://localhost:18080 > service-walk.json

The action list comes from core-walk.json (every ``action:`` node of the graph). Output:
    {"base_url", "actions": {urn: {depth: <ActionContext body or {"http_status": n, "body": ...}>}}}
"""
import json
import sys
import urllib.error
import urllib.request


def get(url: str):
    try:
        with urllib.request.urlopen(url, timeout=30) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        return {"http_status": e.code, "body": e.read().decode()}


def main() -> None:
    core = json.load(open(sys.argv[1]))
    base = sys.argv[2].rstrip("/")
    out = {"base_url": base, "actions": {}}
    for urn in sorted(core["actions"]):
        mission_type, action = urn.split(":", 1)[1].split("/", 1)
        out["actions"][urn] = {
            str(d): get(f"{base}/api/v1/mission-types/{mission_type}/actions/{action}/context?depth={d}")
            for d in (0, 1, 2)
        }
    json.dump(out, sys.stdout, indent=1, sort_keys=True)
    print()


if __name__ == "__main__":
    main()
