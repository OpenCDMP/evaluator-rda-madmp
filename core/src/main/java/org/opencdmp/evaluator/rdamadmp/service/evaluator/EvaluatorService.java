package org.opencdmp.evaluator.rdamadmp.service.evaluator;

import gr.cite.tools.exception.MyApplicationException;
import gr.cite.tools.logging.LoggerService;
import org.apache.commons.io.IOUtils;
import org.everit.json.schema.Schema;
import org.everit.json.schema.SchemaException;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencdmp.commonmodels.enums.PluginEntityType;
import org.opencdmp.evaluator.rdamadmp.service.storage.FileStorageService;
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
    private final FileStorageService storageService;

    private static volatile Schema rdaSchema;

    private byte[] logo;

    @Autowired
    public EvaluatorService(ResourceLoader resourceLoader, EvaluatorRdaMaDmpServiceProperties evaluatorRdaMaDmpServiceProperties, FileStorageService storageService) {
        this.evaluatorRdaMaDmpServiceProperties = evaluatorRdaMaDmpServiceProperties;
        this.resourceLoader = resourceLoader;
        this.storageService = storageService;
    }


    @Override
    public List<RankResultModel> rankPlan(PlanEvaluationModel plan) {
        if (plan == null || plan.getPlanModel() == null || plan.getPlanModel().getRdaJsonFile() == null) throw new MyApplicationException("rda file not found!");

        byte[] rdaJsonFile;
        if (this.evaluatorRdaMaDmpServiceProperties.isUseSharedStorage()) rdaJsonFile = plan.getPlanModel().getRdaJsonFile().getFileRef() == null ? null : this.storageService.readFile(plan.getPlanModel().getRdaJsonFile().getFileRef());
        else rdaJsonFile = plan.getPlanModel().getRdaJsonFile().getFile();
        if (rdaJsonFile == null) throw new MyApplicationException("rda file not found!");

        if (plan.getBenchmarkIds() == null || plan.getBenchmarkIds().isEmpty()) throw new MyApplicationException("benchmark ids are empty!");

        Schema schema = this.getRdaSchema();

        JSONObject planRdaJsonFile = null;
        String invalidJsonMessage = null;
        try {
            planRdaJsonFile = new JSONObject(new String(rdaJsonFile, StandardCharsets.UTF_8));
        } catch (JSONException e) {
            invalidJsonMessage = "The RDA file is not a valid JSON object: " + e.getMessage();
        }

        RankResultModel rankModel = new RankResultModel();
        rankModel.setRank(1);

        List<EvaluationResultModel> results = new ArrayList<>();
        for (String benchmarkId: plan.getBenchmarkIds()) {

            BenchmarkConfiguration benchmarkConfiguration = this.evaluatorRdaMaDmpServiceProperties.getAvailableBenchmarks() == null ? null : this.evaluatorRdaMaDmpServiceProperties.getAvailableBenchmarks().stream().filter(x -> x.getId().equals(benchmarkId)).findFirst().orElse(null);

            if (benchmarkConfiguration == null) throw new MyApplicationException("not found benchmark config with id " + benchmarkId);

            if (benchmarkConfiguration.getAppliesTo() == null || !benchmarkConfiguration.getAppliesTo().contains(PluginEntityType.Plan)) throw new MyApplicationException("benchmark don't apply to plan");


            EvaluationResultModel benchmarkResult = new EvaluationResultModel();
            benchmarkResult.setBenchmarkTitle(benchmarkConfiguration.getLabel());
            benchmarkResult.setRank(1);

            List<EvaluationResultMetricModel> metrics = new ArrayList<>();
            List<EvaluationResultMessageModel> message = new ArrayList<>();
            EvaluationResultMetricModel metric = new EvaluationResultMetricModel();

            List<String> errors = new ArrayList<>();
            if (planRdaJsonFile == null) {
                errors.add(invalidJsonMessage);
            } else {
                try {
                    schema.validate(planRdaJsonFile);
                } catch (ValidationException e) {
                    errors.addAll(e.getAllMessages());
                }
            }

            if (errors.isEmpty()) {
                metric.setRank(1);
            } else {
                metric.setRank(0);
                rankModel.setRank(0);
                benchmarkResult.setRank(0);
                int i = 1;
                for (String error : errors) {
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

        return List.of(rankModel);
    }

    private Schema getRdaSchema() {
        if (rdaSchema == null) {
            synchronized (EvaluatorService.class) {
                if (rdaSchema == null) {
                    Resource resource = this.resourceLoader.getResource(this.evaluatorRdaMaDmpServiceProperties.getRdaSchema());
                    if (!resource.isReadable()) throw new MyApplicationException("rda schema is not readable: " + this.evaluatorRdaMaDmpServiceProperties.getRdaSchema());
                    try (InputStream inputStream = resource.getInputStream()) {
                        JSONObject rawSchema = new JSONObject(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
                        // everit-json-schema supports up to draft 7; the RDA schema declares 2020-12 but uses no 2020-12-only keywords
                        rdaSchema = SchemaLoader.builder()
                                .schemaJson(rawSchema)
                                .draftV7Support()
                                .build()
                                .load().build();
                    } catch (IOException | JSONException | SchemaException e) {
                        logger.error(e.getMessage(), e);
                        throw new MyApplicationException("rda schema could not be loaded: " + this.evaluatorRdaMaDmpServiceProperties.getRdaSchema());
                    }
                }
            }
        }
        return rdaSchema;
    }

    @Override
    public List<RankResultModel> rankDescription(DescriptionEvaluationModel description) {
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
        evaluatorConfiguration.setAvailableTests(this.evaluatorRdaMaDmpServiceProperties.getAvailableTests());
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

