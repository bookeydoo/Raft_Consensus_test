package org.example;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.lang.Thread;
import java.net.Socket;
import java.util.ArrayList;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;


public class Main extends Thread{
    public static Random random=new Random();

    private static ServerSocket srvr=null;

    public static int Serverport =65530;// default Serverport

    private ScheduledExecutorService ElectionTimeoutScheduler= Executors.newScheduledThreadPool(1);
    public static int LeaderPort=0; //if it equals 0 means unknown Serverport else we will specify the actual value

    private static boolean isLeader=false;
    private static boolean isCandidate =false;

    private static int CurrentTerm =0;  //stands for election CurrentTerm it is incremented when an election starts

    private static final List<Socket>Peers=new CopyOnWriteArrayList<>();

    private static List<String>Log=new ArrayList<>();

    private static String VotedFor="";

    private static List<Vote> Votes=new CopyOnWriteArrayList<>();


    public static void start_server(int Port) {


        try {
            srvr = new ServerSocket(Port);
            System.out.print("started the server on Serverport "+ Serverport);

            while(true){
                Socket clientsock=srvr.accept();
                System.out.print("connection occured");
                new Thread(() -> Handle_client(clientsock)).start();
            }


        } catch (IOException e) {
            System.out.print("failed to create the server "+e);
        }

    }

    private static void RequestVote(Socket node){
        try {
            PrintWriter out = new PrintWriter(node.getOutputStream(), true);
            int lastlogindex=Log.size();

            out.println(String.format("REQUEST-VOTE %d %s %d ",CurrentTerm,"Candidateid",lastlogindex));
        } catch (IOException e) {
            System.out.print("failed to send vote req");
        }
    }


    private static void Start_Election(){
        //Election timeout (used for electing leader)

        if(isCandidate){
            for(Socket Peer:Peers){
                RequestVote(Peer);
            }
        }
    }

    private static  void PersistState(int port,String votedfor){
        try(FileWriter writer =new FileWriter("node"+port+".txt")){
            writer.write("current CurrentTerm :"+ CurrentTerm +"\n");
            writer.write("Voted for :"+votedfor);
        } catch ( IOException e) {
            System.out.print("failed to persist this state");
        }

    }


    private static void connectToPeers(int Port){

        //normal timeout
        int Timeout=random.nextInt(50,200);


        boolean connected=false;
        InetSocketAddress address=new InetSocketAddress(Port);
        Socket socket=new Socket();
        while (!connected) {


            try{
                System.out.print("attempting to connect  to node :"+Port+"timeout:"+Timeout+"ms \n");
                socket.connect(address,Timeout);

                connected=true;
                System.out.print("we are connected \n");
                Peers.add(socket);

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


    private static void Handle_client(Socket clientsock){ //TODO change this to listen for the votes
        try(
                BufferedReader in=new BufferedReader(new InputStreamReader(clientsock.getInputStream()));
                PrintWriter out=new PrintWriter(clientsock.getOutputStream(),true);


        ) {


            String Msg;

            if(isLeader){
                while ((Msg=in.readLine()) != null) {
                    Log.add(Msg);
                }
                LeaderFunc(Log.getLast());

                return;
            }

            while ((Msg=in.readLine()) != null){
                if(Msg.startsWith("REQUEST_VOTE")){
                    if(!isCandidate){
                    handleVoteRequest(Msg,out);
                    }
                }else if(Msg.startsWith("APPEND-ENTRIES")){
                    //reset Election (this means the leader is live)
                    LeaderPort= clientsock.getPort();
                    String [] parts =Msg.split("APPEND-ENTRIES",2);
                    if(parts.length>1){
                        String result=parts[1];
                        Log.add(result);
                    }


                }
            }

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

    private static void handleVoteRequest(String msg,PrintWriter out){

        String [] parts=msg.split(" ");
        int term=Integer.parseInt(parts[1]);
        if(term>CurrentTerm){
            CurrentTerm=term;
            VotedFor=null;
        }
        if(isCandidate){
            VotedFor="Me";
        }
        //write voting stuff

        out.write(VotedFor);
     }


    private static void LeaderFunc(String lastLogEntry){

    while (isLeader){
        for(Socket Peer:Peers){
            try {
                PrintWriter out=new PrintWriter(Peer.getOutputStream(),true);
                out.println(String.format("APPEND_ENTRIES %d %s",CurrentTerm,lastLogEntry));
            } catch (IOException e) {
                System.out.print("Heartbeat failed to node :"+Peer);
            }
            try {
                int x=random.nextInt(150,200);
                Thread.sleep(x);
            } catch (InterruptedException e) {
                System.out.print("oh no bro");
                }
            }
        }
    }



    public static void main(String[] args) {



        if ( args.length >= 2 && args[0].equals("--Serverport")) {
            try {
                Serverport = Integer.parseInt(args[1]);

            }
            catch (NumberFormatException e) {
                System.out.print("default Serverport gonna be used ");
            }
        }


        new Thread(()->Main.start_server(Serverport)).start();
        for( int i=65530 ; i< 65533;i++){
            if(i== Serverport){
                continue;
            }
            int peerport=i;
            new Thread(()->Main.connectToPeers(peerport)).start();

        }
    }
}