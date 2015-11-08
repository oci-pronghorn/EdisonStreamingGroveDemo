package com.ociweb.device;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ociweb.embeddedGateway.schema.DataGeneratorSchema;
import com.ociweb.embeddedGateway.schema.SystemSchema;
import com.ociweb.pronghorn.pipe.util.build.FROMValidation;

public class TestSchemas {

    @Test
    public void systemSchemaTest() {
        assertTrue(FROMValidation.testForMatchingFROMs("/SystemSchema.xml", "FROM", SystemSchema.FROM));
    }
    
    @Test
    public void systemSchemaFieldsTest() {        
        assertTrue(FROMValidation.testForMatchingLocators(SystemSchema.instance));
    }

    @Test
    public void dataGeneratorSchemaTest() {
        assertTrue(FROMValidation.testForMatchingFROMs("/DataGenerationSchema.xml", "FROM", DataGeneratorSchema.FROM));
    }
    
    @Test
    public void dataGeneratorSchemaFieldsTest() {        
        assertTrue(FROMValidation.testForMatchingLocators(DataGeneratorSchema.instance));
    }    
    
    
}
