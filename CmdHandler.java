import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.zip.*;


public class CmdHandler extends Thread
{

    private boolean debugMode = true;

    private enum transferType
    {
        ASCII, BINARY
    }

    private enum userStatus
    {
        NOTLOGGEDIN, ENTEREDUSERNAME, LOGGEDIN
    }


    private String root;
    private String currDirectory;
    private String fileSeparator = "/";
    private Socket controlSocket;
    private PrintWriter controlOutWriter;
    private BufferedReader controlIn;
    private ServerSocket dataSocket;
    private Socket dataConnection;
    private PrintWriter dataOutWriter;
    private int dataPort;
    private transferType transferMode = transferType.ASCII;
    private userStatus currentUserStatus = userStatus.NOTLOGGEDIN;
    private String validUser = "user";
    private String validPassword = "pass";

    private boolean quitCommandLoop = false;

    public CmdHandler(Socket client, int dataPort)
    {
        super();
        this.controlSocket = client;
        this.dataPort = dataPort;
        this.currDirectory = System.getProperty("user.home") + "/Desktop/SRV/Files";
	
        this.root = System.getProperty("user.dir");
    }

    public void run()
    {
        debugOutput("Current working directory " + this.currDirectory);
        try
        {
            controlIn = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
            controlOutWriter = new PrintWriter(controlSocket.getOutputStream(), true);
            sendMsgToClient("Enter user command");

            while (!quitCommandLoop)
            {
                executeCommand(controlIn.readLine());
            }

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            try 
            {
                controlIn.close();
                controlOutWriter.close();
                controlSocket.close();
                debugOutput("Sockets closed");
            }
            catch (IOException e)
            {
                e.printStackTrace();
                debugOutput("Error closing socket");
            }
        }
    }

    private void executeCommand(String c)
    {
        int index = c.indexOf(' ');
        String command = ((index == -1) ? c.toUpperCase() : (c.substring(0, index)).toUpperCase());
        String args = ((index == -1) ? null : c.substring(index + 1));

        debugOutput("Command: " + command + " Args: " + args);

        switch (command) 
        {
            case "USER":
                handleUser(args);
                break;

            case "PASS":
                handlePass(args);
                break;

            case "CWD":
                handleCwd(args);
                break;

            case "LIST":
                handleNlst(args);
                break;

            case "NLST":
                handleNlst(args);
                break;

            case "PWD":
            case "XPWD":
                handlePwd();
                break;

            case "QUIT":
                handleQuit();
                break;

            case "PASV":
                handlePasv();
                break;

            case "EPSV":
                handleEpsv();
                break;

            case "SYST":
                handleSyst();
                break;

            case "FEAT":
                handleFeat();
                break;

            case "PORT":
                handlePort(args);
                break;

            case "EPRT":
                handleEPort(args);
                break;

            case "RETR":
                handleRetr(args);
                break;

            case "MKD":
            case "XMKD":
                handleMkd(args);
                break;

            case "RMD":
            case "XRMD":
                handleRmd(args);
                break;

            case "TYPE":
                handleType(args);
                break;

            case "STOR":
                handleStor(args);
                break;

            case "MPUT":
                handleMput(args);
                break;

            default:
                sendMsgToClient("501 Unknown command");
                break;
        }
    }

    private void sendMsgToClient(String msg) 
    {
        controlOutWriter.println(msg);
    }

    private void sendDataMsgToClient(String msg) 
    {
        if (dataConnection == null || dataConnection.isClosed()) 
        {
            sendMsgToClient("425 No data connection was established");
            debugOutput("Cannot send message, because no data connection is established");
        } 
        else 
        {
            dataOutWriter.print(msg + '\r' + '\n');
        }
    }

    private void openDataConnectionPassive(int port) 
    {
        try 
        {
            dataSocket = new ServerSocket(port);
            dataConnection = dataSocket.accept();
            dataOutWriter = new PrintWriter(dataConnection.getOutputStream(), true);
            debugOutput("Data connection - Passive Mode - established");

        } 
        catch (IOException e) 
        {
            debugOutput("Could not create data connection.");
            e.printStackTrace();
        }
    }

    private void openDataConnectionActive(String ipAddress, int port) 
    {
        try 
        {
            dataConnection = new Socket(ipAddress, port);
            dataOutWriter = new PrintWriter(dataConnection.getOutputStream(), true);
            debugOutput("Data connection - Active Mode - established");
        } 
        catch (IOException e) 
        {
            debugOutput("Could not connect to client data socket");
            e.printStackTrace();
        }
    }

    private void closeDataConnection() 
    {
        try 
        {
            dataOutWriter.close();
            dataConnection.close();
            if (dataSocket != null) 
            {
                dataSocket.close();
            }

            debugOutput("Data connection was closed");
        } 
        catch (IOException e) 
        {
            debugOutput("Could not close data connection");
            e.printStackTrace();
        }
        dataOutWriter = null;
        dataConnection = null;
        dataSocket = null;
    }

    private void handleUser(String username) 
    {
        if (username.toLowerCase().equals(validUser)) 
        {
            sendMsgToClient("Enter password command");
            currentUserStatus = userStatus.ENTEREDUSERNAME;
        } 
        else if (currentUserStatus == userStatus.LOGGEDIN)
        {
            sendMsgToClient("User already logged in");
        } 
        else 
        {
            sendMsgToClient("Not logged in");
        }
    }

    private void handlePass(String password) 
    {
        if (currentUserStatus == userStatus.ENTEREDUSERNAME && password.equals(validPassword)) 
        {
            currentUserStatus = userStatus.LOGGEDIN;
            sendMsgToClient("Welcome to Big boi FTP");
            sendMsgToClient("User logged in successfully");
            handlePort("127,0,0,1,32,696");
        }
        else if (currentUserStatus == userStatus.LOGGEDIN) 
        {
            sendMsgToClient("User already logged in");
        }
        else 
        {
            sendMsgToClient("Not logged in");
        }
    }

    private void handleCwd(String args) 
    {
        String filename = currDirectory;

        if (args.equals("..")) 
        {
            int ind = filename.lastIndexOf(fileSeparator);
            if (ind > 0) 
            {
                filename = filename.substring(0, ind);
            }
        }

        else if ((args != null) && (!args.equals("."))) 
        {
            filename = filename + fileSeparator + args;
        }

        File f = new File(filename);

        if (f.exists() && f.isDirectory() && (filename.length() >= root.length())) 
        {
            currDirectory = filename;
            sendMsgToClient("250 The current directory has been changed to " + currDirectory);
        } 
        else 
        {
            sendMsgToClient("550 Requested action not taken. File unavailable.");
        }
    }

    private void handleNlst(String args) 
    {
        if (dataConnection == null || dataConnection.isClosed()) 
        {
            sendMsgToClient("425 No data connection was established");
        } 
        else 
        {
            String[] dirContent = nlstHelper(args);

            if (dirContent == null) 
            {
                sendMsgToClient("550 File does not exist.");
            } 
            else 
            {
                sendMsgToClient("125 Opening ASCII mode data connection for file list.");

                for (int i = 0; i < dirContent.length; i++) 
                {
                    sendDataMsgToClient(dirContent[i]);
                }

                sendMsgToClient("226 Transfer complete.");
                closeDataConnection();
            }
        }
    }

    private String[] nlstHelper(String args) 
    {
        String filename = currDirectory;
        if (args != null) 
        {
            filename = filename + fileSeparator + args;
        }

        File f = new File(filename);

        if (f.exists() && f.isDirectory()) 
        {
            return f.list();
        } 
        else if (f.exists() && f.isFile()) 
        {
            String[] allFiles = new String[1];
            allFiles[0] = f.getName();
            return allFiles;
        } 
        else 
        {
            return null;
        }
    }

    private void handlePort(String args) 
    {
        String[] stringSplit = args.split(",");
        String hostName = stringSplit[0] + "." + stringSplit[1] + "." + stringSplit[2] + "." + stringSplit[3];

        int p = Integer.parseInt(stringSplit[4]) * 256 + Integer.parseInt(stringSplit[5]);

        openDataConnectionActive(hostName, p);
    }

    private void handleEPort(String args) 
    {
        final String IPV4 = "1";
        final String IPV6 = "2";

        String[] splitArgs = args.split("\\|");
        String ipVersion = splitArgs[1];
        String ipAddress = splitArgs[2];

        if (!IPV4.equals(ipVersion) || !IPV6.equals(ipVersion)) 
        {
            throw new IllegalArgumentException("Unsupported IP version");
        }

        int port = Integer.parseInt(splitArgs[3]);

        openDataConnectionActive(ipAddress, port);
        sendMsgToClient("Command OK");

    }

    private void handlePwd() 
    {
        sendMsgToClient("257 \"" + currDirectory + "\"");
    }

    private void handlePasv() 
    {
        String myIp = "127.0.0.1";
        String myIpSplit[] = myIp.split("\\.");

        int p1 = dataPort / 256;
        int p2 = dataPort % 256;

        sendMsgToClient("227 Entering Passive Mode (" + myIpSplit[0] + "," + myIpSplit[1] + "," + myIpSplit[2] + ","
                + myIpSplit[3] + "," + p1 + "," + p2 + ")");

        openDataConnectionPassive(dataPort);
    }

    private void handleEpsv() 
    {
        sendMsgToClient("229 Entering Extended Passive Mode (|||" + dataPort + "|)");
        openDataConnectionPassive(dataPort);
    }

    private void handleQuit() 
    {
        sendMsgToClient("221 Closing connection");
        quitCommandLoop = true;
    }

    private void handleSyst() 
    {
        sendMsgToClient("215 FTP Server");
    }

    private void handleFeat() 
    {
        sendMsgToClient("211-Extensions supported:");
        sendMsgToClient("211 END");
    }

    private void handleMkd(String args) 
    {
        if (args != null && args.matches("^[a-zA-Z0-9]+$")) 
        {
            File dir = new File(currDirectory + fileSeparator + args);

            if (!dir.mkdir()) 
            {
                sendMsgToClient("550 Failed to create new directory");
                debugOutput("Failed to create new directory");
            } 
            else 
            {
                sendMsgToClient("250 Directory successfully created");
            }
        } 
        else 
        {
            sendMsgToClient("550 Invalid name");
        }
    }

    /**
     * @param dir
     */
    private void handleRmd(String dir) 
    {
        String filename = currDirectory;

        if (dir != null && dir.matches("^[a-zA-Z0-9]+$")) 
        {
            filename = filename + fileSeparator + dir;

            File d = new File(filename);

            if (d.exists() && d.isDirectory()) 
            {
                d.delete();

                sendMsgToClient("250 Directory was successfully removed");
            } 
            else 
            {
                sendMsgToClient("550 Requested action not taken. File unavailable.");
            }
        } 
        else 
        {
            sendMsgToClient("550 Invalid file name.");
        }

    }

    /**
     * @param mode 
     */
    private void handleType(String mode) 
    {
        if (mode.toUpperCase().equals("A")) 
        {
            transferMode = transferType.ASCII;
            sendMsgToClient("200 OK");
        } 
        else if (mode.toUpperCase().equals("I")) 
        {
            transferMode = transferType.BINARY;
            sendMsgToClient("200 OK");
        } 
        else
            sendMsgToClient("504 Not OK");
        ;

    }

    /**
     * @param file 
     */
    private void handleRetr(String file) 
    {
        File f = new File(currDirectory + fileSeparator + file);

        if (!f.exists()) 
        {
            sendMsgToClient("550 File does not exist");
        }

        else 
        {
            //bin

            if (transferMode == transferType.BINARY) 
            {
                BufferedOutputStream fout = null;
                BufferedInputStream fin = null;

                sendMsgToClient("150 Opening binary mode data connection for requested file " + f.getName());

                try 
                {
                    fout = new BufferedOutputStream(dataConnection.getOutputStream());
                    fin = new BufferedInputStream(new FileInputStream(f));
                } 
                catch (Exception e) 
                {
                    debugOutput("Could not create file streams");
                }

                debugOutput("Starting file transmission of " + f.getName());

                byte[] buf = new byte[1024];
                int l = 0;
                try 
                {
                    while ((l = fin.read(buf, 0, 1024)) != -1) 
                    {
                        fout.write(buf, 0, l);
                    }
                } 
                catch (IOException e) 
                {
                    debugOutput("Could not read from or write to file streams");
                    e.printStackTrace();
                }

                try 
                {
                    fin.close();
                    fout.close();
                } catch (IOException e) 
                {
                    debugOutput("Could not close file streams");
                    e.printStackTrace();
                }

                debugOutput("Completed file transmission of " + f.getName());

                sendMsgToClient("226 File transfer successful. Closing data connection.");
            }

            // ASCII
            else 
            {
                sendMsgToClient("150 Opening ASCII mode data connection for requested file " + f.getName());

                BufferedReader rin = null;
                PrintWriter rout = null;

                try 
                {
                    rin = new BufferedReader(new FileReader(f));
                    rout = new PrintWriter(dataConnection.getOutputStream(), true);

                } catch (IOException e) 
                {
                    debugOutput("Could not create file streams");
                }

                String s;

                try 
                {
                    while ((s = rin.readLine()) != null) 
                    {
                        rout.println(s);
                    }
                } 
                catch (IOException e) 
                {
                    debugOutput("Could not read from or write to file streams");
                    e.printStackTrace();
                }

                try 
                {
                    rout.close();
                    rin.close();
                } 
                catch (IOException e) 
                {
                    debugOutput("Could not close file streams");
                    e.printStackTrace();
                }
                sendMsgToClient("226 File transfer successful. Closing data connection.");
            }
        }
        closeDataConnection();
    }


    int zips = 0;
    /**
     * @param file 
     */
    private void handleStor(String file) 
    {
        if (file == null) 
        {
            sendMsgToClient("501 No filename given");
        } 
        else 
        {
            File f = new File(currDirectory + fileSeparator + file);

            if (f.exists()) 
            {
                sendMsgToClient("550 File already exists");
            }

            else 
            {
                // bin
                if (transferMode == transferType.BINARY) 
                {
                    BufferedOutputStream fout = null;
                    BufferedInputStream fin = null;

                    sendMsgToClient("150 Opening binary mode data connection for requested file " + f.getName());

                    try 
                    {
                        fout = new BufferedOutputStream(new FileOutputStream(f));
                        fin = new BufferedInputStream(dataConnection.getInputStream());
                    } 
                    catch (Exception e) 
                    {
                        debugOutput("Could not create file streams");
                    }

                    debugOutput("Start receiving file " + f.getName());

                    byte[] buf = new byte[1024];
                    int l = 0;
                    try 
                    {
                        while ((l = fin.read(buf, 0, 1024)) != -1) 
                        {
                            fout.write(buf, 0, l);
                        }
                    } 
                    catch (IOException e) 
                    {
                        debugOutput("Could not read from or write to file streams");
                        e.printStackTrace();
                    }

                    try 
                    {
                        fin.close();
                        fout.close();
                    } 
                    catch (IOException e) 
                    {
                        debugOutput("Could not close file streams");
                        e.printStackTrace();
                    }

                    debugOutput("Completed receiving file " + f.getName());

                    sendMsgToClient("226 File transfer successful. Closing data connection.");
                }

                // ASCII
                else 
                {
                    sendMsgToClient("150 Opening ASCII mode data connection for requested file " + f.getName());

                    BufferedReader rin = null;
                    PrintWriter rout = null;

                    try 
                    {
                        rin = new BufferedReader(new InputStreamReader(dataConnection.getInputStream()));
                        rout = new PrintWriter(new FileOutputStream(f), true);

                    } 
                    catch (IOException e) 
                    {
                        debugOutput("Could not create file streams");
                    }

                    String s;

                    try 
                    {
                        while ((s = rin.readLine()) != null) 
                        {
                            rout.println(s);
                        }
                    } 
                    catch (IOException e) 
                    {
                        debugOutput("Could not read from or write to file streams");
                        e.printStackTrace();
                    }

                    try 
                    {
                        rout.close();
                        rin.close();
                    } 
                    catch (IOException e) 
                    {
                        debugOutput("Could not close file streams");
                        e.printStackTrace();
                    }
                    sendMsgToClient("226 File transfer successful. Closing data connection.");
                }
            }
            closeDataConnection();
        }
    }

    private void handleMput(String names)
    {
        List<String> fileNames = new ArrayList<String>();
        String temp;
        int split = 0;

        for (int i = 0; i < names.length(); ++i)
        {
            if (names.charAt(i) == ',')
            {
                temp = names.substring(split, i);
                fileNames.add(temp);
                split = i + 1;
                temp = null;
            }
            if (i == names.length() - 1)
            {
                temp = names.substring(split, i+1);
                fileNames.add(temp);
                temp = null;
            }
        }

        File f = new File(currDirectory + fileSeparator + "ZIP" + String.valueOf(zips) + ".zip");

        if (f.exists())
        {
            sendMsgToClient("550 File already exists");
        }
        else
        {
            BufferedOutputStream fout = null;
            BufferedInputStream fin = null;

            try
            {
                fout = new BufferedOutputStream(new FileOutputStream(f));
                
                ZipOutputStream zipOut = new ZipOutputStream(fout);

                for (String fileName : fileNames)
                {
                    File file = new File("/home/xa/Desktop/CL/" + fileSeparator + fileName);

                    fin = new BufferedInputStream(new FileInputStream(file));
                    ZipEntry zipEntry = new ZipEntry(file.getName());
                    zipOut.putNextEntry(zipEntry);

                    System.out.println("Uploading " + file.getName());

                    byte[] bytes = new byte[1024];
                    int length;
                    while ((length = fin.read(bytes)) >= 0)
                    {
                        zipOut.write(bytes, 0, length);
                    }
                    zipOut.flush();
                    fin.close();
                }
                zipOut.close();
                fout.close();
            }
            catch (Exception e)
            {
                debugOutput("Could not create file streams");
                System.out.println(e.getMessage());
                e.printStackTrace();

            }
            ++zips;
        }
    }

    /**
     * @param msg
     */
    private void debugOutput(String msg) 
    {
        if (debugMode) 
        {
            System.out.println("Thread " + this.getId() + ": " + msg);
        }
    }
}
