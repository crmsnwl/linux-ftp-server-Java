import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;

import static java.net.SocketOptions.*;

public class Server 
{
    private int controlPort = 8889;
    private ServerSocket welcomeSocket;
    boolean running = true;

    public static void main(String[] args)
    {
        new Server();
    }

    public Server()
    {
        try
        {
            welcomeSocket = new ServerSocket(controlPort);
        }
        catch (IOException e)
        {
            System.out.println("Could not create socket");
            e.printStackTrace(System.out);
            System.exit(-1);
        }

        System.out.println("FTP launched on port " + controlPort + "\nLog in using Telnet");

        int noOfThreads = 0;

        while (running)
        {
            try
            {

                Socket client = welcomeSocket.accept();
                int dataPort = controlPort + noOfThreads + 1;

                // Create new worker thread for new connection
                CmdHandler w = new CmdHandler(client, dataPort);

                System.out.println("New conn");
                noOfThreads++;
                w.start();
            }
            catch (IOException e)
            {
                System.out.println("Exception encountered on accept");
                e.printStackTrace();
            }

        }

        try
        {
            welcomeSocket.close();
            System.out.println("Server was stopped");

        }
        catch (IOException e)
        {
            System.out.println("Problem stopping server");
            System.exit(-1);
        }
    }
}