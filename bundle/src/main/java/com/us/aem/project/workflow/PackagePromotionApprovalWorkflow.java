package com.us.aem.project.workflow;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.ParticipantStepChooser;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.day.cq.mailer.MailService;

@Component
@Service
@Properties({
		@Property(
				name = Constants.SERVICE_DESCRIPTION, 
				value = "Package Promotion Approval Workflow Step"),
		@Property(
				label = "Workflow Label", 
				name = "chooser.label", 
				value = "Package Promotion Approval", 
				description = "Package Promotion Approval Workflow Step") })
public class PackagePromotionApprovalWorkflow implements ParticipantStepChooser {
	
	private static final Logger LOG = LoggerFactory.getLogger(PackagePromotionApprovalWorkflow.class);

	@Reference
	MailService mailService;

	@Reference
	private ResourceResolverFactory resolverFactory;
	
	// change
	private static final String IT_APPROVAL_GROUP = "";
	
	private static final String UAT_APPROVAL_GROUP = "";
	
	private static final String PROD_APPROVAL_GROUP = "group2";

	public String getParticipant(WorkItem workItem, WorkflowSession wfSession, MetaDataMap metaDataMap) {
		String participant = null;
		try {
			int itemHistorySize = wfSession.getHistory(workItem.getWorkflow()).size();
			
			MetaDataMap dialogDataMap = wfSession.getHistory(workItem.getWorkflow()).get(itemHistorySize - 1).getWorkItem().getMetaDataMap();
			
			String destinationEnvironment = dialogDataMap.get("destinationEnvironment").toString();
			
			if ("it".equals(destinationEnvironment)) {
				participant = IT_APPROVAL_GROUP;
			} else if ("uat".equals(destinationEnvironment)) {
				participant = UAT_APPROVAL_GROUP;
			} else if ("prod".equals(destinationEnvironment)) {
				participant = PROD_APPROVAL_GROUP;
			}
		} catch (Exception ex) {
			LOG.error("Error while choosing package prmotion Approval Step", ex);
		}
		return participant;
	}
}
