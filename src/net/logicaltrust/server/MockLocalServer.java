package net.logicaltrust.server;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Optional;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import burp.BurpExtender;
import burp.IExtensionStateListener;
import burp.IParameter;
import burp.IRequestInfo;
import net.logicaltrust.SimpleLogger;
import net.logicaltrust.persistent.MockRepository;

public class MockLocalServer implements IExtensionStateListener {

	private final SimpleLogger logger;
	private boolean stopped = false;
	private ServerSocket ss;
	private final int port;
	private final MockRepository repository;
	private final Pattern startLinePattern = Pattern.compile("^POST /\\?(\\d+) HTTP/1\\.\\d$", Pattern.CASE_INSENSITIVE);
	private final Pattern contentLengthPattern = Pattern.compile("^Content-Length: (\\d+)$");
	
	public MockLocalServer(int port, MockRepository repository) {
		this.logger = BurpExtender.getLogger();
		this.port = port;
		this.repository = repository;
	}
	
	public void run() {
		try {
			ss = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
			logger.debugForce("Server has started " + ss);
			while (!isStopped()) {
				serve();
			}
		} catch (IOException e) {
			e.printStackTrace(logger.getStderr());
			logger.debugForce("Cannot create server. Try with another port.");
		}
	}

	private void serve() {
		try {
			logger.debug("Waiting for connection");
			handleConnection(ss.accept());
		} catch (IOException e) {
			if (isStopped()) {
				logger.debugForce("Server has stopped");
			} else {
				e.printStackTrace(logger.getStderr());
			}
		}
	}

	private void handleConnection(Socket accept) throws IOException {
		//this is basically the world's worst HTTP server, but it's OK because we're in a controlled environment
		//We're manually handling the connection rather than using Burp's functionality because this way allows burp's
		//normal request parsing (e.g. MIME type calculation) to work as expected
		logger.debug("Connection " + accept + " accepted");
		/*BufferedReader br = new BufferedReader(new InputStreamReader(accept.getInputStream()));
		BufferedOutputStream bos = new BufferedOutputStream(accept.getOutputStream());

		String startLine = br.readLine();
		Matcher startLineMatcher = startLinePattern.matcher(startLine);
		if (!startLineMatcher.matches()) {
			logger.debugForce("WARNING: Got an unexpected request! Start line: " + startLine);
			return;
		}
		String contentLengthLine = br.readLine();
		Matcher contentLengthMatcher = contentLengthPattern.matcher(contentLengthLine);
		if (!contentLengthMatcher.matches()) {
			logger.debugForce("WARNING: Couldn't parse Content-Length header. Got: " + contentLengthLine);
		}

		br.readLine();
		char[] mockedRequest = new char[Integer.parseInt(contentLengthMatcher.group(1))];
		if (br.read(mockedRequest) != mockedRequest.length) {
			logger.debugForce("Warning: didn't receive enough input from a request. This is not fatal, but shouldn't happen.");
		}

		bos.write("HTTP/1.0 292 Mock\r\nContent-Type: application/x\r\n\r\n".getBytes());
		bos.close();
		br.close();*/
		byte[] buf = new byte[1024];
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		while (accept.getInputStream().read(buf) != -1) bos.write(buf);
		byte[] fullReq = bos.toByteArray();
		IRequestInfo req = BurpExtender.getCallbacks().getHelpers().analyzeRequest(fullReq);
		Optional<String> id = req.getParameters().stream()
				.filter(p -> p.getType() == IParameter.PARAM_URL)
				.filter(p -> p.getName().equalsIgnoreCase("id"))
				.findFirst()
				.map(IParameter::getName);

		if (!id.isPresent()) {
			logger.debugForce("ERROR: Couldn't get an ID for a request!");
			return;
		}


		accept.getOutputStream().write(repository.getEntryById(id.get()).handleResponse())

		accept.close();
	}
	
	private synchronized boolean isStopped() {
		return stopped;
	}
	
	private synchronized void setStopped(boolean stopped) {
		this.stopped = stopped;
	}

	@Override
	public void extensionUnloaded() {
		setStopped(true);
		try {
			ss.close();
		} catch (IOException e) {
			e.printStackTrace(logger.getStderr());
		}
	}
	
}
