import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

public class FileServer {
    private static final int SERVER_PORT = 12345;
    private static final String UPLOADS_FOLDER = "uploads/";

    private static HashMap<String, Socket> connectedClients = new HashMap<>();

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            System.out.println("Server started. Waiting for clients...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

            String clientHandle = null;

            // Read and process client commands
            while (true) {
                String command = reader.readLine();
                if (command == null) {
                    break;
                }

                String[] parts = command.split(" ");
                String keyword = parts[0].toLowerCase();

                switch (keyword) {
                    case "/join":
                        // Handle connection command
                        handleJoin(clientSocket, parts, writer);
                        break;

                    case "/leave":
                        // Handle disconnection command
                        handleLeave(clientSocket, writer);
                        break;

                    case "/register":
                        // Handle registration command
                        clientHandle = handleRegister(parts, writer);
                        break;

                    case "/store":
                        // Handle file storage command
                        handleStore(clientHandle, parts, reader, writer);
                        break;

                    case "/dir":
                        // Handle directory request command
                        handleDir(writer);
                        break;

                    case "/get":
                        // Handle file retrieval command
                        handleGet(clientHandle, parts, writer);
                        break;

                    case "/?":
                        // Handle command help request
                        handleCommandHelp(writer);
                        break;

                    default:
                        // Handle unknown command
                        writer.write("Error: Command not found.\n");
                        writer.flush();
                        break;
                }
            }

            // Clean up when client disconnects
            if (clientHandle != null) {
                connectedClients.remove(clientHandle);
                System.out.println("Client disconnected: " + clientHandle);
            }

            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleJoin(Socket clientSocket, String[] parts, BufferedWriter writer) throws IOException {
        if (parts.length == 3) {
            String serverIp = parts[1];
            int serverPort = Integer.parseInt(parts[2]);

            try {
                Socket serverSocket = new Socket(serverIp, serverPort);
                connectedClients.put(clientSocket.getInetAddress().toString(), serverSocket);
                writer.write("Connection to the File Exchange Server is successful!\n");
                writer.flush();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
            } catch (IOException e) {
                writer.write("Error: Connection to the Server has failed! Please check IP Address and Port Number.\n");
                writer.flush();
            }
        } else {
            writer.write("Error: Invalid /join command syntax.\n");
            writer.flush();
        }
    }

    private static void handleLeave(Socket clientSocket, BufferedWriter writer) throws IOException {
        if (connectedClients.containsKey(clientSocket.getInetAddress().toString())) {
            connectedClients.get(clientSocket.getInetAddress().toString()).close();
            connectedClients.remove(clientSocket.getInetAddress().toString());
            writer.write("Connection closed. Thank you!\n");
            writer.flush();
            System.out.println("Client disconnected: " + clientSocket.getInetAddress());
        } else {
            writer.write("Error: Disconnection failed. Please connect to the server first.\n");
            writer.flush();
        }
    }

    private static String handleRegister(String[] parts, BufferedWriter writer) throws IOException {
        if (parts.length == 2) {
            String clientHandle = parts[1];
            if (!connectedClients.containsValue(clientHandle)) {
                writer.write("Welcome " + clientHandle + "!\n");
                writer.flush();
                System.out.println("Client registered: " + clientHandle);
                return clientHandle;
            } else {
                writer.write("Error: Registration failed. Handle or alias already exists.\n");
                writer.flush();
            }
        } else {
            writer.write("Error: Invalid /register command syntax.\n");
            writer.flush();
        }
        return null;
    }

    private static void handleStore(String clientHandle, String[] parts, BufferedReader reader, BufferedWriter writer) throws IOException {
        if (isValidStoreCommand(clientHandle, parts)) {
            String filename = parts[1];
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

            // Create a subfolder for each client if not exists
            String clientFolder = UPLOADS_FOLDER + clientHandle + "/";
            File clientFolderFile = new File(clientFolder);
            if (!clientFolderFile.exists() && !clientFolderFile.mkdirs()) {
                writer.write("Error: Unable to create client folder.\n");
                writer.flush();
                return;
            }

            // Save the file in the client's subfolder
            String filePath = clientFolder + filename;
            try (BufferedWriter fileWriter = new BufferedWriter(new FileWriter(filePath))) {
                // Read and write the file content
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty() && !line.equals("EOF")) {
                    fileWriter.write(line);
                    fileWriter.newLine();  // Use newLine() to handle different line endings
                }
                writer.write(clientHandle + "<" + timestamp + ">: Uploaded " + filename + "\n");
                writer.flush();
            } catch (IOException e) {
                writer.write("Error: Failed to save the file.\n");
                writer.flush();
                e.printStackTrace(); // Log the exception for debugging purposes
            }
        } else {
            writer.write("Error: Invalid /store command syntax or not registered.\n");
            writer.flush();
        }
    }

    private static boolean isValidStoreCommand(String clientHandle, String[] parts) {
        return clientHandle != null && parts.length == 2;
    }

    private static void handleDir(BufferedWriter writer) throws IOException {
        // Implement directory listing logic here (list files in the uploads folder)
        File uploadsFolder = new File(UPLOADS_FOLDER);
        File[] files = uploadsFolder.listFiles();

        if (files != null && files.length > 0) {
            writer.write("Server Directory\n");
            for (File file : files) {
                writer.write(file.getName() + "\n");
            }
            writer.flush();
        } else {
            writer.write("Server Directory is empty.\n");
            writer.flush();
        }
    }

    private static void handleGet(String clientHandle, String[] parts, BufferedWriter writer) throws IOException {
        if (clientHandle != null && parts.length == 2) {
            String filename = parts[1];
            String filePath = UPLOADS_FOLDER + clientHandle + "/" + filename;

            // Check if the file exists
            File file = new File(filePath);
            if (file.exists()) {
                try (BufferedReader fileReader = new BufferedReader(new FileReader(filePath))) {
                    char[] buffer = new char[1024];
                    int bytesRead;
                    while ((bytesRead = fileReader.read(buffer)) != -1) {
                        writer.write(buffer, 0, bytesRead);
                        writer.flush();
                    }
                } catch (IOException e) {
                    writer.write("Error: Failed to read the file.\n");
                    writer.flush();
                    e.printStackTrace(); // Log the exception for debugging purposes
                }
            } else {
                writer.write("Error: File not found on the server.\n");
                writer.flush();
            }
        } else {
            writer.write("Error: Invalid /get command syntax or not registered.\n");
            writer.flush();
        }

        // Signal the end of the file
        writer.write("\nEOF\n");
        writer.flush();

        // Wait for a new command
        writer.write("Enter command: ");
        writer.flush();
    }


    private static void handleCommandHelp(BufferedWriter writer) throws IOException {
        // Provide a list of available commands
        writer.write("Available Commands:\n");
        writer.write("/join <server_ip_add> <port>\n");
        writer.write("/leave\n");
        writer.write("/register <handle>\n");
        writer.write("/store <filename>\n");
        writer.write("/dir\n");
        writer.write("/get <filename>\n");
        writer.write("/?\n");
        writer.flush();
    }
}
