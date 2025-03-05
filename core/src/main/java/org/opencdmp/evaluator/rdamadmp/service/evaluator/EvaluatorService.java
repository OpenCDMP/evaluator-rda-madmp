package org.opencdmp.evaluator.rdamadmp.service.evaluator;


import com.sun.jdi.InvalidTypeException;
import gr.cite.tools.exception.MyApplicationException;
import gr.cite.tools.logging.LoggerService;
import org.apache.commons.io.IOUtils;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencdmp.evaluatorbase.enums.EvaluatorEntityType;
import org.opencdmp.evaluatorbase.enums.RankType;
import org.opencdmp.evaluatorbase.enums.SuccessStatus;
import org.opencdmp.evaluatorbase.interfaces.EvaluatorClient;
import org.opencdmp.evaluatorbase.interfaces.EvaluatorConfiguration;
import org.opencdmp.evaluatorbase.interfaces.SelectionConfiguration;
import org.opencdmp.evaluatorbase.models.misc.RankModel;
import org.opencdmp.evaluatorbase.models.misc.RankConfig;
import org.opencdmp.commonmodels.models.description.DescriptionModel;
import org.opencdmp.commonmodels.models.plan.PlanModel;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.util.ResourceUtils;

import javax.management.InvalidApplicationException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@RequestScope
public class EvaluatorService implements EvaluatorClient {

    private static final LoggerService logger = new LoggerService(LoggerFactory.getLogger(EvaluatorService.class));
    private final EvaluatorRdaMaDmpServiceProperties evaluatorRdaMaDmpServiceProperties;

    private byte[] logo;

    @Autowired
    public EvaluatorService(EvaluatorRdaMaDmpServiceProperties evaluatorRdaMaDmpServiceProperties) {
        this.evaluatorRdaMaDmpServiceProperties = evaluatorRdaMaDmpServiceProperties;
    }


    @Override
    public RankModel rankPlan(PlanModel plan) throws InvalidApplicationException, IOException, InvalidTypeException {
        if (plan == null || plan.getRdaJsonFile() == null ||  plan.getRdaJsonFile().getFile() == null) throw new MyApplicationException("rda file not found!");

        RankModel rankModel = new RankModel();
        Map<String, String> messages = new HashMap<>();
        rankModel.setRank(1);

        JSONObject rawSchema = null;
        JSONObject planRdaJsonFile = null;
        try {
            planRdaJsonFile = new JSONObject(new String(plan.getRdaJsonFile().getFile(), StandardCharsets.UTF_8));

            InputStream is = new FileInputStream(ResourceUtils.getFile(this.evaluatorRdaMaDmpServiceProperties.getRdaSchema()));
            String jsonTxt = IOUtils.toString(is, StandardCharsets.UTF_8);
            rawSchema = new JSONObject(jsonTxt);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        SchemaLoader loader = SchemaLoader.builder()
                .schemaJson(rawSchema)
                .draftV7Support()
                .build();
        Schema schema = loader.load().build();
        if (schema == null) throw new MyApplicationException("json schema not found");

        try {
            schema.validate(planRdaJsonFile);
        } catch (ValidationException e) {
            rankModel.setRank(0);
            int i = 0;
            for (String error: e.getAllMessages()) {
                messages.put("Error " + (i+1), error);
                i++;
            }
        }

        if (!messages.isEmpty()) rankModel.setMessages(messages);
        return rankModel;
    }

    @Override
    public RankModel rankDescription(DescriptionModel description) throws InvalidApplicationException, IOException {
        throw new UnsupportedOperationException("rank description not supported");
    }

    @Override
    public EvaluatorConfiguration getConfiguration() {
        EvaluatorConfiguration evaluatorConfiguration = new EvaluatorConfiguration();
        evaluatorConfiguration.setEvaluatorId(evaluatorRdaMaDmpServiceProperties.getEvaluatorId());
        evaluatorConfiguration.setEvaluatorEntityTypes(Arrays.asList(EvaluatorEntityType.Plan));
        evaluatorConfiguration.setUseSharedStorage(evaluatorRdaMaDmpServiceProperties.isUseSharedStorage());
        evaluatorConfiguration.setHasLogo(this.evaluatorRdaMaDmpServiceProperties.getHasLogo());
        evaluatorConfiguration.setRankConfig(new RankConfig());
        evaluatorConfiguration.getRankConfig().setRankType(RankType.Selection);
        evaluatorConfiguration.getRankConfig().setSelectionConfiguration(new SelectionConfiguration());
        SelectionConfiguration.ValueSet valueSetSuccess = new SelectionConfiguration.ValueSet();
        valueSetSuccess.setKey(1);
        valueSetSuccess.setSuccessStatus(SuccessStatus.Pass);

        SelectionConfiguration.ValueSet valueSetFail = new SelectionConfiguration.ValueSet();
        valueSetFail.setKey(0);
        valueSetFail.setSuccessStatus(SuccessStatus.Fail);
        evaluatorConfiguration.getRankConfig().getSelectionConfiguration().setValueSetList(Arrays.asList(valueSetSuccess, valueSetFail));
        return evaluatorConfiguration;
    }

    @Override
    public String getLogo() {
        if(this.evaluatorRdaMaDmpServiceProperties != null && this.evaluatorRdaMaDmpServiceProperties.getHasLogo() && this.evaluatorRdaMaDmpServiceProperties.getLogo() != null && !this.evaluatorRdaMaDmpServiceProperties.getLogo().isBlank()){
            if(this.logo == null){
                try{
                    java.io.File logoFile = ResourceUtils.getFile(this.evaluatorRdaMaDmpServiceProperties.getLogo());
                    if(!logoFile.exists()) return null;
                    try(InputStream inputStream = new FileInputStream(logoFile)) {
                        this.logo = inputStream.readAllBytes();
                    }
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }
            return (this.logo != null && this.logo.length != 0) ? Base64.getEncoder().encodeToString(this.logo) : null;
        }
        return null;
    }
}

