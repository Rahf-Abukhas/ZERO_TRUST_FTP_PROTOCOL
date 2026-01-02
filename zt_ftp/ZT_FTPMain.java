
package com.mycompany.zt_ftp;


import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


public class ZT_FTPMain {

    public static void main(String[] args) {

        int port = 5000; 

        try {
            ServerSocket serverSocket = new ServerSocket(port);
        
                
            System.out.println("ZT-FTP Server running on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept(); 
                System.out.println("New client connected: " + clientSocket.getInetAddress());

          
                ZT_FTPServer thread = new ZT_FTPServer(clientSocket);
                thread.start();
            }//while

        }//try
        catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }//catch
    }//main
}//class

