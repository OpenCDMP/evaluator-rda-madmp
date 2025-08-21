package org.opencdmp.evaluator.rdamadmp.web.controller;

import gr.cite.tools.auditing.AuditService;
import gr.cite.tools.logging.LoggerService;
import gr.cite.tools.logging.MapLogEntry;
import org.opencdmp.commonmodels.models.description.DescriptionModel;
import org.opencdmp.commonmodels.models.plan.PlanModel;
import org.opencdmp.evaluator.rdamadmp.audit.AuditableAction;
import org.opencdmp.evaluatorbase.interfaces.EvaluatorClient;
import org.opencdmp.evaluatorbase.interfaces.EvaluatorConfiguration;
import org.opencdmp.evaluatorbase.models.misc.DescriptionEvaluationModel;
import org.opencdmp.evaluatorbase.models.misc.PlanEvaluationModel;
import org.opencdmp.evaluatorbase.models.misc.RankResultModel;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.AbstractMap;
import java.util.Map;

@RestController
@RequestMapping("/api/evaluator")
public class EvaluatorController implements org.opencdmp.evaluatorbase.interfaces.EvaluatorController {
    private static final LoggerService logger = new LoggerService(LoggerFactory.getLogger(EvaluatorController.class));

    private final EvaluatorClient evaluatorClient;

    private final AuditService auditService;

    @Autowired
    public EvaluatorController(EvaluatorClient evaluatorClient, AuditService auditService) {
        this.evaluatorClient = evaluatorClient;
        this.auditService = auditService;
    }

    @Override
    public RankResultModel rankPlan(@RequestBody PlanEvaluationModel planModel) throws Exception {

         logger.debug(new MapLogEntry("rankPlan" + PlanModel.class.getSimpleName()).And("planModel", planModel));

        RankResultModel model  = evaluatorClient.rankPlan(planModel);

        this.auditService.track(AuditableAction.Evaluator_RankPlan, Map.ofEntries(
                new AbstractMap.SimpleEntry<String, Object>("planModel", planModel)
        ));

        return model;
    }

    @Override
    public RankResultModel rankDescription(@RequestBody DescriptionEvaluationModel descriptionModel) throws Exception {

        logger.debug(new MapLogEntry("rankDescription " + DescriptionModel.class.getSimpleName()).And("descriptionModel", descriptionModel));

        RankResultModel model = evaluatorClient.rankDescription(descriptionModel);

        this.auditService.track(AuditableAction.Evaluator_RankDescription, Map.ofEntries(
                new AbstractMap.SimpleEntry<String, Object>("descriptionModel", descriptionModel)
        ));

        return model;
    }

    @Override
    public EvaluatorConfiguration getConfiguration() {

        logger.debug(new MapLogEntry("getConfiguration"));

        EvaluatorConfiguration model = evaluatorClient.getConfiguration();

        this.auditService.track(AuditableAction.Evaluator_GetConfiguration);

        return model;
    }

    @Override
    public String getLogo() {

        logger.debug(new MapLogEntry("getLogo " + PlanModel.class.getSimpleName()));

        String logo = evaluatorClient.getLogo();

        this.auditService.track(AuditableAction.Evaluator_GetLogo);

        return logo;
    }
}
