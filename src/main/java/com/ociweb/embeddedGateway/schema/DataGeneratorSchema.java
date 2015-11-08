package com.ociweb.embeddedGateway.schema;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.MessageSchema;

public class DataGeneratorSchema extends MessageSchema {

    public final static FieldReferenceOffsetManager FROM = new FieldReferenceOffsetManager(
            new int[]{0xc0400002,0x80000000,0xc0200002,0xc0400002,0x80000001,0xc0200002},
            (short)0,
            new String[]{"SineData","value",null,"RandomData","value",null},
            new long[]{10, 11, 0, 20, 21, 0},
            new String[]{"global",null,null,"global",null,null},
            "DataGenerationSchema.xml");
    
    public static DataGeneratorSchema instance = new DataGeneratorSchema();
    
    public static final int MSG_SINEDATA_10 = 0x0;
    public static final int MSG_SINEDATA_10_FIELD_VALUE_11 = 0x1;
    public static final int MSG_RANDOMDATA_20 = 0x3;
    public static final int MSG_RANDOMDATA_20_FIELD_VALUE_21 = 0x1;
    
    
    protected DataGeneratorSchema(FieldReferenceOffsetManager from) {
        super(from);
    }    
    
    private DataGeneratorSchema() {
        super(FROM);
    }
    
}
