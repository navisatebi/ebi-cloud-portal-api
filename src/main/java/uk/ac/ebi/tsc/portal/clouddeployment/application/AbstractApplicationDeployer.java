package uk.ac.ebi.tsc.portal.clouddeployment.application;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import uk.ac.ebi.tsc.portal.api.application.repo.Application;
import uk.ac.ebi.tsc.portal.api.application.repo.ApplicationOutput;
import uk.ac.ebi.tsc.portal.api.application.service.ApplicationService;
import uk.ac.ebi.tsc.portal.api.cloudproviderparameters.repo.CloudProviderParamsCopy;
import uk.ac.ebi.tsc.portal.api.cloudproviderparameters.service.CloudProviderParamsCopyService;
import uk.ac.ebi.tsc.portal.api.configuration.repo.Configuration;
import uk.ac.ebi.tsc.portal.api.configuration.service.ConfigDeploymentParamsCopyService;
import uk.ac.ebi.tsc.portal.api.deployment.repo.Deployment;
import uk.ac.ebi.tsc.portal.api.deployment.repo.DeploymentAssignedInput;
import uk.ac.ebi.tsc.portal.api.deployment.repo.DeploymentAssignedParameter;
import uk.ac.ebi.tsc.portal.api.deployment.repo.DeploymentAttachedVolume;
import uk.ac.ebi.tsc.portal.api.deployment.repo.DeploymentConfiguration;
import uk.ac.ebi.tsc.portal.api.deployment.repo.DeploymentGeneratedOutput;
import uk.ac.ebi.tsc.portal.api.deployment.repo.DeploymentStatusEnum;
import uk.ac.ebi.tsc.portal.api.deployment.service.DeploymentSecretService;
import uk.ac.ebi.tsc.portal.api.deployment.service.DeploymentService;
import uk.ac.ebi.tsc.portal.clouddeployment.exceptions.ApplicationDeployerException;
import uk.ac.ebi.tsc.portal.clouddeployment.model.ErrorFromTerraformOutput;
import uk.ac.ebi.tsc.portal.clouddeployment.model.StateFromTerraformOutput;
import uk.ac.ebi.tsc.portal.clouddeployment.model.terraform.TerraformState;
import uk.ac.ebi.tsc.portal.usage.deployment.model.DeploymentDocument;
import uk.ac.ebi.tsc.portal.usage.deployment.service.DeploymentIndexService;

/**
 * Created by jdianes on 01/10/2018.
 */
@Component
public abstract class AbstractApplicationDeployer {

    private static final Logger logger = LoggerFactory.getLogger(AbstractApplicationDeployer.class);

    protected final DeploymentService deploymentService;
    protected final ApplicationService applicationService;
    protected final CloudProviderParamsCopyService cloudProviderParametersCopyService;
    protected final ConfigDeploymentParamsCopyService configDeploymentParamsCopyService;
    protected final DeploymentSecretService secretService;

    @Autowired
    public AbstractApplicationDeployer(DeploymentService deploymentService,
    								   ApplicationService applicationService,
                                       ConfigDeploymentParamsCopyService configDeploymentParamsCopyService,
                                       DeploymentSecretService secretService,
                                       CloudProviderParamsCopyService cloudProviderParametersCopyService) {
        this.deploymentService = deploymentService;
        this.applicationService = applicationService;
        this.cloudProviderParametersCopyService = cloudProviderParametersCopyService;
        this.configDeploymentParamsCopyService = configDeploymentParamsCopyService;
        this.secretService = secretService;

    }
    public abstract void deploy(String userEmail,
                                Application theApplication,
                                String reference,
                                String cloudProviderPath,
                                Map<String, String> inputAssignments,
                                Map<String, String> parameterAssignments,
                                Map<String, String> volumeAttachments,
                                Map<String, String> configurationParameters,
                                CloudProviderParamsCopy cloudProviderParametersCopy,
                                Configuration configuration,
                                java.sql.Timestamp startTime,
                                String userSshKey,
                                String baseUrl                    
                               )
                               throws IOException, ApplicationDeployerException, NoSuchAlgorithmException, NoSuchProviderException;

    public abstract StateFromTerraformOutput state(String repoPath,
                                          String reference,
                                          String cloudProviderPath,
                                          Map<String, String> outputs,
                                          Configuration configuration,
                                          String userSshKey) throws IOException, ApplicationDeployerException;

    public abstract void destroy(String repoPath, String reference,
                 String cloudProviderPath,
                 Collection<DeploymentAssignedInput> inputAssignments,
                 Collection<DeploymentAssignedParameter> parameterAssignments,
                 Collection<DeploymentAttachedVolume> volumeAttachments,
                 DeploymentConfiguration deploymentConfiguration,
                 CloudProviderParamsCopy cloudProviderParametersCopy) throws IOException,
            ApplicationDeployerException;


    protected void updateDeploymentStatus(DeploymentIndexService deploymentIndexService,
                                          Deployment theDeployment,
                                        DeploymentStatusEnum status, String cause, TerraformState terraformState, String terraformOutput,
                                        java.sql.Timestamp startTime) {
        // update status
        theDeployment.getDeploymentStatus().setStatus(status);
        // persist changes
        this.deploymentService.save(theDeployment);

        CloudProviderParamsCopy cloudProviderParametersCopy = this.cloudProviderParametersCopyService.
                findByCloudProviderParametersReference(theDeployment.getCloudProviderParametersReference());


        // Index deployment
        if (deploymentIndexService != null) {
            DeploymentDocument theDeploymentDocument = deploymentIndexService.findById(theDeployment.getReference());
            if (theDeploymentDocument == null) {
                theDeploymentDocument = new DeploymentDocument(
                        theDeployment.getAccount().getEmail(),
                        theDeployment.getReference(),
                        theDeployment.getDeploymentApplication().getName(),
                        theDeployment.getDeploymentApplication().getContact(),
                        theDeployment.getDeploymentApplication().getVersion(),
                        theDeployment.getDeploymentApplication().getName(),
                        cloudProviderParametersCopy.cloudProvider);
            }
            // set resources if provided
            if (terraformState!=null) {
                ApplicationDeployerHelper.updateResourceConsumptionFromTerraformState(terraformState, theDeploymentDocument);
            }
            // set status
            theDeploymentDocument.setStatus(status.toString());
            switch (status) {
                case STARTING:
                    theDeploymentDocument.setStartedTime(new Date(System.currentTimeMillis()));
                    //start time
                    theDeployment.setStartTime(startTime);
                    break;
                case RUNNING:
                    //deployed time
                    theDeploymentDocument.setDeployedTime(new Date(System.currentTimeMillis()));
                    theDeployment.setDeployedTime(new java.sql.Timestamp(System.currentTimeMillis()));
                    break;
                case DESTROYED:
                    theDeploymentDocument.setDestroyedTime(new Date(System.currentTimeMillis()));
                    theDeployment.setDestroyedTime(new java.sql.Timestamp(System.currentTimeMillis()));
                    if (theDeploymentDocument.getDeployedTime()!=null) {
                        theDeploymentDocument.setTotalRunningTime(
                                theDeploymentDocument.getDestroyedTime().getTime() - theDeploymentDocument.getDeployedTime().getTime()
                        );
                    }
                    break;
                case STARTING_FAILED:
                case RUNNING_FAILED:
                case DESTROYING_FAILED:
                    //start time, would be null for detroying_failed
                    if(startTime != null){
                        theDeployment.setStartTime(startTime);
                    }
                    theDeploymentDocument.setFailedTime(new Date(System.currentTimeMillis()));
                    theDeployment.setFailedTime(new java.sql.Timestamp(System.currentTimeMillis()));
                    theDeploymentDocument.setErrorCause(cause);
                    ErrorFromTerraformOutput error = ApplicationDeployerHelper.errorFromTerraformOutput(terraformOutput, logger);
                    theDeploymentDocument.setErrorCause(error.getErrorMessage());
                    break;
            }
            try {
                deploymentIndexService.save(theDeploymentDocument);
            } catch (RestClientException rce) {
                logger.error("DeploymentIndex service not available. Cause: ");
                rce.printStackTrace();
            }
            logger.info("Saving deployment status times");
            this.deploymentService.save(theDeployment);
        }

    }


    protected void updateDeploymentOutputs(DeploymentIndexService deploymentIndexService, ApplicationService applicationService,
                                         Deployment theDeployment, String cloudProviderPath,
                                         DeploymentStatusEnum failStatus, Configuration configuration, String terraformOutput) {
        Application theApplication = applicationService.findByAccountUsernameAndName(
                theDeployment.getDeploymentApplication().getAccount().getUsername(),
                theDeployment.getDeploymentApplication().getName());

        // get deployment state
        Map<String,String> deploymentOutputs = theApplication.getOutputs().stream()
                .collect(Collectors.toMap(ApplicationOutput::getName, s -> ""));
        StateFromTerraformOutput stateFromTerraformOutput = null;
        try {
            stateFromTerraformOutput = this.state(
                    theDeployment.getDeploymentApplication().getRepoPath(),
                    theDeployment.getReference(),
                    cloudProviderPath,
                    deploymentOutputs,
                    configuration,
                    theDeployment.getUserSshKey()
            );


            // update the deployment
            theDeployment.setAccessIp(stateFromTerraformOutput.getAccessIp());
            theDeployment.setProviderId(stateFromTerraformOutput.getId()!=null ? stateFromTerraformOutput.getId():"");

            // set outputs
            theApplication.getOutputs().forEach(output ->  {
//                        logger.debug( "Adding output " + output.getName() + " = " + deploymentOutputs.get(output.getName()) );
                        //                        logger.info( "  with length " + deploymentOutputs.get(output.getName()).length());
                        DeploymentGeneratedOutput newOutput = new DeploymentGeneratedOutput(
                                output.getName(),
                                deploymentOutputs.get(output.getName()),
                                theDeployment
                        );
                        //                        logger.info( "  with new length " + newOutput.getValue().length());
                        theDeployment.getGeneratedOutputs().add(newOutput);
                    }
            );

        } catch (IOException | ApplicationDeployerException e) {
            e.printStackTrace();
            // update status
            this.updateDeploymentStatus(deploymentIndexService, theDeployment,
                    failStatus,"Failed to obtain outputs from state file", null, terraformOutput, null);
        }

    }
}
