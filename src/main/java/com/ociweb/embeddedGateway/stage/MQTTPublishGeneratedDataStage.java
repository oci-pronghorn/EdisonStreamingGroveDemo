package com.ociweb.embeddedGateway.stage;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.ociweb.embeddedGateway.schema.DataGeneratorSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class MQTTPublishGeneratedDataStage extends PronghornStage {
    
    private final Pipe<DataGeneratorSchema> pipe;
    private MqttClient client;
    private final String host;
    private final String clientName;
    private final int qos;
    private final static boolean retained = false;
    private byte[] pPayload;
    private byte[] sPayload;
    
    public MQTTPublishGeneratedDataStage(GraphManager graphManager, Pipe<DataGeneratorSchema> pipe, String host, String clientName, int qos) {
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
            pPayload = new byte[4];
            sPayload = new byte[4];
        } catch (MqttException e) {
          throw new RuntimeException(e);
        }
    }
    
    
    @Override
    public void run() {
        try {
        
            while (PipeReader.tryReadFragment(pipe)) {
    
                switch (PipeReader.getMsgIdx(pipe)) {
                    case DataGeneratorSchema.MSG_SINEDATA_10:
                        publish(client, pPayload, qos, retained, PipeReader.readInt(pipe, DataGeneratorSchema.MSG_SINEDATA_10_FIELD_VALUE_11),"source/1");
                        break;
                    case DataGeneratorSchema.MSG_RANDOMDATA_20:
                        publish(client, sPayload, qos, retained, PipeReader.readInt(pipe, DataGeneratorSchema.MSG_RANDOMDATA_20_FIELD_VALUE_21),"source/2");
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
                throw new RuntimeException(e);
            }
        }
    }
}
