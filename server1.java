

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.lang.Thread;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Random;
import java.util.Scanner;
import java.util.random.*;

//basic server that does does summation to 2 values and return it

public class server1 extends Thread{

    public static Random random=Random;

    private static ServerSocket srvr=null;

    public static int port=65530;// default port
    public static void start_server(int Port) {


        try {
            srvr = new ServerSocket(Port);
            System.out.print("started the server on port "+port);

            while(true){
                Socket clientsock=srvr.acc-ept();
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
       while (!connected) {
        
       
        try(Socket socket=new Socket()){
            System.out.print("attempting to connect  to node :"+Port+"timeout:"+Timeout+"ms \n");
            socket.connect(address,Timeout);
            
            connected=true;
            System.out.print("we are connected");
            
        }   catch(SocketTimeoutException e){
            System.out.print("Timeout connecting to "+Port+" Retrying...\n");
            socket.connect(address,Timeout);
        }catch(IOException IE){
            System.out.print("failed to connect to node "+Port+IE.getMessage());
            break;
        }
    }
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
        new Thread(()->server1.start_server(port)).start();
        new Thread(()->server1.connectToPeers(65531)).start();


    }
}