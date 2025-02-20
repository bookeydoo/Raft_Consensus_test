### NOTE:So all the servers start as followers so none of them will have transactions in their log at first

1. **Leader**:
   - Leader is the one that recieves requests from the client

2. **Election steps**

   - Each follower sets a randomized election timeout (typically between 150 and 300 milliseconds).
      If a follower does not receive any communication (like a heartbeat) from a leader within this timeout, it assumes there is no active leader.

   - The follower transitions to a candidate state and starts a new election term.
   -  It increments its term number, votes for itself, and sends RequestVote messages to all other servers in the       cluster.


3. **Election**:
- Since there’s no leader at startup, all servers begin as followers and then start an election after a randomized timeout. The election isn’t about updating logs—it’s about choosing which server will act as the leader to accept new client commands.

4. **Leader Takes Over Log Updates**
- Once a leader is elected, it starts accepting client requests (i.e., new transactions). It appends these transactions to its log and then replicates them to the followers via the AppendEntries RPC .

5. **Consensus on Log State**
- The log’s state is synchronized among servers after the leader is chosen. Even if all logs are empty initially, the first leader’s log (as it builds up with new transactions) becomes the source of truth. During elections, if logs aren’t empty, candidates include their last log index and term, and voters will only vote for candidates whose logs are at least as up-to-date as their own.
