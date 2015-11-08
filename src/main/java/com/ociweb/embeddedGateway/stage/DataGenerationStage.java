package com.ociweb.embeddedGateway.stage;

import java.util.Random;

import com.ociweb.embeddedGateway.schema.DataGeneratorSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class DataGenerationStage extends PronghornStage {

    private final static int range = Integer.MAX_VALUE-10;
    private final static double factor = .7d;
    private long x = 0;
    private final Random rand = new Random(42);
    private final Pipe<DataGeneratorSchema> output;
    
    
    public DataGenerationStage(GraphManager graphManager, Pipe<DataGeneratorSchema> output) {
        super(graphManager, NONE, output);
        this.output = output;
        GraphManager.addNota(graphManager, GraphManager.PRODUCER, GraphManager.PRODUCER, this);
    }

    @Override
    public void run() {
                
        x++;
        
        if (PipeWriter.tryWriteFragment(output, DataGeneratorSchema.MSG_SINEDATA_10)) {
           
            int sineValue = range+(int)(range*Math.sin(factor*(float)x/(120d)));       
            PipeWriter.writeInt(output, DataGeneratorSchema.MSG_SINEDATA_10_FIELD_VALUE_11, sineValue);            
            PipeWriter.publishWrites(output);            
        }
        
        if (PipeWriter.tryWriteFragment(output, DataGeneratorSchema.MSG_RANDOMDATA_20)) {
            
            int randomValue = Math.abs(rand.nextInt());      
            PipeWriter.writeInt(output, DataGeneratorSchema.MSG_RANDOMDATA_20_FIELD_VALUE_21, randomValue);            
            PipeWriter.publishWrites(output);            
        }
         
    }
 
    
    

}
