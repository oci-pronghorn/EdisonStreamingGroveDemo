package com.ociweb.embeddedGateway.stage;

import java.lang.management.ManagementFactory;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import com.ociweb.embeddedGateway.schema.SystemSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class CPUMonitorStage extends PronghornStage {

    public static String[] PROCESS_CPU_LOAD = new String[]{ "ProcessCpuLoad" };
    public static String[] SYSTEM_CPU_LOAD = new String[]{ "SystemCpuLoad" };
    public static MBeanServer mbs    = ManagementFactory.getPlatformMBeanServer();
    public static ObjectName os      = null;
    
    static{
        try {
            CPUMonitorStage.os = ObjectName.getInstance("java.lang:type=OperatingSystem");
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        } catch (NullPointerException e) {
            throw new RuntimeException(e);
        }
    }
    
    private final Pipe<SystemSchema> output;
    
    public CPUMonitorStage(GraphManager graphManager, Pipe<SystemSchema> output) {
        super(graphManager, NONE, output);
        this.output = output;
        GraphManager.addNota(graphManager, GraphManager.SCHEDULE_RATE, 500*1000*1000, this);
        GraphManager.addNota(graphManager, GraphManager.PRODUCER, GraphManager.PRODUCER, this);     
    }
        
    

    @Override
    public void run() {
        try {
            long now;
            int load;
            
            if (PipeWriter.tryWriteFragment(output, SystemSchema.MSG_PROCESSCPULOAD_10)) {
                now  = System.currentTimeMillis();
                load = (int)(100000000*CPUMonitorStage.getProcessCpuLoad(CPUMonitorStage.PROCESS_CPU_LOAD));
                PipeWriter.writeInt(output, SystemSchema.MSG_PROCESSCPULOAD_10_FIELD_LOAD_11,  load);
                PipeWriter.writeLong(output, SystemSchema.MSG_PROCESSCPULOAD_10_FIELD_TIME_1, now);
                PipeWriter.publishWrites(output);
            }
            
            if (PipeWriter.tryWriteFragment(output, SystemSchema.MSG_SYSTEMCPULOAD_20)) {
                now  = System.currentTimeMillis();
                load = (int)(100000000*CPUMonitorStage.getProcessCpuLoad(CPUMonitorStage.SYSTEM_CPU_LOAD));
                PipeWriter.writeInt(output, SystemSchema.MSG_SYSTEMCPULOAD_20_FIELD_LOAD_21,  load);
                PipeWriter.writeLong(output, SystemSchema.MSG_SYSTEMCPULOAD_20_FIELD_TIME_1, now);
                PipeWriter.publishWrites(output);
            }
            
       } catch (InstanceNotFoundException e1) {
       } catch (ReflectionException e1) {
       }
    }

    public static double getProcessCpuLoad(String[] attrib) throws ReflectionException, InstanceNotFoundException {
    
        if (null==os) {
            return Double.NaN; ///TODO: URGENT MUST, remove all usage of NaN it prevents optimization of this code.
        }
        
        AttributeList list = mbs.getAttributes(os, attrib);
    
        if (list.isEmpty()) {
            return Double.NaN;
        }
    
        Attribute att = (Attribute)list.get(0);
        Double value  = (Double)att.getValue();
    
        if (value == -1.0) {
            return Double.NaN;  // usually takes a couple of seconds before we get real values
        }
    
        return value;
    }

}
