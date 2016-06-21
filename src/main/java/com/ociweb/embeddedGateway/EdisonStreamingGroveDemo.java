package com.ociweb.embeddedGateway;

import static com.ociweb.iot.grove.GroveTwig.Button;
import static com.ociweb.iot.grove.GroveTwig.LightSensor;
import static com.ociweb.iot.grove.GroveTwig.MoistureSensor;
import static com.ociweb.iot.grove.GroveTwig.MotionSensor;
import static com.ociweb.iot.grove.GroveTwig.RotaryEncoder;
import static com.ociweb.iot.grove.GroveTwig.UVSensor;

import java.util.concurrent.TimeUnit;

import org.slf4j.impl.SimpleLogger;

import com.ociweb.embeddedGateway.schema.DataGeneratorSchema;
import com.ociweb.embeddedGateway.schema.SystemSchema;
import com.ociweb.embeddedGateway.stage.BrowserSubscriptionStage;
import com.ociweb.embeddedGateway.stage.CPUMonitorStage;
import com.ociweb.embeddedGateway.stage.DataGenerationStage;
import com.ociweb.embeddedGateway.stage.I2CCommandStage;
import com.ociweb.embeddedGateway.stage.MQTTPublishCPUMonitorStage;
import com.ociweb.embeddedGateway.stage.MQTTPublishGeneratedDataStage;
import com.ociweb.embeddedGateway.stage.MQTTPublishSensorDataStage;
import com.ociweb.embeddedGateway.stage.MQTTSubscriptionStage;
import com.ociweb.iot.hardware.GroveShieldV2EdisonImpl;
import com.ociweb.iot.hardware.GroveShieldV2MockImpl;
import com.ociweb.iot.hardware.HardConnection;
import com.ociweb.iot.hardware.Hardware;
import com.ociweb.pronghorn.adapter.netty.WebSocketSchema;
import com.ociweb.pronghorn.adapter.netty.WebSocketServerPronghornStage;
import com.ociweb.pronghorn.iot.ReadDeviceInputStage;
import com.ociweb.pronghorn.iot.i2c.PureJavaI2CStage;
import com.ociweb.pronghorn.iot.schema.GroveResponseSchema;
import com.ociweb.pronghorn.iot.schema.I2CCommandSchema;
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
    private static final int maxCountOfMessagesOnPipe = 100;
    
    private static final int maxDataGenOnPipe = 100;
    private static final int maxWebSocketMessagesInFlight = 100;
        
    
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
            
            
            StringBuilder dot = new StringBuilder();
            GraphManager.writeAsDOT(gm, dot);
            System.out.println(dot);
            
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
        
        CPUMonitorStage cpuLoadMonitorStage = new CPUMonitorStage(gm, PipeConfig.pipe(cpuLoadPipeConfig));
        GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 100*1000*1000, cpuLoadMonitorStage);
        
        MQTTPublishCPUMonitorStage cpuLoadPublish = new MQTTPublishCPUMonitorStage(gm, GraphManager.<SystemSchema>getOutputPipe(gm, cpuLoadMonitorStage),
                                                                                   host, "CPU monitor demo", qos);
        GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 200*1000*1000, cpuLoadPublish);
        
        //////////
        ///generate sine wave and random numbers and publish on mqtt
        ///////////
        DataGenerationStage dataGenStage = new DataGenerationStage(gm, PipeConfig.pipe(genDataPipeConfig));
        GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 50*1000*1000, dataGenStage);
        
        MQTTPublishGeneratedDataStage genDataPublish = new MQTTPublishGeneratedDataStage(gm, GraphManager.<DataGeneratorSchema>getOutputPipe(gm, dataGenStage), host, "Gen Data demo", qos );
        GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 100*1000*1000, genDataPublish);
        
        ////////
        //pull data from all the configured sensors and publish on mqtt
        ////////
                   
        
        Hardware config = null;

        boolean isOnEdison = true;
        
        if (isOnEdison) {
        
             config = new GroveShieldV2EdisonImpl().useI2C().useConnectDs(RotaryEncoder,2,3).useConnectD(Button, 0).useConnectD(MotionSensor,8).useConnectA(MoistureSensor,1).useConnectA(LightSensor, 2).useConnectA(UVSensor,3);
                   
             setupRGBLCD(gm, config);   
            
        } else {
           System.out.println("Not on edison hardware so mock data sensors will be used.");
          //Fake configuration to mock behavior of hardware.
           config = new GroveShieldV2EdisonImpl().useConnectDs(RotaryEncoder,2,3).useConnectD(Button, 0).useConnectD(MotionSensor,8).useConnectA(MoistureSensor,1).useConnectA(LightSensor, 2).useConnectA(UVSensor,3);
           
        }
        
        config.coldSetup(); //set initial state so we can configure the Edison soon.
        
        ReadDeviceInputStage responseStage = new ReadDeviceInputStage(gm, PipeConfig.pipe(groveResponsePipeConfig), config);
        GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 5*1000*1000, responseStage);
        
        MQTTPublishSensorDataStage sensorPublish = new MQTTPublishSensorDataStage(gm, GraphManager.<GroveResponseSchema>getOutputPipe(gm, responseStage), host, "sensor demo", qos );
        GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 10*1000*1000, sensorPublish);
                
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
        GraphManager.addNota(gm, GraphManager.UNSCHEDULED,  GraphManager.UNSCHEDULED, serverStage);
        
        int i = toBrowserPipes.length;
        while (--i>=0) {
            
            @SuppressWarnings("unchecked")
            Pipe<RawDataSchema>[] toSubscribers = new Pipe[10];
            for (int source=1;source<=10;source++) {                
                toSubscribers[source-1] = new Pipe<RawDataSchema>(toSubscribersConfig);                                
            }                        

            MQTTSubscriptionStage subStage = new MQTTSubscriptionStage(gm, toSubscribers, "source/#");
            //this stage has its own thread (not normal and to be fixed) and so never needs to be scheduled  
            GraphManager.addNota(gm, GraphManager.UNSCHEDULED, GraphManager.UNSCHEDULED, subStage);

            //this rate must be high to ensure smooth graph
            BrowserSubscriptionStage stage = new BrowserSubscriptionStage(gm, fromBrowserPipes[i], toBrowserPipes[i], toSubscribers);
            GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 10*1000*1000, stage);
            
        }        
        
        return gm;
    }
     
    private void setupRGBLCD(GraphManager gm, Hardware config) {
        PipeConfig<I2CCommandSchema> requestI2CConfig = new PipeConfig<I2CCommandSchema>(I2CCommandSchema.instance, 64, 256);

        
        Pipe<I2CCommandSchema> i2cToBusPipe = new Pipe<I2CCommandSchema>(requestI2CConfig);
        
        I2CCommandStage comStage = new I2CCommandStage(gm,i2cToBusPipe); //TODO: old test code delete and the class soon.
        
        Pipe[] requests = new Pipe[]{i2cToBusPipe};
        Pipe[] response = new Pipe[0];
        
        new PureJavaI2CStage(gm, requests, response, config);
        
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
