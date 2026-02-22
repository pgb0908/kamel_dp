package com.mycompany.integration;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class ExampleBeanTest {

    @Inject
    ExampleBean exampleBean;

    @Test
    void testProcess() {
        String result = exampleBean.process("hello");
        assertEquals("Processed: hello", result);
    }
}
