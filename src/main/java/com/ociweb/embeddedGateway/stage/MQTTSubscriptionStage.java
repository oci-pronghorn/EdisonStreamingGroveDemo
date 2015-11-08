package com.ociweb.embeddedGateway.stage;

import static org.junit.Assert.fail;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.RawDataSchema;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class MQTTSubscriptionStage extends PronghornStage {

    private static final Logger log = LoggerFactory.getLogger(MQTTSubscriptionStage.class);
    
    private final String host;
    private final String clientName;
    private final Pipe<RawDataSchema> output;
    private final int msgSize;
    private MqttClient client;
    private final String topic;
    
    public MQTTSubscriptionStage(GraphManager graphManager, Pipe<RawDataSchema> output, String topic) {
        super(graphManager, NONE, output);
        
        this.host = "tcp://localhost:1883";
        this.clientName = "MQTTSubscriptionStage"+this.stageId;
        this.output = output;
        this.topic = topic;
        this.msgSize = Pipe.from(output).fragDataSize[0];
    }
    
    @Override
    public void startup() {
        try {
            client = new MqttClient(host, clientName, new MemoryPersistence());
            client.setCallback(getCallback());
            client.connect();
            client.subscribe(topic, 0);
        } catch (MqttException e) {
          log.error("Startup",e);  
          throw new RuntimeException(e);
        }
    }
    
    private MqttCallback getCallback() {
        return new MqttCallback() {

            @Override
            public void connectionLost(Throwable cause) {
                log.error("Connection lost",cause);
                try {
                    client.connect();
                    client.subscribe(topic, 2); 
                } catch (MqttException e) {
                    log.error("Callback",e);  
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                                
                //NOTE: This is bad and blocking but it will do for the demo until the MQTT Client from OCI is completed.
                while (!Pipe.hasRoomForWrite(output, msgSize)) {
                }
                
                Pipe.addMsgIdx(output, 0);
                byte[] payload = message.getPayload();
                Pipe.addByteArray(payload, 0, payload.length, output);
                Pipe.publishWrites(output);    
                
                Pipe.confirmLowLevelWrite(output, msgSize);

            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                //not used this is a subscriber only.
            }
            
        };
    }

    @Override
    public void run() {
        //This is not needed for this stage, will use once MQTT Client from OCI is complete
    }
    
    @Override
    public void shutdown() {
        try {
            client.disconnect();
            client.close();
        } catch (MqttException e) {
            //we want to disconnect so if its already done this is not a problem
            if (!e.getMessage().contains("Client is disconnected")) {
                fail(e.getMessage());
            }
        }
    }
    

}
