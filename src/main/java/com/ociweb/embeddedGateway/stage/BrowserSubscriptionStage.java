package com.ociweb.embeddedGateway.stage;

import com.ociweb.pronghorn.adapter.netty.WebSocketSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.RawDataSchema;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class BrowserSubscriptionStage extends PronghornStage {
    //TODO: Eventually stop sub from broker when no one is listening, for now just drop incoming messages.

    private Pipe<WebSocketSchema> fromNetwork; 
    private Pipe<WebSocketSchema> toNetwork;
    private Pipe<RawDataSchema>[] fromMQTT;
    private final int msgSizeMQTT;
    
    private final int totalSubsciptions = 12;
    private boolean[] subscriptions = new boolean[totalSubsciptions];
        
    
    public BrowserSubscriptionStage(GraphManager gm, Pipe<WebSocketSchema> input, Pipe<WebSocketSchema> output, Pipe<RawDataSchema>[] fromMQTT) {
        super(gm,  pipeJoin(input, fromMQTT), output);
        this.fromNetwork = input;
        this.toNetwork = output;
        this.fromMQTT = fromMQTT;
        
        this.msgSizeMQTT = Pipe.from(fromMQTT[0]).fragDataSize[0];
    }

    public static Pipe<?>[] pipeJoin(Pipe<?> input, Pipe<?>[] fromMQTT) {
        Pipe<?>[] result = (Pipe<?>[])new Pipe[fromMQTT.length+1];
        result[0]=input;
        System.arraycopy(fromMQTT, 0, result, 1, fromMQTT.length);        
        return result;
    }

    @Override
    public void run() {
        
        if (Pipe.hasContentToRead(fromNetwork, WebSocketSchema.startSubPublishIdSize)) {
                       
            int msgIdx = Pipe.takeMsgIdx(fromNetwork);
            
            if (msgIdx == WebSocketSchema.startSubPublishIdx) {              
                int subIdx = Pipe.takeValue(fromNetwork);
                subscriptions[subIdx] = true;
                Pipe.confirmLowLevelRead(fromNetwork, WebSocketSchema.startSubPublishIdSize);
                
                                
            } else if (msgIdx == WebSocketSchema.stopSubPublishIdx) {
               
                subscriptions[Pipe.takeValue(fromNetwork)] = false;
                Pipe.confirmLowLevelRead(fromNetwork, WebSocketSchema.stopSubPublishIdSize);
                
            } else {
                //else just reflect the message back
              //  System.out.println(fromNetwork);
                
              if (msgIdx==WebSocketSchema.forSingleChannelMessageIdx) {  
                  
                  long connectionId = Pipe.takeLong(fromNetwork);
                  int meta       = Pipe.takeRingByteMetaData(fromNetwork);
                  int len        = Pipe.takeRingByteLen(fromNetwork);
                  
                  
                  if (Pipe.hasRoomForWrite(toNetwork, WebSocketSchema.forSingleChannelMessageIdx)) {
 
                      int mask       = Pipe.blobMask(fromNetwork);
                      byte[] backing = Pipe.byteBackingArray(meta, fromNetwork);
                      int offset     = Pipe.bytePosition(meta, fromNetwork, len);
                      
                      Pipe.addMsgIdx(toNetwork, WebSocketSchema.forSingleChannelMessageIdx);                              
                      Pipe.addLongValue(connectionId, toNetwork);                        
                      //assumes nothing about the data
                      Pipe.addByteArrayWithMask(toNetwork, mask, len, backing, offset);                              
                      Pipe.confirmLowLevelWrite(toNetwork, WebSocketSchema.forSingleChannelMessageIdx);
                      Pipe.publishWrites(toNetwork);  
                  }
                  
                  Pipe.confirmLowLevelRead(fromNetwork, WebSocketSchema.forSingleChannelMessageSize);
                  
              } else {
                  throw new UnsupportedOperationException();
              }
              
            }
          
            Pipe.releaseReadLock(fromNetwork);
            
        }        
               
        byte j = (byte)(fromMQTT.length);
        while (--j>=0) {
        
            while (Pipe.hasContentToRead(fromMQTT[j])) {
                                
                
                //take and dump all message if there is no one to listen.
                long now = System.currentTimeMillis();
                int msgId = Pipe.takeMsgIdx(fromMQTT[j]);
                int meta = Pipe.takeRingByteMetaData(fromMQTT[j]);
                int len = Pipe.takeRingByteLen(fromMQTT[j]);
                
                byte subscription = (byte)(j+1);
                
                if (subscriptions[subscription] && Pipe.hasRoomForWrite(toNetwork, WebSocketSchema.forSubscribersMessageSize)) {
                                    
                    Pipe.addMsgIdx(toNetwork, WebSocketSchema.forSubscribersMessageIdx);  //Subscription message              
                    Pipe.addIntValue(subscription, toNetwork);
                                    
                    //send payload that will be sent over websocket
                    //[sub id] [ time ] [ time ] [ value]
                                   
                    byte[] targetBuffer = Pipe.blob(toNetwork);
                    int writePos = Pipe.bytesWorkingHeadPosition(toNetwork);
                    int writePosMask = toNetwork.byteMask;
                    
                    Pipe.addBytePosAndLen(toNetwork, writePos, 16);
                    
                    targetBuffer[writePosMask & writePos++] = subscription;
                    targetBuffer[writePosMask & writePos++] = 0;
                    targetBuffer[writePosMask & writePos++] = 0;
                    targetBuffer[writePosMask & writePos++] = 0;
                    
                    targetBuffer[writePosMask & writePos++] =(byte)(0xFF&(now>>32));
                    targetBuffer[writePosMask & writePos++] =(byte)(0xFF&(now>>40));
                    targetBuffer[writePosMask & writePos++] =(byte)(0xFF&(now>>48));
                    targetBuffer[writePosMask & writePos++] =(byte)(0xFF&(now>>56));
                    
                    targetBuffer[writePosMask & writePos++] =(byte)(0xFF&(now>> 0));
                    targetBuffer[writePosMask & writePos++] =(byte)(0xFF&(now>> 8));
                    targetBuffer[writePosMask & writePos++] =(byte)(0xFF&(now>>16));
                    targetBuffer[writePosMask & writePos++] =(byte)(0xFF&(now>>24));
                            
                    int pos = Pipe.bytePosition(meta, fromMQTT[j], len);
                    byte[] payload = Pipe.byteBackingArray(meta, fromMQTT[j]);
                    int mask = Pipe.blobMask(fromMQTT[j]);
    
                    targetBuffer[writePosMask & writePos++] = payload[mask&pos++];
                    targetBuffer[writePosMask & writePos++] = payload[mask&pos++];
                    targetBuffer[writePosMask & writePos++] = payload[mask&pos++];
                    targetBuffer[writePosMask & writePos++] = payload[mask&pos++];
                   
                    Pipe.addAndGetBytesWorkingHeadPosition(toNetwork, 16);
                              
                    Pipe.confirmLowLevelWrite(toNetwork, WebSocketSchema.forSubscribersMessageSize);
                    
                    Pipe.publishWrites(toNetwork);                
                    
                    
                } else {
                    //dropped data.
                }
                
                Pipe.releaseReadLock(fromMQTT[j]);
                Pipe.confirmLowLevelRead(fromMQTT[j], msgSizeMQTT);
                            
            }
        }
        
        
        
    }

  
}
