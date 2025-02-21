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
import java.util.concurrent.*;


public class Main extends Thread{
    public static Random random=new Random();

    private static ServerSocket srvr=null;

    public static int Serverport =65530;// default Serverport

    public static ScheduledExecutorService ElectionTimeoutScheduler= Executors.newScheduledThreadPool(1);
    private static ScheduledFuture<?>electionTimeoutFuture;
    public static int LeaderPort=0; //if it equals 0 means unknown Serverport else we will specify the actual value

    private static boolean isLeader=false;
    private static boolean isCandidate =false;

    private static int CurrentTerm =0;  //stands for election CurrentTerm it is incremented when an election starts

    private static final List<Socket>Peers=new CopyOnWriteArrayList<>();

    private static List<String>Log=new ArrayList<>();

    private static String VotedFor="";

    private static List<Candidate> Votes=new CopyOnWriteArrayList<>();


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

        synchronized (Main.class){
            if(isCandidate || isLeader){
                return;
            }


            CurrentTerm++;
            isCandidate=true;
            VotedFor="self";
            Votes.clear();
            Votes.add(new Candidate(Serverport,1));
        }

        for(Socket Peer:Peers){
            RequestVote(Peer);
        }

        new Thread(()->{
            while (true){
                synchronized (Main.class){
                    if(!isCandidate) break;
                    for(Candidate c:Votes){
                        if(c.getCandidatePort()==Serverport){

                            if( c.NoOfVotes  > Peers.size()/2){
                                isLeader=true;
                                isCandidate=false;
                                break;
                            }
                        }
                    }
                }try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();

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
       
        while (true) {
            Socket socket=new Socket();

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
                if(Msg.startsWith("REQUEST-VOTE")){

                    handleVoteRequest(Msg,out,clientsock);

                }else if(Msg.startsWith("APPEND-ENTRIES")){
                    //reset Election (this means the leader is live)
                    scheduleElectionTimeout();
                    LeaderPort= clientsock.getPort();
                    isCandidate=false;
                    String [] parts =Msg.split("APPEND-ENTRIES",2);
                    int leaderTerm=Integer.parseInt(parts[1]);
                    if(leaderTerm<CurrentTerm){
                        out.println("APPEND-REJECTED"+CurrentTerm);
                    }
                    if(parts.length>1){
                        String result=parts[1];
                        Log.add(result);
                    }



                } else if(Msg.startsWith("VOTE-GRANTED")){
                    synchronized (Main.class){
                        Votes.add(new Candidate(clientsock.getPort(),1));
                    }
                }
            }

        } catch (IOException e) {
            System.out.print("sth wrong happened while trying to read from the client\n");
        }
    }






    private static void handleVoteRequest(String msg,PrintWriter out,Socket sock) {

        String[] parts = msg.split(" ");
        int term = Integer.parseInt(parts[1]);

        synchronized (Main.class) {
            if (term > CurrentTerm) {
                isCandidate=false;
                isLeader=false;
                CurrentTerm = term;
                VotedFor = "" ;
            }
            int candidateLastLogindex=Integer.parseInt(parts[3]);
            if(Log.size()>candidateLastLogindex){
                out.println("VOTE-DENIED"+CurrentTerm);
                return;
            }

            if(VotedFor.equals(parts[2])){
                int candidatePort=Integer.parseInt(parts[2]);
                Votes.add(new Candidate(candidatePort,1));
            }

            if(VotedFor.isEmpty() || VotedFor.equals(parts[2])){
                VotedFor=parts[2];
                out.println("VOTE-GRANTED "+CurrentTerm);
                PersistState(Serverport,VotedFor);
            }else {
                out.println("VOTE-DENIED "+CurrentTerm);
            }
        }

    }
    private static void LeaderFunc(String lastLogEntry){

    while (isLeader){
        System.out.print("I am the leader");
        for(Socket Peer:Peers){
            try {
                PrintWriter out=new PrintWriter(Peer.getOutputStream(),true);
                out.println(String.format("APPEND-ENTRIES %d %s %d",CurrentTerm,lastLogEntry,Log.size()));
            } catch (IOException e) {
                System.out.print("Heartbeat failed to node :"+Peer);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                System.out.print("oh no bro");
                }
            }
        }
    }

    //no idea what this does copy and pasted tbh
    private static void scheduleElectionTimeout() {
        // Cancel existing timeout (if any)
        if (electionTimeoutFuture != null && !electionTimeoutFuture.isDone()) {
            electionTimeoutFuture.cancel(false);
        }

        // Randomize timeout (150-300ms)
        int timeout = random.nextInt(150, 300);
        electionTimeoutFuture = ElectionTimeoutScheduler.schedule(
                () -> {
                    synchronized (Main.class) {
                        if (!isLeader && !isCandidate) {
                            Start_Election();
                        }
                    }
                    scheduleElectionTimeout(); // Reschedule after execution
                },
                timeout,
                TimeUnit.MILLISECONDS
        );
    }


    public static void main(String[] args) {



        if ( args.length >= 2 && args[0].equals("--port")) {
            try {
                Serverport = Integer.parseInt(args[1]);

            }
            catch (NumberFormatException e) {
                System.out.print("default Serverport gonna be used ");
            }
        }

         ElectionTimeoutScheduler=Executors.newScheduledThreadPool(1);
         ElectionTimeoutScheduler.scheduleAtFixedRate(()->{
            if(!isLeader && !isCandidate){
                Start_Election();
            }
        },0,150+random.nextInt(150), TimeUnit.MILLISECONDS);


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