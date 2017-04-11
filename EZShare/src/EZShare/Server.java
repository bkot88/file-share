package EZShare;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import javax.net.ServerSocketFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Server {

	private ServerArgs serverArgs;
	private ConcurrentHashMap<Resource, String> resources = new ConcurrentHashMap<>();
	public static final int TIME_OUT_LIMIT = 5000;
	private static Logger logger;
	private Response successResponse;

	public static void main(String[] args) {
		// TODO: Remove if -- solely for testing purposes
		if (args.length == 0) {
			String[] args2 = { "-" + Constants.debugOption };
			args = args2;
		}

		Server server = new Server(args);

		if (server.serverArgs.hasOption(Constants.debugOption)) {
			System.setProperty("log4j.configurationFile", "../logging-config-debug.xml");
		} else {
			System.setProperty("log4j.configurationFile", "../logging-config-default.xml");
		}
		logger = LogManager.getRootLogger();
		logger.debug("Debugger enabled");

		server.parseCommand();
		server.listen();
	}

	/**
	 * Constructor for Server
	 * 
	 * @param args
	 *            String[] command line arguments
	 */
	public Server(String[] args) {
		serverArgs = new ServerArgs(args);
		// TODO: populate resources from file/Database
	}

	/**
	 * Examines the command line arguments
	 * 
	 * @return Command object encapsulating the arguments provided
	 */
	public Command parseCommand() {
		if (serverArgs.cmd.hasOption(Constants.advertisedHostNameOption)) {
			logger.debug("Advertised hostname command found");
		}
		if (serverArgs.cmd.hasOption(Constants.portOption)) {
			logger.debug("Port command found");
		}
		if (serverArgs.cmd.hasOption(Constants.secretOption)) {
			logger.debug("Secret command found");
		}

		logger.info("Using secret " + this.serverArgs.getSafeSecret());
		logger.info("Using advertised hostname " + this.serverArgs.getSafeHost());
		logger.info("Bound to port " + this.serverArgs.getSafePort());
		return null;
	}

	/**
	 * 
	 */
	public void listen() {
		// TODO: Implement blocking until client request
		ServerSocketFactory factory = ServerSocketFactory.getDefault();
		
		try (ServerSocket server = factory.createServerSocket(this.serverArgs.getSafePort())) {
			logger.info("Listening for request...");

			// Wait for connection
			while (true) {
				Socket client = server.accept();
				logger.info("Received request");

				// TODO: replace this with a call to a class that will serve
				// client
				// i.e., implement a runnable class
				// lambda expression
				Thread t = new Thread(() -> this.serveClient(client));
				t.start();
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			logger.error(e.getClass().getName() + " " + e.getMessage());
			e.printStackTrace();
		}

	}

	/**
	 * 
	 * @param client
	 */
	private void serveClient(Socket client) {
		try (Socket clientSocket = client) {
			clientSocket.setSoTimeout(TIME_OUT_LIMIT);
			DataInputStream input = new DataInputStream(clientSocket.getInputStream());
			DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());

			String request = input.readUTF();
			Command command = (new Command()).fromJson(request);
			logger.debug("RECEIVED: " + command.toJson());

			if (!parseCommandForErrors(command, output)) {

				switch (command.command) {
				case Constants.queryCommand:
					processQueryCommand(command, output);
					break;
				case Constants.fetchCommand:
					processFetchCommand(command, output);
					break;
				case Constants.exchangeCommand:
					processExchangeCommand(command, output);
					break;
				case Constants.publishCommand:
					processPublishCommand(command, output);
					break;
				case Constants.shareCommand:
					processShareCommand(command, output);
					break;
				case Constants.removeCommand:
					processRemoveCommand(command, output);
					break;
				case Constants.invalidCommand:
					processInvalidCommand(command, output);
					break;
				default:
					processMissingOrInvalidCommand(command, output);
					break;
				}
			}

			clientSocket.close();

		} catch (SocketTimeoutException e) {
			logger.error(e.getClass().getName() + " " + e.getMessage());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			logger.error(e.getClass().getName() + " " + e.getMessage());
		}
	}

	private boolean parseCommandForErrors(Command command, DataOutputStream output) {
		// "String values must not contain the "\0" character, nor start or end
		// with whitespace."
		// "The field must not be the single character "*"."
		// TODO Check if every possible case is covered
		boolean errorFound = false;

		ArrayList<String> stringValues = new ArrayList<>();
		stringValues.add(command.secret);
		if (command.resource != null) {
			String[] strings = { command.resource.name, command.resource.description, command.resource.uri,
					command.resource.channel, command.resource.owner, command.resource.ezserver };
			stringValues.addAll(Arrays.asList(strings));
			if (command.resource.tags != null) {
				stringValues.addAll(command.resource.tags);
			}
		}
		if (command.resourceTemplate != null) {
			String[] strings = { command.resourceTemplate.name, command.resourceTemplate.description,
					command.resourceTemplate.uri, command.resourceTemplate.channel, command.resourceTemplate.owner,
					command.resourceTemplate.ezserver };
			stringValues.addAll(Arrays.asList(strings));
			if (command.resourceTemplate.tags != null) {
				stringValues.addAll(command.resourceTemplate.tags);
			}
		}

		for (String value : stringValues) {
			if (value == null) {
				// Do nothing
			} else if (value.equals("*")) {
				sendResponse(buildErrorResponse("String values cannot be *"), output);
				errorFound = true;
				break;
			} else if (value != value.trim()) {
				sendResponse(buildErrorResponse("String values cannot start or end with whitespace(s)"), output);
				errorFound = true;
				break;
			} else if (value.contains("\0")) {
				sendResponse(buildErrorResponse("String values cannot contain \0"), output);
				errorFound = true;
				break;
			}
		}

		return errorFound;
	}

	private void processInvalidCommand(Command command, DataOutputStream output) {
		sendResponse(buildErrorResponse("invalid command"), output);
	}

	private void processMissingOrInvalidCommand(Command command, DataOutputStream output) {
		sendResponse(buildErrorResponse("missing or incorrect type for command"), output);
	}

	private void processPublishCommand(Command command, DataOutputStream output) {
		logger.debug("Processing PUBLISH command");

		Response response = buildErrorResponse("cannot publish resource");

		// Check for invalid resource fields
		if (command.resource == null) {
			response = buildErrorResponse("missing resource");
		} else if (command.resource.uri == null || command.resource.uri.length() == 0
				|| command.resource.uri.equals(Constants.emptyString)) {
			// "The URI must be present, ..."
			response = buildErrorResponse("invalid resource - missing uri");
		} else {
			try {
				URI uri = new URI(command.resource.uri);

				if (!uri.isAbsolute()) {
					// "... must be absolute ..."
					response = buildErrorResponse("invalid resource - uri must be absolute");
				} else if (uri.getScheme().equals("file")) {
					// "... and cannot be a file scheme."
					response = buildErrorResponse("invalid resource - uri cannot be a file scheme");
				} else if (this.resources.containsKey(command.resource)
						&& !this.resources.get(command.resource).equals(command.resource.owner)) {
					// "Publishing a resource with the same channel and URI but
					// different owner is not allowed."
					response = buildErrorResponse("cannot publish resource - uri already exists in channel");
				} else {
					// SUCCESS
					command.resource.ezserver = this.serverArgs.getSafeHost() + ":" + this.serverArgs.getSafePort();
					this.resources.put(command.resource, command.resource.owner);
					response = buildSuccessResponse();
				}
			} catch (URISyntaxException e) {
				logger.error(e.getClass().getName() + " " + e.getMessage());
				sendResponse(buildErrorResponse("invalid resource - invalid uri"), output);
			}
		}

		sendResponse(response, output);
	}

	private void processQueryCommand(Command command, DataOutputStream output) {
		logger.debug("Processing QUERY command");
		
		// TODO Implement query relay (how???)

		if (command.resourceTemplate == null) {
			sendResponse(buildErrorResponse("missing resourceTemplate"), output);
		} else {

			sendResponse(buildSuccessResponse(), output);

			// TODO Don't iterate over whole map!!
			int count = 0;
			for (ConcurrentHashMap.Entry<Resource, String> entry : this.resources.entrySet()) {
				Resource resource = entry.getKey();
				String owner = entry.getValue();

				// Query rules:
				// "(The template channel equals (case sensitive) the resource
				// channel AND
				boolean equalChannel = resource.channel.equals(command.resourceTemplate.channel);

				// If the template contains an owner that is not "", then the
				// candidate owner must equal it (case sensitive) AND
				boolean equalOrNoOwner = command.resourceTemplate.owner.equals(Constants.emptyString) ? true
						: resource.owner.equals(command.resourceTemplate.owner);

				// Any tags present in the template also are present in the
				// candidate (case insensitive) AND
				boolean equalTags = command.resourceTemplate.tags.size() == 0 ? true
						: resource.tags.containsAll(command.resourceTemplate.tags);

				// If the template contains a URI then the candidate URI matches
				// (case sensitive) AND
				boolean equalOrNoUri = command.resourceTemplate.uri.equals(Constants.emptyString) ? true
						: resource.uri.equals(command.resourceTemplate.uri);

				// (The candidate name contains the template name as a substring
				// (for non "" template name) OR
				boolean nameIsSubstring = command.resourceTemplate.name.equals(Constants.emptyString) ? true
						: resource.name.equals(command.resourceTemplate.name);

				// The candidate description contains the template description
				// as a substring (for non "" template descriptions) OR
				boolean descriptionIsSubstring = command.resourceTemplate.description.equals(Constants.emptyString)
						? true : resource.description.equals(command.resourceTemplate.description);

				// The template description and name are both ""))"
				boolean noDescriptionAndName = command.resourceTemplate.name.equals(Constants.emptyString)
						&& command.resourceTemplate.description.equals(Constants.emptyString);

				if (equalChannel && equalOrNoOwner && equalTags && equalOrNoUri
						&& (nameIsSubstring || descriptionIsSubstring || noDescriptionAndName)) {
					count++;

					// "The server will never reveal the owner of a resource in
					// a response. If a resource has an owner then it will be
					// replaced with the "*" character."
					resource.owner = "*";

					sendString(resource.toJson(), output);

					// Reset owner
					resource.owner = owner;
				}
			}
			Response response = new Response();
			response.resultSize = count;
			sendResponse(response, output);
		}
	}

	private void processExchangeCommand(Command command, DataOutputStream output) {
		logger.debug("Processing EXCHANGE command");
		// TODO Auto-generated method stub
	}

	private void processFetchCommand(Command command, DataOutputStream output) {
		logger.debug("Processing FETCH command");
		// TODO Auto-generated method stub
		

		// Check for invalid resourceTemplate fields
		if (command.resourceTemplate == null) {
			sendResponse(buildErrorResponse("missing resourceTemplate"),output);
		} else if (command.resourceTemplate.uri == null || command.resourceTemplate.uri.length() == 0
				|| command.resourceTemplate.uri.equals(Constants.emptyString)) {
			sendResponse(buildErrorResponse("invalid resourceTemplate - missing uri"), output);
		} else if (command.resourceTemplate.channel == null || command.resourceTemplate.channel.length() == 0
				|| command.resourceTemplate.channel.equals(Constants.emptyString)) {
			sendResponse(buildErrorResponse("invalid resourceTemplate - missing channel"), output);
		} else if (!this.resources.containsKey(command.resourceTemplate)) {
			sendResponse(buildErrorResponse("resource doesn't exist"), output);
		} else {
			// TODO Don't iterate over Map, do something else!!
			for (ConcurrentHashMap.Entry<Resource, String> entry : this.resources.entrySet()) {
				Resource resource = entry.getKey();
				String owner = entry.getValue();
				if (resource.channel.equals(command.resourceTemplate.channel) && resource.uri.equals(command.resourceTemplate.uri)) {
					sendResponse(buildSuccessResponse(), output);
										
					try {
						URI uri = new URI(resource.uri);
						File file = new File(uri);
				        long length = file.length();

						resource.resourceSize = length;
												
						// "The server will never reveal the owner of a resource in
						// a response. If a resource has an owner then it will be
						// replaced with the "*" character."
						resource.owner = "*";

						sendString(resource.toJson(), output);

						// Reset owner
						resource.owner = owner;
						
						// TODO convert file into bytes
						// TODO write bytes to output
						
						Response response = new Response();
						response.resultSize = 1;
						sendResponse(response, output);
						break;

					} catch (URISyntaxException e) {
						logger.error(e.getClass().getName() + " " + e.getMessage());
						sendResponse(buildErrorResponse("invalid resource - invalid uri"), output);
					} 
				}
			}
		}		
	}

	private void processShareCommand(Command command, DataOutputStream output) {
		logger.debug("Processing SHARE command");

		Response response = buildErrorResponse("cannot share resource");

		// Check for invalid resource fields
		if (command.secret == null || command.secret.equals(Constants.emptyString) || command.secret.length() == 0) {
			// "The server secret must be present ..."
			response = buildErrorResponse("missing resource and/or secret");
		} else if (!command.secret.equals(this.serverArgs.getSafeSecret())) {
			// "... and must equal the value known to the server."
			response = buildErrorResponse("incorrect secret");
		} else if (command.resource == null) {
			response = buildErrorResponse("missing resource and/or secret");
		} else if (command.resource.uri == null || command.resource.uri.length() == 0
				|| command.resource.uri.equals(Constants.emptyString)) {
			// "The URI must be present, ..."
			response = buildErrorResponse("invalid resource - missing uri");
		} else {
			try {
				URI uri = new URI(command.resource.uri);

				if (!uri.isAbsolute()) {
					// "..., must be absolute ..."
					response = buildErrorResponse("invalid resource - uri must be absolute");
				} else if (uri.getAuthority() != null) {
					// "..., non-authoritative ...."
					response = buildErrorResponse("invalid resource - uri must be non-authoritative");
				} else if (!uri.getScheme().equals("file")) {
					// "... and must be a file scheme."
					response = buildErrorResponse("invalid resource - uri must be a file scheme");
				} else if (this.resources.containsKey(command.resource)
						&& !this.resources.get(command.resource).equals(command.resource.owner)) {
					// "Sharing a resource with the same channel and URI but
					// different owner is not allowed."
					response = buildErrorResponse("cannot share resource - uri already exists in channel");
				} else {
					File f = new File(uri);
					if (!f.exists()) {
						// "[The URI] must point to a file on the local file
						// system that the server can read as a file."
						response = buildErrorResponse(
								"invalid resource - uri does not point to a file on the local file system");
					} else {
						// SUCCESS
						command.resource.ezserver = this.serverArgs.getSafeHost() + ":" + this.serverArgs.getSafePort();
						this.resources.put(command.resource, command.resource.owner);
						response = buildSuccessResponse();
					}
				}
			} catch (URISyntaxException e) {
				logger.error(e.getClass().getName() + " " + e.getMessage());
				sendResponse(buildErrorResponse("invalid resource - invalid uri"), output);
			}
		}

		sendResponse(response, output);
	}

	private void processRemoveCommand(Command command, DataOutputStream output) {
		logger.debug("Processing REMOVE command");

		Response response = buildErrorResponse("cannot remove resource");

		// Check for invalid resource fields
		if (command.resource == null) {
			response = buildErrorResponse("missing resource");
		} else if (command.resource.uri == null || command.resource.uri.equals(Constants.emptyString)) {
			// URI must be present
			response = buildErrorResponse("invalid resource - missing uri");
		} else if (!this.resources.containsKey(command.resource)) {
			// Resource must exist
			response = buildErrorResponse("cannot remove resource - resource does not exist");
		} else {
			// SUCCESS
			this.resources.remove(command.resource);
			response = buildSuccessResponse();
		}

		sendResponse(response, output);
	}

	private Response buildSuccessResponse() {
		if (this.successResponse == null) {
			this.successResponse = new Response();
			this.successResponse = this.successResponse.success();
		}
		return this.successResponse;
	}

	private Response buildErrorResponse(String message) {
		Response response = new Response();
		response = response.error(message);
		return response;
	}

	private void sendResponse(Response response, DataOutputStream output) {
		sendString(response.toJson(), output);
	}

	private void sendString(String string, DataOutputStream output) {
		try {
			output.writeUTF(string);
			output.flush();
			logger.debug("SENT: " + string);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			logger.error(e.getClass().getName() + " " + e.getMessage());
		}
	}
	
	public void sendFile(File file, Socket socket) throws IOException {
		// define an array as the length of the file
		byte[] bytes  = new byte [(int)file.length()];
		FileInputStream fis = new FileInputStream(file);
		BufferedInputStream bis = new BufferedInputStream(fis);
		// read the file into the bytes array
		bis.read(bytes);
		// define an output stream
		OutputStream os = socket.getOutputStream();
		// write the bytes array onto the stream
		os.write(bytes);
		os.flush();
		// close bis and os - no longer needed
		bis.close();
		os.close();
	}
}
