package org.opencdmp.evaluator.rdamadmp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "org.opencdmp.evaluator.rdamadmp",
        "gr.cite.tools",
        "gr.cite.commons",
})
public class EvaluatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvaluatorApplication.class, args);
    }
}
