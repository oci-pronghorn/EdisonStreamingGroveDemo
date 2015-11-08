package com.ociweb.embeddedGateway.stage;

import static org.junit.Assert.fail;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.ociweb.device.grove.schema.GroveResponseSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class MQTTPublishSensorDataStage extends PronghornStage {
    
    private final Pipe<GroveResponseSchema> pipe;
    private MqttClient client;
    private final String host;
    private final String clientName;
    private final int qos;
    private final static boolean retained = false;
    private byte[] payload0;
    private byte[] payload1;
    private byte[] payload2;
    private byte[] payload3;
    private byte[] payload4;
    private byte[] payload5;
    private byte[] payload6;
    
    
    public MQTTPublishSensorDataStage(GraphManager graphManager, Pipe<GroveResponseSchema> pipe, String host, String clientName, int qos) {
        super(graphManager, pipe, NONE);
        this.pipe = pipe;
        this.host = host;
        this.clientName = clientName;
        this.qos = qos;
    }

    @Override
    public void startup() {
        try {
            client = new MqttClient(host, clientName, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            client.connect(options );
            
            payload0 = new byte[4];
            payload1 = new byte[4];
            payload2 = new byte[4];
            payload3 = new byte[4];
            payload4 = new byte[4];
            payload5 = new byte[4];
            payload6 = new byte[4];
            
        } catch (MqttException e) {
          throw new RuntimeException(e);
        }
    }
    
    
    @Override
    public void run() {
        try {
            int lastButtonValue = 0;
            
            while (PipeReader.tryReadFragment(pipe)) {    
                int value = PipeReader.getMsgIdx(pipe);
                switch (value) {
                    case GroveResponseSchema.MSG_BUTTON_50:
                        int newButtonValue =  PipeReader.readInt(pipe, GroveResponseSchema.MSG_BUTTON_50_FIELD_VALUE_52);
                        publish(client, payload0, qos, retained, 20 * lastButtonValue,"source/9"); //to capture square change on graph must publish previous value.
                        publish(client, payload1, qos, retained, 20 * newButtonValue,"source/9");
                        lastButtonValue = newButtonValue;
                        break;
                    case GroveResponseSchema.MSG_LIGHT_30:
                        publish(client, payload2, qos, retained, PipeReader.readInt(pipe, GroveResponseSchema.MSG_LIGHT_30_FIELD_VALUE_32),"source/6");
                        break;
                    case GroveResponseSchema.MSG_MOISTURE_40:
                        publish(client, payload3, qos, retained, PipeReader.readInt(pipe, GroveResponseSchema.MSG_MOISTURE_40_FIELD_VALUE_42),"source/8");
                        break;
                    case GroveResponseSchema.MSG_MOTION_60:
                        //TODO: may be better with moving average.
                        publish(client, payload4, qos, retained, 30 * PipeReader.readInt(pipe, GroveResponseSchema.MSG_MOTION_60_FIELD_VALUE_62),"source/5");
                        break;
                    case GroveResponseSchema.MSG_ROTARY_70:
                        publish(client, payload5, qos, retained,Math.max(0, PipeReader.readInt(pipe, GroveResponseSchema.MSG_ROTARY_70_FIELD_VALUE_72)),"source/10");
                        break;
                    case GroveResponseSchema.MSG_UV_20:
                        publish(client, payload6, qos, retained, PipeReader.readInt(pipe, GroveResponseSchema.MSG_UV_20_FIELD_VALUE_22),"source/7");
                        break;
                    default:
                        requestShutdown();
                }
                PipeReader.releaseReadLock(pipe);
            }
        } catch (MqttPersistenceException e) {
           throw new RuntimeException(e);
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }
    
    public void publish(MqttClient client, byte[] payload, int qos, boolean retained, int value, String topic)
            throws MqttException, MqttPersistenceException {
         payload[3] = (byte)(0xFF&(value>>24));
         payload[2] = (byte)(0xFF&(value>>16));
         payload[1] = (byte)(0xFF&(value>> 8));
         payload[0] = (byte)(0xFF&(value>> 0));
        
        client.publish(topic, payload, qos, retained );
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
