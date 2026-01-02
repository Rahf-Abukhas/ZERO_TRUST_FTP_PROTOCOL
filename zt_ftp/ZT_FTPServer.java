package com.mycompany.zt_ftp;

/*  
AUTH
Session key + 3 strikes
LIST
UPLOAD 
DOWNLOAD 
DELETE (super)
Logging
 */
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class ZT_FTPServer extends Thread {

    private Socket socket;

    // Streams
    private BufferedReader in;
    private PrintWriter out;

    // Zero-Trust Session State 
    private boolean authenticated = false;
    private String username = null;
    private String role = null;      // normal | super
    private String sessionKey = null;
    private int invalidKeyCount = 0;
    private int authFailCount = 0;

    

    public ZT_FTPServer(Socket skt) {
        this.socket = skt; // accepted socket
    }

    @Override
    public void run() {

        try {
            //stream to communicate with client

          
            in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
            out = new PrintWriter(this.socket.getOutputStream(), true);//****
            log("NEW CONNECTION from " + socket.getInetAddress());

            String line;
            while (true) {
                
                line = in.readLine();
                // must be: AUTH <user> <password>
                line = line.trim();
                String[] parts = line.split("\\s+");
                String user = parts[1];
                String password = parts[2];

                //Auth Fail
                if (!authenticate(user, password)) {
                    authFailCount++;
                    out.println("ERROR AUTH_FAILED");
                    out.flush();
                    log("FAILED LOGIN for user=" + user + " attempt=" + authFailCount);

                   if (authFailCount >= 3) {
                        out.println("error2");
                        out.flush();
                        closeConnection();
                        log("CONNECTION TERMINATED after 3 failed AUTH attempts from "
                                + socket.getInetAddress());
                        return;
                    }//iner if
                    continue;
                }//if

                // Auth success
                authenticated = true;
                username = user;
                sessionKey =  UUID.randomUUID().toString();//UUID provides a highly unique and unpredictable session identifier, which improves security and prevents session guessing attacks
                authFailCount = 0;
                out.println("OK " + sessionKey);
                out.flush();
                log("LOGIN SUCCESS user=" + username + " role=" + role);
                break;
            }//while

            //END Session Client sends bye
            while (true) {
                line = in.readLine();
                // allow client to end
                if (line.equals("bye")) {
                    out.println("OK BYE");
                    log("Client ended session user=" + username);
                    break;
                }//if

                Command(line);
            }//while

            closeConnection();
        }//try
        catch (IOException e) {
            log("THREAD ERROR: " + e.getMessage());
            closeConnection();
        }//catch
    }//run

    private void Command(String line) {//loop بدنا 
        // Expected format:
        // <sessionKey> LIST
        // <sessionKey> DOWNLOAD <filename>
        // <sessionKey> UPLOAD <filename> <size>
        // <sessionKey> DELETE <filename>
        
        String[] t = line.split(" ");
        String key = t[0];
        String cmd = t[1];

        // 1 Session Key check --> تعديل
        if (!key.equals(sessionKey)) {
            invalidKeyCount++;
            out.println("ERROR INVALID_SESSION_KEY");
            log("INVALID SESSION KEY user=" + username + " count=" + invalidKeyCount);

            if (invalidKeyCount >= 3) {
                out.println("ERROR TOO_MANY_INVALID_KEYS CONNECTION_TERMINATED");
                log("CONNECTION TERMINATED due to 3 invalid keys user=" + username);
                closeConnection();
            }//if
            return;
        }//if
        // reset counter after correct key
        invalidKeyCount = 0;

        switch (cmd) {
            case "LIST":
                listFiles();
                break;

            case "DOWNLOAD":
                Download(t[2]);
                break;

            case "UPLOAD":
                Upload(t[2], t[3]);
                break;

            case "DELETE":
                Delete(t[2]);
                break;

            default:
                out.println("ERROR UNKNOWN_COMMAND");
        }//switch
    }// Command

    //List
    private void listFiles() {
        // super sees everything
        if (role.equals("super")) {
            File storage = new File("storage");   
            out.println("OK FILE LIST");
            File[] users = storage.listFiles();
            for (int i = 0; i < users.length; i++) {//users
                File[] files = users[i].listFiles();
                for (int j = 0; j < files.length; j++) {
                    out.println(users[i].getName() + "/" + files[j].getName());//files
                }//for files
            }//for users
            out.println("END");
            out.flush();
            return;
        }//if super
        
        //normal user 
        File directory = new File("storage/" + username);//dir.exists(),dir.isDirectory(),dir.list()  
        //File = representation of a file or directory path, not the content itself.
        if (!directory.exists()) {
            out.println("ERROR DIRECTORY_NOT_FOUND");
            out.flush();
            return;
        }//if
        String[] files = directory.list();
        out.println("OK FILE LIST");
        for (int i = 0; i < files.length; i++) {
            out.println(files[i]);
        }//for
        out.println("END");
        out.flush();
    }//listFiles

    // UPLOAD
    private void Upload(String filename, String sizeStr) {
        long size;
        try {
            size = Long.parseLong(sizeStr);//converts a numeric string into a long value 
        }//try
        catch (NumberFormatException e) {
            out.println("ERROR INVALID_SIZE");
            out.flush();
            return;
        }//catch

        try {
            File outFile = new File("storage/" + username + "/" + filename);//accesrole
            out.println("Server Ready");
            out.flush();

            BufferedInputStream uploadIN = new BufferedInputStream(socket.getInputStream());

            FileOutputStream uploadOUT = new FileOutputStream(outFile);

            byte[] buffer = new byte[4096];
            long remaining = size;
            int read;

            while (remaining > 0) {
                int toRead;
                if (remaining < buffer.length) {
                    toRead = (int) remaining;//(int) cast 
                } else {
                    toRead = buffer.length;
                }

                read = uploadIN.read(buffer, 0, toRead);//read-->لما ما تلاقي داتا، Java ترجع -1 تلقائيًا

                if (read == -1) {
                    break;
                }

                uploadOUT.write(buffer, 0, read);
                remaining -= read;
            }//while

            if (remaining == 0) {
                log("UPLOAD user=" + username + " file=" + filename + " size=" + size);
            } else {
                out.println("ERROR UPLOAD_INCOMPLETE");
                log("UPLOAD INCOMPLETE user=" + username + " file=" + filename);
            }

            uploadOUT.close();
            out.println("UPLOADED SUCCESSFULLY");
            out.println("END");
            out.flush();

        } //try
        catch (IOException e) {
            out.println("ERROR UPLOAD_FAILED"+e.getMessage());
            log("UPLOAD FAILED user=" + username + " file=" + filename + " err=" + e.getMessage());
        }//Catch
    }//Upload

    // DOWNLOAD
    private void Download(String filename) {
        
        File file = roleAccess(filename);
       
        if (!file.exists()) {
            out.println("ERROR FILE_NOT_FOUND");
            out.flush();
            return;
        }//if

        try {
            long size = file.length();//We use file.length() to obtain the actual file size in bytes, not the filename length.
            out.println("DOWNLOADING " + size);
            out.flush();

            // send bytes
            BufferedInputStream fileIN = new BufferedInputStream(new FileInputStream(file));
            BufferedOutputStream fileOUT = new BufferedOutputStream(socket.getOutputStream());

            byte[] buffer = new byte[4096];
            long remaining = size;
            int read;
            while (remaining > 0) {

                int toRead;
                if (remaining < buffer.length) {
                    toRead = (int) remaining;
                } else {
                    toRead = buffer.length;
                }
                read = fileIN.read(buffer, 0, toRead);

                if (read == -1) {
                    break;
                }

                fileOUT.write(buffer, 0, read);
                remaining -= read;
            }//while
            fileOUT.flush();
            fileIN.close();

            out.println("DOWNLOADED SUCCESSFULLY");
            out.flush();
            log("DOWNLOAD user=" + username + " file=" + file.getPath() + " size=" + size);
        } //try
        catch (IOException e) {
            out.println("ERROR DOWNLOAD_FAILED");
            out.flush();
            log("DOWNLOAD FAILED user=" + username + " file=" + filename + " error=" + e.getMessage());
        }//catch

    }//Download

    
    // DELETE
    private void Delete(String filename) {

        log("Try to DELETE COMMAND user=" + username + " target=" + filename);

        if (role.equals("normal")) {
            out.println("ERROR PERMISSION_DENIED");
            out.flush();
            return;
        }//if
        
        filename = filename.trim();
 
        File toDelete;//path
         if (filename.contains("/")) {
        toDelete = new File("storage/" + filename);//admin--> q.txt // user1/q.txt
    } else {
        toDelete = new File("storage/" + username + "/" + filename);
    }//else
         
    if (!toDelete.exists()) {
        out.println("ERROR FILE_NOT_FOUND");
        out.flush();
        return;
    }//if

    if (toDelete.delete()) {
        out.println("DELETED SUCCESSFULLY");
        out.flush();
        log("DELETE SUCCESS by super user=" + username +" file=" + toDelete.getPath());
    } else {
        out.println("ERROR DELETE_FAILED");
        out.flush();
        log("DELETE FAILED by super user=" + username +" file=" + toDelete.getPath());
    }//else
    }//Delete

  
    // Functions
    private boolean authenticate(String user, String password) {
        try {
            BufferedReader IN = new BufferedReader(new FileReader("users.txt"));
            String line;
            while ((line = IN.readLine()) != null) {
                String[] Cred = line.split(":");
                if (Cred.length == 3) {
                    if (Cred[0].equals(user) && Cred[1].equals(password)) {
                        this.role = Cred[2];
                        return true;
                    }//if
                }//if
            }//while
        } //try
        catch (IOException e) {
            log("AUTH FILE ERROR: " + e.getMessage());
        }
        return false;
    }

    private File roleAccess(String filename) {
        // normal
        if (role.equals("normal")) {
            return new File("storage/" + username + "/" + filename);
        }else{
        // super
        if (filename.contains("/")) {
                return new File("storage/" + filename);
            }else{
            return new File("storage/" + username + "/" + filename);
        }//else
        }//outelse
    }//roleAccess

    private void log(String msg) {
        FileWriter out = null;
        try {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());//new Date is an Object get current time , 
            out = new FileWriter("logs/server.log", true);// true -->dont override
            out.write("[" + time + "] " + msg + "\n");
            out.flush();
        }//try
        catch (IOException e) {
            System.out.println("LOG ERROR: " + e.getMessage());
        }//catch
        finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                System.out.println("LOG CLOSE ERROR: " + e.getMessage());
            }//catch
        }//finally
    }//log

    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } //try
        catch (IOException e) {
            System.out.println("Error in closeConnection : " + e.getMessage());
        }//catch
    }//closeConnection()

}//class

