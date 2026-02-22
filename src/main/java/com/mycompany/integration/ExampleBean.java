package com.mycompany.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 샘플 Java Bean — YAML 라우트에서 다음과 같이 호출합니다:
 * <pre>
 * - to:
 *     uri: "bean:exampleBean?method=process"
 * </pre>
 */
@ApplicationScoped
@Named("exampleBean")
public class ExampleBean {

    private static final Logger log = LoggerFactory.getLogger(ExampleBean.class);

    public String process(String input) {
        log.info("Processing input: {}", input);
        return "Processed: " + input;
    }
}
