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
import org.opencdmp.commonmodels.enums.PluginEntityType;
import org.opencdmp.evaluatorbase.enums.RankType;
import org.opencdmp.evaluatorbase.enums.SuccessStatus;
import org.opencdmp.evaluatorbase.interfaces.BenchmarkConfiguration;
import org.opencdmp.evaluatorbase.interfaces.EvaluatorClient;
import org.opencdmp.evaluatorbase.interfaces.EvaluatorConfiguration;
import org.opencdmp.evaluatorbase.interfaces.SelectionConfiguration;
import org.opencdmp.evaluatorbase.models.misc.*;
import org.opencdmp.commonmodels.models.description.DescriptionModel;
import org.opencdmp.commonmodels.models.plan.PlanModel;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import javax.management.InvalidApplicationException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@RequestScope
public class EvaluatorService implements EvaluatorClient {

    private static final LoggerService logger = new LoggerService(LoggerFactory.getLogger(EvaluatorService.class));
    private final EvaluatorRdaMaDmpServiceProperties evaluatorRdaMaDmpServiceProperties;
    private final ResourceLoader resourceLoader;

    private byte[] logo;

    @Autowired
    public EvaluatorService(ResourceLoader resourceLoader, EvaluatorRdaMaDmpServiceProperties evaluatorRdaMaDmpServiceProperties) {
        this.evaluatorRdaMaDmpServiceProperties = evaluatorRdaMaDmpServiceProperties;
        this.resourceLoader = resourceLoader;
    }


    @Override
    public RankResultModel rankPlan(PlanEvaluationModel plan) throws InvalidApplicationException, IOException, InvalidTypeException {
        if (plan == null || plan.getPlanModel() == null || plan.getPlanModel().getRdaJsonFile() == null ||  plan.getPlanModel().getRdaJsonFile().getFile() == null) throw new MyApplicationException("rda file not found!");

        if (plan.getBenchmarkIds() == null || plan.getBenchmarkIds().isEmpty()) throw new MyApplicationException("benchmark ids are empty!");

        RankResultModel rankModel = new RankResultModel();
        rankModel.setRank(1);

        List<EvaluationResultModel> results = new ArrayList<>();
        for (String benchmarkId: plan.getBenchmarkIds()) {

            BenchmarkConfiguration benchmarkConfiguration = this.evaluatorRdaMaDmpServiceProperties.getAvailableBenchmarks().stream().filter(x -> x.getId().equals(benchmarkId)).findFirst().orElse(null);

            if (benchmarkConfiguration == null) throw new MyApplicationException("not found benchmark config with id " + benchmarkId);

            if (!benchmarkConfiguration.getAppliesTo().contains(PluginEntityType.Plan)) throw new MyApplicationException("benchmark don't apply to plan");


            EvaluationResultModel benchmarkResult = new EvaluationResultModel();
            benchmarkResult.setBenchmarkTitle(benchmarkConfiguration.getLabel());
            benchmarkResult.setRank(1);

            JSONObject rawSchema = null;
            JSONObject planRdaJsonFile = null;
            try {
                planRdaJsonFile = new JSONObject(new String(plan.getPlanModel().getRdaJsonFile().getFile(), StandardCharsets.UTF_8));

                try {
                    Resource resource = this.resourceLoader.getResource(this.evaluatorRdaMaDmpServiceProperties.getRdaSchema());
                    if (!resource.isReadable()) return null;
                    try (InputStream inputStream = resource.getInputStream()) {
                        String jsonTxt = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                        rawSchema = new JSONObject(jsonTxt);
                    }
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            SchemaLoader loader = SchemaLoader.builder()
                    .schemaJson(rawSchema)
                    .draftV7Support()
                    .build();
            Schema schema = loader.load().build();
            if (schema == null) throw new MyApplicationException("json schema not found");

            List<EvaluationResultMetricModel> metrics = new ArrayList<>();
            List<EvaluationResultMessageModel> message = new ArrayList<>();
            EvaluationResultMetricModel metric = new EvaluationResultMetricModel();

            try {
                schema.validate(planRdaJsonFile);
                metric.setRank(1);
            } catch (ValidationException e) {
                metric.setRank(0);
                rankModel.setRank(0);
                benchmarkResult.setRank(0);
                int i = 1;
                for (String error : e.getAllMessages()) {
                    EvaluationResultMessageModel errorMessage = new EvaluationResultMessageModel();
                    errorMessage.setTitle("Error " + i);
                    errorMessage.setMessage(error);
                    i++;

                    message.add(errorMessage);
                    metric.setMessages(message);

                }
            }
            metrics.add(metric);

            benchmarkResult.setMetrics(metrics);
            results.add(benchmarkResult);
            rankModel.setResults(results);
        }
        if (!results.isEmpty()) rankModel.setResults(results);

        return rankModel;
    }

    @Override
    public RankResultModel rankDescription(DescriptionEvaluationModel description) throws InvalidApplicationException, IOException {
        throw new UnsupportedOperationException("rank description not supported");
    }

    @Override
    public EvaluatorConfiguration getConfiguration() {
        EvaluatorConfiguration evaluatorConfiguration = new EvaluatorConfiguration();
        evaluatorConfiguration.setEvaluatorId(evaluatorRdaMaDmpServiceProperties.getEvaluatorId());
        evaluatorConfiguration.setEvaluatorEntityTypes(List.of(PluginEntityType.Plan));
        evaluatorConfiguration.setUseSharedStorage(evaluatorRdaMaDmpServiceProperties.isUseSharedStorage());
        evaluatorConfiguration.setHasLogo(this.evaluatorRdaMaDmpServiceProperties.getHasLogo());
        evaluatorConfiguration.setConfigurationFields(this.evaluatorRdaMaDmpServiceProperties.getConfigurationFields());
        evaluatorConfiguration.setUserConfigurationFields(this.evaluatorRdaMaDmpServiceProperties.getUserConfigurationFields());
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

        evaluatorConfiguration.setAvailableBenchmarks(this.evaluatorRdaMaDmpServiceProperties.getAvailableBenchmarks());
        return evaluatorConfiguration;
    }

    @Override
    public String getLogo() {
        if(this.evaluatorRdaMaDmpServiceProperties != null && this.evaluatorRdaMaDmpServiceProperties.getHasLogo() && this.evaluatorRdaMaDmpServiceProperties.getLogo() != null && !this.evaluatorRdaMaDmpServiceProperties.getLogo().isBlank()){
            if(this.logo == null){
                try{
                    Resource resource = this.resourceLoader.getResource(this.evaluatorRdaMaDmpServiceProperties.getLogo());
                    if(!resource.isReadable()) return null;
                    try(InputStream inputStream = resource.getInputStream()) {
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

