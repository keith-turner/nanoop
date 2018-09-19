# Nanoop

A simple Java agent that instruments Namenode RPC calls to track what Accumulo
code is calling the RPC. Build this agent using `mvn package`.  When its built,
edit `$ACCUMULO_HOME/conf/accumulo-env.sh` and add the following to
`ACCUMULO_GENERAL_OPTS`.

```
-javaagent:$NANOOP_DIR/target/nanoop-1.0.0-SNAPSHOT.jar
```

Currently the only way to get the agents output is to cleanly kill the Accumulo
processes.

Nanoop output from bulk importing 10,000 files is in [bulk-test.md](bulk-test.md)

Thanks to [ivanyu][1] for a great example!.  It made writing this agent super
quick.

[1]: https://github.com/ivanyu/java-agents-demo
