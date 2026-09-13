"""Entry point — runs the Java Edition listener and the Bedrock bridge
listener side by side. See java_server.py and bedrock_bridge.py for what
each actually does."""

import threading

import java_server
import bedrock_bridge


def main():
	threading.Thread(target=java_server.serve, daemon=True).start()
	threading.Thread(target=bedrock_bridge.serve, daemon=True).start()
	threading.Event().wait()  # block forever; both servers run on daemon threads


if __name__ == "__main__":
	main()
