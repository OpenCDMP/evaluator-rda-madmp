package org.opencdmp.evaluator.rdamadmp.service.evaluator;

import org.opencdmp.commonmodels.models.ConfigurationField;
import org.opencdmp.evaluatorbase.interfaces.BenchmarkConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "rda-madmp")
public class EvaluatorRdaMaDmpServiceProperties {
	private String evaluatorId;
	private boolean useSharedStorage;
	private String logo;
	private boolean hasLogo;
	private String rdaSchema;
	private List<ConfigurationField> configurationFields;
	private List<ConfigurationField> userConfigurationFields;
	private List<BenchmarkConfiguration> availableBenchmarks;

	public String getEvaluatorId() {
		return evaluatorId;
	}

	public void setEvaluatorId(String evaluatorId) {
		this.evaluatorId = evaluatorId;
	}

	public boolean isUseSharedStorage() {
		return useSharedStorage;
	}

	public void setUseSharedStorage(boolean useSharedStorage) {
		this.useSharedStorage = useSharedStorage;
	}

	public String getLogo() {
		return logo;
	}

	public void setLogo(String logo) {
		this.logo = logo;
	}

	public boolean getHasLogo() {
		return hasLogo;
	}

	public void setHasLogo(boolean hasLogo) {
		this.hasLogo = hasLogo;
	}

	public String getRdaSchema() {
		return rdaSchema;
	}

	public void setRdaSchema(String rdaSchema) {
		this.rdaSchema = rdaSchema;
	}

	public List<ConfigurationField> getConfigurationFields() {
		return configurationFields;
	}

	public void setConfigurationFields(List<ConfigurationField> configurationFields) {
		this.configurationFields = configurationFields;
	}

	public List<ConfigurationField> getUserConfigurationFields() {
		return userConfigurationFields;
	}

	public void setUserConfigurationFields(List<ConfigurationField> userConfigurationFields) {
		this.userConfigurationFields = userConfigurationFields;
	}

	public List<BenchmarkConfiguration> getAvailableBenchmarks() {

		return availableBenchmarks;
	}

	public void setAvailableBenchmarks(List<BenchmarkConfiguration> availableBenchmarks) {
		this.availableBenchmarks = availableBenchmarks;
	}
}
