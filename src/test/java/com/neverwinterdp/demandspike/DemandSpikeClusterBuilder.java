package com.neverwinterdp.demandspike;

import com.neverwinterdp.server.Server;
import com.neverwinterdp.server.gateway.ClusterGateway;
import com.neverwinterdp.server.gateway.CommandParams;
import com.neverwinterdp.server.shell.Shell;
import com.neverwinterdp.sparkngin.http.NullDevMessageForwarder;
import com.neverwinterdp.util.FileUtil;
/**
 * @author Tuan Nguyen
 * @email  tuan08@gmail.com
 */
public class DemandSpikeClusterBuilder {
  static {
    System.setProperty("app.dir", "build/cluster") ;
    System.setProperty("app.config.dir", "src/app/config") ;
    System.setProperty("log4j.configuration", "file:src/app/config/log4j.properties") ;
  }
  
  public static String TOPIC = "metrics.consumer" ;
  
  public Server zkServer, sparknginServer, demandSpikeServer ;
  public Server[] kafkaServer ;
  public Shell shell ;
  public ClusterGateway gateway ;
  
  public DemandSpikeClusterBuilder() {
    kafkaServer = new Server[1] ;
  }
  
  public DemandSpikeClusterBuilder(int numOfKafkaServer) {
    kafkaServer = new Server[numOfKafkaServer] ;
  }

  public void start() throws Exception {
    FileUtil.removeIfExist("build/cluster", false);
    zkServer = Server.create("-Pserver.name=zookeeper", "-Pserver.roles=zookeeper") ;
    
    for(int i  = 0; i < kafkaServer.length; i++) {
      int id = i + 1;
      kafkaServer[i] = Server.create("-Pserver.name=kafka" + id, "-Pserver.roles=kafka") ;
    }
    sparknginServer = Server.create("-Pserver.name=sparkngin", "-Pserver.roles=sparkngin") ;
    
    demandSpikeServer = Server.create("-Pserver.name=demandspike", "-Pserver.roles=demandspike") ;
    
    shell = new Shell() ;
    shell.getShellContext().connect();
    gateway = shell.getShellContext().getClusterGateway() ;
    //Wait to make sure all the servervices are launched
    Thread.sleep(2000) ;
  }
  
  public void destroy() throws Exception {
    shell.close();
    demandSpikeServer.destroy() ;
    sparknginServer.destroy() ;
    for(int i  = 0; i < kafkaServer.length; i++) {
      kafkaServer[i].destroy() ;
    }
    zkServer.destroy() ;
  }
 
  public void install() throws Exception {
    gateway.module.execute(
        "install", 
        new CommandParams().
          field("member-role", "zookeeper").
          field("autostart", true).
          field("module", new String[] { "Zookeeper" }).
          field("-Pmodule.data.drop", "true")
    ) ;

    String kafkaReplication = kafkaServer.length >= 2 ? "2" : "1" ;
    for(int i  = 0; i < kafkaServer.length; i++) {
      int id = i + 1;
      gateway.module.execute(
          "install", 
          new CommandParams().
            field("member-name", "kafka" + id).
            field("autostart", true).
            field("module", new String[] { "Kafka" }).
            field("-Pmodule.data.drop", "true").
            field("-Pkafka:broker.id", Integer.toString(id)).
            field("-Pkafka:port", Integer.toString(9092 + i)).
            field("-Pkafka:zookeeper.connect", "127.0.0.1:2181").
            field("-Pkafka:default.replication.factor", kafkaReplication).
            field("-Pkafka:controller.socket.timeout.ms", "90000").
            field("-Pkafka:controlled.shutdown.enable", "true"). 
            field("-Pkafka:controlled.shutdown.max.retries", "3").
            field("-Pkafka:controlled.shutdown.retry.backoff.ms", "60000")
      ) ;
    }

    gateway.module.execute(
        "install", 
        new CommandParams().
          field("member-role", "sparkngin").
          field("autostart", true).
          field("module", new String[] { "Sparkngin" }).
          field("-Pmodule.data.drop", "true").
          field("-Phttp-listen-port", "8181").
          field("-Pforwarder-class",NullDevMessageForwarder.class.getName())
    ) ;
    
    gateway.module.execute(
        "install", 
        new CommandParams().
          field("member-role", "demandspike").
          field("autostart", true).
          field("module", new String[] { "DemandSpike" })
    ) ;
         ;
    shell.execute("server registration");
    Thread.sleep(1000);
  }
  
  public void uninstall() {
    shell.execute("module uninstall --member-role demandspike --timeout 20000 --module DemandSpike");
    shell.execute("module uninstall --member-role sparkngin --timeout 20000 --module Sparkngin");
    shell.execute("module uninstall --member-role kafka --timeout 20000 --module Kafka");
    shell.execute("module uninstall --member-role zookeeper --timeout 20000 --module Zookeeper");
  }
  
  public String getKafkaConnect() {
    StringBuilder b = new StringBuilder() ;
    for(int i = 0; i < kafkaServer.length; i++) {
      if(i > 0) b.append(",") ;
      b.append("127.0.0.1:").append(9092 + i) ;
    }
    return b.toString() ;
  }
}