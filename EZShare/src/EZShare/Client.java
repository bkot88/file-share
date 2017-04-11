package EZShare;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * The class contains functionality to parse command-line arguments according to
 * the assignment specifications. Additionally, it can also communicate with an
 * EZShare.Server that is listening for EZShare.Client, according to the
 * protocol defined in the assignment specifications.
 * 
 * Aaron's server: sunrise.cis.unimelb.edu.au:3780
 * 
 * @author Koteski, B
 *
 */
public class Client {

	private ClientArgs clientArgs;
	public static final int TIME_OUT_LIMIT = 5000;
	private static Logger logger;
	private static Socket socket;
	
	//private static HashMap<String, Runnable> commandMap = new HashMap<String, Runnable>();


	public static void main(String[] args) {
		/*
		commandMap.put("PUBLISH", () -> processQuery());
		commandMap.put("REMOVE", () -> processQuery());
		commandMap.put("SHARE", () -> processQuery());
		commandMap.put("QUERY", () -> processQuery());
		commandMap.put("FETCH", () -> processQuery());
		commandMap.put("EXCHANGE", () -> processQuery());
		*/


		
		
		// TODO: Remove if -- solely for testing purposes
		if (args.length == 0) {
			
//			 String[] args2 = { "-" + Constants.shareOption, "-" +
//			 Constants.uriOption,
//			 "file:///Users/alexandrafritzen/ezshare.jar", "-" +
//			 Constants.nameOption, "EZShare JAR",
//			 "-" + Constants.descriptionOption, "The jar file for EZShare.",
//			 "-" + Constants.tagsOption, "jar", "-" + Constants.channelOption,
//			 "myprivatechannel",
//			 "-" + Constants.ownerOption, "aaron010", "-" +
//			 Constants.secretOption, "1234",
//			 "-" + Constants.debugOption };

			// PUBLISH
			// String[] args2 = { "-" + Constants.publishOption, "-" +
			// Constants.nameOption, "Unimelb website",
			// "-" + Constants.descriptionOption, "The main page for the
			// University of Melbourne",
			// "-" + Constants.uriOption, "http://www.unimelb.edu.au", "-" +
			// Constants.tagsOption, "web,html",
			// "-" + Constants.ownerOption, "Alex", "-" + Constants.debugOption,
			// "-" + Constants.debugOption
			// };

			// REMOVE
			// String[] args2 = { "-" + Constants.removeOption, "-" +
			// Constants.uriOption, "http://www.unimelb.edu.au", "-" +
			// Constants.debugOption };
			
			// QUERY
//			String[] args2 = { "-" + Constants.queryOption, "-" + Constants.channelOption, "myprivatechannel",
//					"-" + Constants.descriptionOption, "jar",
//					"-" + Constants.debugOption };

			// FETCH
			//String[] args2 = { "-" + Constants.fetchOption, "-" + Constants.channelOption, "myprivatechannel", "-" + Constants.uriOption, "file:///Users/alexandrafritzen/ezshare.jar"};
			//args = args2;
			//for (int i=0;i<(args).length;i++) {
			//	System.out.println(args[i]);
			//}
		}

		// String[] args2 = { "-exchange", "-servers", "host1:sadf"};
		Client client = new Client(args);
		
		// print current classpath (for debugging)
		//System.out.println(System.getProperty("java.class.path"));

		// Configure logger
		if (client.clientArgs.hasOption(Constants.debugOption)) {

			//System.setProperty("log4j.configurationFile", "logging-config-debug.xml");
			System.setProperty("log4j.configurationFile", "../logging-config-debug.xml");
		} else {
			//System.setProperty("log4j.configurationFile", "logging-config-default.xml");
			System.setProperty("log4j.configurationFile", "../logging-config-default.xml");
		}
		//Logger logger = LogManager.getRootLogger();
		logger = LogManager.getRootLogger();

		logger.debug("Debugger enabled");
				
		Command command = client.parseCommand();
		ServerInfo serverInfo = client.parseServerInfo();

		logger.debug("Publishing to " + serverInfo);

		
		try {
			logger.info("Creating socket at port "+serverInfo.getPort());
			//Socket socket = new Socket(serverInfo.getHostname(), serverInfo.getPort());
			socket = new Socket(serverInfo.getHostname(), serverInfo.getPort());
			socket.setSoTimeout(TIME_OUT_LIMIT); // wait for 5 seconds
			DataInputStream inFromServer = new DataInputStream(socket.getInputStream());
			DataOutputStream outToServer = new DataOutputStream(socket.getOutputStream());

			outToServer.writeUTF(command.toJson());
			logger.info("Sending "+command.command+" command... ");
			outToServer.flush();
			logger.info(command.command+" command sent");

			
			// Doesn't work with the logger due to multiple lines being
			// generated
			// logger.info(command.toJsonPretty());
			logger.debug("SENT: " + command.toJson());

			// TODO processing the responses in a better way
			// TODO: Implement timeout
			boolean run = false;
			do {
				String fromServer = inFromServer.readUTF();
				//logger.info("logga");
				if (fromServer.contains("success") && command.command.equals("QUERY")
						|| command.command.equals("FETCH"))
					run = true;
				
				if (fromServer.contains("resultSize"))
					run = false;
				logger.debug("RECEIVED: " + fromServer);
				//logger.info("Response received from Server. Processing.. ");
				processServerResponse(fromServer);
				//logger.info("Server response successfully processed ");
			} while (run);

			socket.close();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			logger.error(e.getClass().getName() + " " + e.getMessage());
		} catch (SocketTimeoutException e) {
			logger.error(e.getClass().getName() + " " + e.getMessage());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			logger.error(e.getClass().getName() + " " + e.getMessage());
		}
		
	}

	private static void processQuery(String test) {
		// TODO Auto-generated method stub
		logger.info("THIS IS THE PROCESS QUERY METHOD STUB");
	}

	/**
	 * Constructor for Client
	 * 
	 * @param args
	 *            String[] command line arguments
	 */
	public Client(String[] args) {
		clientArgs = new ClientArgs(args);
	}

	/**
	 * 
	 * @return
	 */
	public Command parseCommand() {
		return new Command(clientArgs);
	}

	/**
	 * 
	 * @return
	 */
	public ServerInfo parseServerInfo() {
		return new ServerInfo(clientArgs.getSafeHost(), clientArgs.getSafePort());
	}

	/**
	 * 
	 */
	public static void processServerResponse(String response) {
		try 
		{
			// TODO: implement proper logic
			
			Command command = (new Command()).fromJson(response);
			DataOutputStream output = new DataOutputStream(socket.getOutputStream());

			if (!parseResponseForErrors(command, output)) {
				switch (command.command) {
					case Constants.queryCommand:
						processQueryResponse(command, output);
						break;
					case Constants.fetchCommand:
						processFetchResponse(command, output);
						break;
					//case Constants.exchangeCommand:
					//	processExchangeResponse(command, output);
					//	break;
					case Constants.publishCommand:
						processPublishResponse(command, output);
						break;
					case Constants.shareCommand:
						processShareResponse(command, output);
						break;
					case Constants.removeCommand:
						processRemoveResponse(command, output);
						break;
					case Constants.invalidCommand:
						processInvalidResponse(command, output);
						break;
					default:
						processMissingOrInvalidResponse(command, output);
						break;
				}
			}
			
		}
		catch (Exception e) {
			logger.error("Error processing Server response");
		}
		// TODO ?
	
}

	private static void processMissingOrInvalidResponse(Command command, DataOutputStream output) {
		// TODO Auto-generated method stub
		
	}

	private static void processInvalidResponse(Command command, DataOutputStream output) {
		// TODO Auto-generated method stub
		
	}

	private static void processRemoveResponse(Command command, DataOutputStream output) {
		// TODO Auto-generated method stub
		
	}

	private static void processShareResponse(Command command, DataOutputStream output) {
		// TODO Auto-generated method stub
		
	}

	private static void processPublishResponse(Command command, DataOutputStream output) {
		// TODO Auto-generated method stub
		
	}

	private static void processExchangeResponse(Command command, DataOutputStream output) {
		// TODO Auto-generated method stub
		
	}

	private static void processFetchResponse(Command command, DataOutputStream output) {
		// TODO Auto-generated method stub
		
	}

	private static void processQueryResponse(Command command, DataOutputStream output) {
		// TODO Auto-generated method stub
		
	}
	private static boolean parseResponseForErrors(Command command, DataOutputStream output) {
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
				//sendResponse(buildErrorResponse("String values cannot be *"), output);
				errorFound = true;
				break;
			} else if (value != value.trim()) {
				//sendResponse(buildErrorResponse("String values cannot start or end with whitespace(s)"), output);
				errorFound = true;
				break;
			} else if (value.contains("\0")) {
				//sendResponse(buildErrorResponse("String values cannot contain \0"), output);
				errorFound = true;
				break;
			}
		}

		return errorFound;
	}
	
}
