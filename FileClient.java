import java.io.*;
import java.net.*;
import java.util.Scanner;

public class FileClient {
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        try {
            Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            Scanner scanner = new Scanner(System.in);

            while (true) {
                System.out.print("Enter command: ");
                String command = scanner.nextLine();

                // Send the command to the server
                writer.write(command + "\n");
                writer.flush();

                // Check if it's a file storage command
                if (command.startsWith("/store")) {
                    // Get the file name from the command
                    String[] parts = command.split(" ");
                    if (parts.length == 2) {
                        String filename = parts[1];

                        // Read the file content and send it to the server
                        try (BufferedReader fileReader = new BufferedReader(new FileReader(filename))) {
                            String line;
                            while ((line = fileReader.readLine()) != null) {
                                writer.write(line + System.lineSeparator());
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                }

                // Receive and display server response
                String response = reader.readLine();
                System.out.println(response);

                // Check for disconnection
                if (response.startsWith("Connection closed")) {
                    break;
                }
            }

            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
