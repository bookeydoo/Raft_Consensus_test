
package org.example;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.lang.Thread;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Random;


//basic server that  does summation to 2 values and return it

public class Main extends Thread{
    public static Random random=new Random();

    private static ServerSocket srvr=null;

    public static int port=65530;// default port

    private static final List<Socket>OpenSockets=new ArrayList<>();

    public static void start_server(int Port) {


        try {
            srvr = new ServerSocket(Port);
            System.out.print("started the server on port "+port);

            while(true){
                Socket clientsock=srvr.accept();
                System.out.print("connection occured");
                new Thread(() -> Handle_client(clientsock)).start();
            }


        } catch (IOException e) {
            System.out.print("failed to create the server "+e);
        }

    }

    private static void connectToPeers(int Port){


        int Timeout=random.nextInt(50,200);
        boolean connected=false;
        InetSocketAddress address=new InetSocketAddress(Port);
        Socket socket=new Socket();
        while (!connected) {


            try{
                System.out.print("attempting to connect  to node :"+Port+"timeout:"+Timeout+"ms \n");
                socket.connect(address,Timeout);

                connected=true;
                System.out.print("we are connected");
                OpenSockets.add(socket);

            }   catch(SocketTimeoutException e){
                System.out.print("Timeout connecting to "+Port+" Retrying...\n");

                try{
                    Thread.sleep(2000);
                }catch(InterruptedException ex){
                    Thread.currentThread().interrupt();

                }

            }catch(IOException IE){
                System.out.print("failed to connect to node "+Port+IE.getMessage()+"\n");
                break;
            }

        }
    }


    static {
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            for (Socket socket :OpenSockets){
                try{
                    if(!socket.isClosed()){
                        socket.close();
                        System.out.print("closed socket for port :"+socket.getPort());
                    }
                } catch (IOException e) {
                    System.out.print("failed to close the socket during cleaning up");
                }
            }
        }));
    }

    private static void Handle_client(Socket clientsock){
        try(
                BufferedReader in=new BufferedReader(new InputStreamReader(clientsock.getInputStream()));
                PrintWriter out=new PrintWriter(clientsock.getOutputStream(),true);

        ) {
            out.println("input 2 values\n");
            int x= Integer.parseInt( in.readLine());
            int y= Integer.parseInt( in.readLine());
            int result=x+y;
            System.out.print(result+"\n");

        } catch (IOException e) {
            System.out.print("sth wrong happened while trying to read from the client\n");
        }finally {
            try {
                clientsock.close();
                System.out.print("memory freed\n");
            } catch (IOException e) {
                System.out.print("error closing client sock");
            }
        }


    }


    public static void main(String[] args) {



        if ( args.length >= 2 && args[0].equals("--port")) {
            try {
                port = Integer.parseInt(args[1]);

            }
            catch (NumberFormatException e) {
                System.out.print("default port gonna be used ");
            }
        }
        new Thread(()->Main.start_server(port)).start();
        new Thread(()->Main.connectToPeers(65531)).start();
        new Thread(()->Main.connectToPeers(65532)).start();

    }
}