"""Entry point for hosts (like Danbot's Python egg) that only let you pick
which .py file to run, with no way to set a custom environment variable —
sets the one secret config.py needs before importing anything else, then
just runs the real server. Point "App py file" at THIS file instead of
server.py.

If you ever move to a host that DOES let you set real environment
variables, prefer that instead (edit the value below, or better, delete
this file and set VERIFY_WORKER_SHARED_SECRET as a real env var) — a value
sitting in a plain .py file is fine for a locked-down single-purpose
container, but a real env var is the better home for a secret in general.
"""

import os

os.environ.setdefault("VERIFY_WORKER_SHARED_SECRET", "77d0b57c983a61ec2f69a052a68e9cb1d3d612684afcb3095fea0c6862a87b3d")

import server

if __name__ == "__main__":
	server.main()
