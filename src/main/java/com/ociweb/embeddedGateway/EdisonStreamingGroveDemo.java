package com.ociweb.embeddedGateway;

import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.impl.SimpleLogger;

import com.ociweb.device.config.GroveConnectionConfiguration;
import com.ociweb.device.config.GroveShieldV2EdisonConfiguration;
import com.ociweb.device.config.GroveShieldV2MockConfiguration;
import com.ociweb.device.grove.GroveConnect;
import com.ociweb.device.grove.GroveShieldV2ResponseStage;
import static com.ociweb.device.grove.GroveTwig.*;
import com.ociweb.device.grove.schema.GroveResponseSchema;
import com.ociweb.embeddedGateway.schema.DataGeneratorSchema;
import com.ociweb.embeddedGateway.schema.SystemSchema;
import com.ociweb.embeddedGateway.stage.BrowserSubscriptionStage;
import com.ociweb.embeddedGateway.stage.CPUMonitorStage;
import com.ociweb.embeddedGateway.stage.DataGenerationStage;
import com.ociweb.embeddedGateway.stage.MQTTPublishCPUMonitorStage;
import com.ociweb.embeddedGateway.stage.MQTTPublishGeneratedDataStage;
import com.ociweb.embeddedGateway.stage.MQTTPublishSensorDataStage;
import com.ociweb.embeddedGateway.stage.MQTTSubscriptionStage;
import com.ociweb.pronghorn.adapter.netty.WebSocketSchema;
import com.ociweb.pronghorn.adapter.netty.WebSocketServerPronghornStage;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.RawDataSchema;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.StageScheduler;
import com.ociweb.pronghorn.stage.scheduling.ThreadPerStageScheduler;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

public class EdisonStreamingGroveDemo {

    private static final int longestVariableByteFieldValue = 16;
    private static final int maxCountOfMessagesOnPipe = 10;
    
    private static final int maxDataGenOnPipe = 50;
    private static final int maxWebSocketMessagesInFlight = 50;
        
    
    ////////////
    //Define all the pipe configurations, eg types and sizes
    ///////////
    private static final PipeConfig<WebSocketSchema> toBrowserConfig = new PipeConfig<WebSocketSchema>(WebSocketSchema.instance, maxWebSocketMessagesInFlight, longestVariableByteFieldValue);
    private static final PipeConfig<WebSocketSchema> fromBrowserConfig = new PipeConfig<WebSocketSchema>(WebSocketSchema.instance, maxWebSocketMessagesInFlight, longestVariableByteFieldValue);
    private static final PipeConfig<RawDataSchema> toSubscribersConfig = new PipeConfig<RawDataSchema>(RawDataSchema.instance, maxWebSocketMessagesInFlight, longestVariableByteFieldValue);
    private static final PipeConfig<SystemSchema> cpuLoadPipeConfig = new PipeConfig<SystemSchema>(SystemSchema.instance, maxCountOfMessagesOnPipe);
    private static final PipeConfig<DataGeneratorSchema> genDataPipeConfig = new PipeConfig<DataGeneratorSchema>(DataGeneratorSchema.instance, maxDataGenOnPipe);
    private static final PipeConfig<GroveResponseSchema> groveResponsePipeConfig = new PipeConfig<GroveResponseSchema>(GroveResponseSchema.instance, maxCountOfMessagesOnPipe);        
    ////////////
    ////////////
    
    
    private StageScheduler scheduler;
    
    
    private static final String host = "tcp://127.0.0.1:1883";
    private static final int qos = 0;
    
    static {
        //This line is required to make netty be quiet 
        System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "warn");        
    }
    
    public EdisonStreamingGroveDemo() {      
    }
    
    
    public static void main(String[] args) {
        
         EdisonStreamingGroveDemo instance = new EdisonStreamingGroveDemo();   

            EventLoopGroup eventGroupLoop = new NioEventLoopGroup(1); //this is only needed for netty                    
            GraphManager gm = instance.buildGraph(new GraphManager(), eventGroupLoop, eventGroupLoop);
            instance.start(gm);

            try {
                Thread.sleep(60000*60*24); //stop if left running for a day
            } catch (InterruptedException e) {               
            }            
            
            instance.stop();

    }

     public GraphManager buildGraph(GraphManager gm, EventLoopGroup bossGroup, EventLoopGroup workerGroup) {
        
        
        ////////
        //collect the cpu usage and publish it on mqtt
        ///////            
        
        Pipe<SystemSchema> cpuLoadPipe = new Pipe<SystemSchema>(cpuLoadPipeConfig);            
        
        CPUMonitorStage cpuLoadMonitorStage = new CPUMonitorStage(gm, cpuLoadPipe);
        GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 500*1000*1000, cpuLoadMonitorStage);
        
        MQTTPublishCPUMonitorStage cpuLoadPublish = new MQTTPublishCPUMonitorStage(gm, cpuLoadPipe, host, "CPU monitor demo", qos);
        GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 500*1000*1000, cpuLoadPublish);
        
        //////////
        ///generate sine wave and random numbers and publish on mqtt
        ///////////
        
        Pipe<DataGeneratorSchema> getDataPipe = new Pipe<DataGeneratorSchema>(genDataPipeConfig);     
        
        DataGenerationStage dataGenStage = new DataGenerationStage(gm, getDataPipe);
        GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 10*1000*1000, dataGenStage);
        
        MQTTPublishGeneratedDataStage genDataPublish = new MQTTPublishGeneratedDataStage(gm, getDataPipe, host, "Gen Data demo", qos );
        GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 40*1000*1000, genDataPublish);
        
        ////////
        //pull data from all the configured sensors and publish on mqtt
        ////////
        
        Pipe<GroveResponseSchema> grovePipe = new Pipe<GroveResponseSchema>(groveResponsePipeConfig);     
        
        GroveConnectionConfiguration config = null;
        
        System.out.println("review this on Edison for clue to detect when we are running there.");
        Set<Entry<Object, Object>> props = System.getProperties().entrySet();
        for (Entry<Object, Object> e: props) {
            System.out.println(e.getKey()+" === "+e.getValue());
        }
        
        boolean isOnEdison = false;
        
        if (isOnEdison) {
        
            config = new GroveShieldV2EdisonConfiguration(
                    false, //publish time 
                    false,  //turn on I2C
                    new GroveConnect[] {new GroveConnect(RotaryEncoder,2),new GroveConnect(RotaryEncoder,3)}, //rotary encoder 
                    new GroveConnect[] {new GroveConnect(Button,0) ,new GroveConnect(MotionSensor,8)}, //7 should be avoided it can disrupt WiFi, button and motion 
                    new GroveConnect[] {}, //for requests like do the buzzer on 4
                    new GroveConnect[]{},  //for PWM requests //(only 3, 5, 6, 9, 10, 11) //3 here is D3
                    new GroveConnect[] {new GroveConnect(MoistureSensor,1), //1 here is A1
                                        new GroveConnect(LightSensor,2), 
                                        new GroveConnect(UVSensor,3)
                                  }); //for analog sensors A0, A1, A2, A3
        } else {
           System.out.println("Not on edison hardware so mock data sensors will be used.");
          //Fake configuration to mock behavior of hardware.
           config = new GroveShieldV2MockConfiguration(
                  false, //publish time 
                  false,  //turn on I2C
                  new GroveConnect[] {new GroveConnect(RotaryEncoder,2),new GroveConnect(RotaryEncoder,3)}, //rotary encoder 
                  new GroveConnect[] {new GroveConnect(Button,0) ,new GroveConnect(MotionSensor,8)}, //7 should be avoided it can disrupt WiFi, button and motion 
                  new GroveConnect[] {}, //for requests like do the buzzer on 4
                  new GroveConnect[]{},  //for PWM requests //(only 3, 5, 6, 9, 10, 11) //3 here is D3
                  new GroveConnect[] {new GroveConnect(MoistureSensor,1), //1 here is A1
                                 new GroveConnect(LightSensor,2), 
                                 new GroveConnect(UVSensor,3)
                                }); //for analog sensors A0, A1, A2, A3
        }
        
        config.coldSetup(); //set initial state so we can configure the Edison soon.
        
        GroveShieldV2ResponseStage responseStage = new GroveShieldV2ResponseStage(gm, grovePipe, config);
        GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 10*1000*1000, responseStage);
        
        MQTTPublishSensorDataStage sensorPublish = new MQTTPublishSensorDataStage(gm, grovePipe, host, "sensor demo", qos );
        GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 40*1000*1000, sensorPublish);
        
                
        ///////     
        //Subscribe to all the MQTT messages and publish them to the browser ove websocket
        //////
        
        @SuppressWarnings("unchecked")
        Pipe<WebSocketSchema>[] toBrowserPipes = new Pipe[] {new Pipe<WebSocketSchema>(toBrowserConfig)};
        @SuppressWarnings("unchecked")
        Pipe<WebSocketSchema>[] fromBrowserPipes = new Pipe[] {new Pipe<WebSocketSchema>(fromBrowserConfig)};

        //for development to get the updates while changing the JS
        //WebSocketServerPronghornStage.setRelativeAppFolderRoot(SystemPropertyUtil.get("user.dir")+"/src/main/resources/webApp"); 
        
        WebSocketServerPronghornStage serverStage = new WebSocketServerPronghornStage(gm, toBrowserPipes, fromBrowserPipes, bossGroup, workerGroup); 
        //this stage has its own thread (not normal and to be fixed) and so never needs to be scheduled      
        GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE,  10*1000*1000*1000, serverStage);
        
        int i = toBrowserPipes.length;
        while (--i>=0) {
            
            @SuppressWarnings("unchecked")
            Pipe<RawDataSchema>[] toSubscribers = new Pipe[10];
            for (int source=1;source<=10;source++) {                
                toSubscribers[source-1] = new Pipe<RawDataSchema>(toSubscribersConfig);
                MQTTSubscriptionStage stage = new MQTTSubscriptionStage(gm, toSubscribers[source-1], "source/"+source);
                //this stage has its own thread (not normal and to be fixed) and so never needs to be scheduled               
                GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 10*1000*1000*1000, stage);
                                
            }                        
            //this rate must be high to ensure smooth graph
            BrowserSubscriptionStage stage = new BrowserSubscriptionStage(gm, fromBrowserPipes[i], toBrowserPipes[i], toSubscribers);
            GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 10*1000*1000, stage);
            
        }        
        
        return gm;
    }
    

    public void start(GraphManager gm) {
        
        scheduler = new ThreadPerStageScheduler(gm);
        scheduler.startup();  
    }
        
    private void stop() {
        scheduler.shutdown();
        scheduler.awaitTermination(2, TimeUnit.SECONDS);
    }       
      
}
