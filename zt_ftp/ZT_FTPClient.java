package com.mycompany.zt_ftp;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ZT_FTPClient {

    public static void main(String[] args) {

        final int port = 5000;
        Socket socket = null;
        BufferedReader in = null;
        PrintWriter out = null;

        try {
            socket = new Socket("localhost", port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream());

            Scanner keyboard = new Scanner(System.in);
            //Authentication
            String sessionKey = null;

            while (true) {
                System.out.print("Username: ");
                String user = keyboard.nextLine();

                System.out.print("Password: ");
                String password = keyboard.nextLine();

                out.println("AUTH " + user + " " + password);
                out.flush();

                String response = in.readLine().trim();
                // 3 failed attempts
                if (response.equals("error2")) {
                    System.out.println("Too many failed login attempts. Connection closed by server.");
                    return;
                }//if
                // success
                if (response.startsWith("OK ")) {//  !response.equals("OK ")-->error
                    String[] parts = response.split("\\s+");
                    sessionKey = parts[1];
                    System.out.println("Logged in successfully. SessionKey = " + sessionKey);
                    break;
                }//if
                else {
                    System.out.println("Login failed. Try again.\n");
                }//else
            }//while

            //MENU
            while (true) {//forever
                System.out.println("1) LIST");
                System.out.println("2) UPLOAD");
                System.out.println("3) DOWNLOAD");
                System.out.println("4) DELETE (super only)");
                System.out.println("5) bye");
                System.out.print("Choose a number: ");

                String choice = keyboard.nextLine();
                //list
                if (choice.equals("1")) {
                   out.println(sessionKey + " LIST");
                   // out.println( "fackSession LIST");
                    out.flush();

                    String line = checkSession(in.readLine());
                    if (line == null) {
                        break;
                    }
                    if (line.equals("ERROR")) {
                        continue;
                    }

                    while (line != null) {
                        if (line.equals("END")) {
                            break;
                        } else {
                            System.out.println(" - " + line);
                        }
                        line = in.readLine();
                    }

                }//outer if
                //UPLOAD
                else if (choice.equals("2")) {
                    System.out.print("Enter file Name: ");
                    String fileName = keyboard.nextLine().trim();

                    File file = new File(fileName);
                    if (!file.exists()) {
                        System.out.println("File not found.");
                        continue;
                    }//if

                    long size = file.length();
                    String filename = file.getName();

                    out.println(sessionKey + " UPLOAD " + filename + " " + size);
                    out.flush();

                    String ready = in.readLine();

                    ready = checkSession(ready);
                    if (ready == null) {
                        break;
                    }
                    if (ready.equals("ERROR")) {
                        continue;
                    }

                    System.out.println(ready);

                    BufferedInputStream fileIN = new BufferedInputStream(new FileInputStream(fileName));
                    BufferedOutputStream fileOUT = new BufferedOutputStream(socket.getOutputStream());

                    byte[] buffer = new byte[4096];
                    long remaining = size;
                    int read;

                    while (remaining > 0) {
                        int toRead = buffer.length;
                        if (remaining < buffer.length) {
                            toRead = (int) remaining;
                        }

                        read = fileIN.read(buffer, 0, toRead);
                        if (read == -1) {
                            break;
                        }

                        fileOUT.write(buffer, 0, read);
                        remaining -= read;
                    }

                    fileOUT.flush();
                    fileIN.close();

                    String line;
                    while (true) {
                        line = in.readLine();
                        if (line.equals("END")) {
                            break;
                        }
                        System.out.println(line);
                    }//while
                }//else if upload
                // DOWNLOAD 
                else if (choice.equals("3")) {
                    System.out.print("Enter filename to download: ");
                    String filename = keyboard.nextLine();

                    out.println(sessionKey + " DOWNLOAD " + filename);
                    out.flush();

                    String header = checkSession(in.readLine());
                    if (header == null) {
                        break;
                    }
                    if (header.startsWith("ERROR")) {
                        continue;
                    }
                    System.out.println(header);

                    String[] arr = header.split(" ");
                    long size = Long.parseLong(arr[1]);

                    FileOutputStream fileOUT = new FileOutputStream(filename);
                    BufferedInputStream fileIN = new BufferedInputStream(socket.getInputStream());

                    byte[] buffer = new byte[4096];
                    long remaining = size;
                    int read;

                    while (remaining > 0) {
                        int toRead = buffer.length;
                        if (remaining < buffer.length) {
                            toRead = (int) remaining;
                        }//if

                        read = fileIN.read(buffer, 0, toRead);
                        if (read == -1) {
                            break;
                        }//if

                        fileOUT.write(buffer, 0, read);
                        remaining -= read;
                    }//while

                    fileOUT.close();
                    System.out.println(in.readLine());
                }//else if download 
                // DELETE 
                else if (choice.equals("4")) {
                    System.out.print("Enter filename to delete: ");
                    String filename = keyboard.nextLine();

                    out.println(sessionKey + " DELETE " + filename);
                    out.flush();

                    String line = checkSession(in.readLine());
                    if (line == null) {
                        break;
                    }
                    if (!line.equals("ERROR")) {
                        System.out.println(line);
                    }
                }//else if delete
                //bye
                else if (choice.equals("5")) {
                    out.println("bye");
                    out.flush();
                    System.out.println(in.readLine());
                    break;
                }//else if bye
                else {
                    System.out.println("Invalid choice.");
                }//else
            }//outer while    
        }//try
        catch (Exception e) {
            System.out.println("Client Error: " + e.getMessage());
        }//catch
        finally {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }//if
            } //try
            catch (IOException e) {
                System.out.println("Close error: " + e.getMessage());
            }//catch
        }//finally
    }//main

    private static String checkSession(String line) {

        if (line == null) {
            System.out.println("Connection closed by server.");
            return null;
        }//if
        if (line.startsWith("ERROR TOO_MANY_INVALID_KEYS")) {
            System.out.println(line);
            return null; 
        }//if

        if (line.startsWith("ERROR INVALID_SESSION_KEY")) {
            System.out.println(line);
            return "ERROR"; 
        }//if
        if (line.startsWith("ERROR")) {
        System.out.println(line);
        return line;
    }//if
        return line; 
    }//checkSession

}//ZT_FTPClient
