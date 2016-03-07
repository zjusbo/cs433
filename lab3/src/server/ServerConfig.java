package server;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Scanner;

public class ServerConfig {
	static private HashSet<String> requiredLabels;

	static {
		requiredLabels = new HashSet<String>();
		requiredLabels.add("listen"); // case insensitive
		requiredLabels.add("threadpoolsize");
		requiredLabels.add("cachesize");
		requiredLabels.add("documentroot");
		requiredLabels.add("servername");
	}

	public int port, threadPoolSize, cacheSize;
	public String servername, documentRoot;
	public String userAgent;

	private ServerConfig() {
	};

	@Override
	public String toString() {
		String s;
		s = String.format(
				"Config:\n listen: %d\n threadpoolsize: %d\n cachesize: %d\n documentroot: %s\n servername: %s\n",
				this.port, this.threadPoolSize, this.cacheSize, this.documentRoot, this.servername);
		return s;
	}

	static public ServerConfig parse(String filename) throws FileNotFoundException {
		HashSet<String> parsedLabel = new HashSet<String>();

		ServerConfig sc = new ServerConfig();

		Scanner scanner = new Scanner(new FileReader(filename));
		while (scanner.hasNext()) {
			String line = scanner.nextLine().trim(); // delete heading and
														// trailing white spaces
			// ignore <virtualHost line
			if (line.isEmpty() || line.startsWith("<")) {
				continue;
			}
			String token[] = line.split("\\s+");

			if (token == null || token.length < 2) {
				System.out.println("configuration file illegal: " + line);
				scanner.close();
				return null;
			}
			// case insensitive
			String label = token[0].toLowerCase();
			String value = token[1].toLowerCase();
			parsedLabel.add(label);
			switch (label) {
			case "listen":
				sc.port = Integer.valueOf(value);
				break;
			case "threadpoolsize":
				sc.threadPoolSize = Integer.valueOf(value);
				break;
			case "cachesize":
				sc.cacheSize = Integer.valueOf(value);
				break;
			case "documentroot":
				sc.documentRoot = value;
				break;
			case "servername":
				sc.servername = value;
				break;
			case "user-agent":
				sc.userAgent = value;
				break;
			default:
				System.err.println("Unknown label: " + label);
				break;
			}

		}
		scanner.close();
		boolean isError = false;
		for (String label : ServerConfig.requiredLabels) {
			if (!parsedLabel.contains(label)) {
				isError = true;
				System.err.println("Configuration file format error: " + label + " required");
			}
		}
		if (isError) {
			return null;
		} else {
			return sc;
		}

	}
}
