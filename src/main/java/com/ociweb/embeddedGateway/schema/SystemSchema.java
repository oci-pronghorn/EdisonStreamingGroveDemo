package com.ociweb.embeddedGateway.schema;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.MessageSchema;

public class SystemSchema extends MessageSchema {

    public final static FieldReferenceOffsetManager FROM = new FieldReferenceOffsetManager(
            new int[]{0xc0400003,0x80000000,0x98000000,0xc0200003,0xc0400003,0x80000001,0x98000000,0xc0200003},
            (short)0,
            new String[]{"ProcessCpuLoad","load","time",null,"SystemCpuLoad","load","time",null},
            new long[]{10, 11, 1, 0, 20, 21, 1, 0},
            new String[]{"global",null,null,null,"global",null,null,null},
            "SystemSchema.xml");
    
    public static final int MSG_PROCESSCPULOAD_10 = 0x00000000;
    public static final int MSG_PROCESSCPULOAD_10_FIELD_LOAD_11 = 0x00000001;
    public static final int MSG_PROCESSCPULOAD_10_FIELD_TIME_1 = 0x00C00002;
    public static final int MSG_SYSTEMCPULOAD_20 = 0x00000004;
    public static final int MSG_SYSTEMCPULOAD_20_FIELD_LOAD_21 = 0x00000001;
    public static final int MSG_SYSTEMCPULOAD_20_FIELD_TIME_1 = 0x00C00002;
    
    public static SystemSchema instance = new SystemSchema();
    
    private SystemSchema() {
        super(FROM);
    }

    
    
    
}
