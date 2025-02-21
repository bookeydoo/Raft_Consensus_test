package org.example;

public class Candidate {
    public int CandidatePort;
    public int NoOfVotes;

    public Candidate(int x, int y){
        CandidatePort=x;
        NoOfVotes=y;
    }

    public int getCandidatePort() {
        return CandidatePort;
    }
}
