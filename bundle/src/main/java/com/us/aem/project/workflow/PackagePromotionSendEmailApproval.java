package com.us.aem.project.workflow;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.jcr.Session;

import org.apache.commons.lang.text.StrLookup;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.HtmlEmail;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.HistoryItem;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.Workflow;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.day.cq.commons.mail.MailTemplate;
import com.day.cq.mailer.MailService;

@Component
@Service
@Properties({
    @Property(
            name = Constants.SERVICE_DESCRIPTION,
            value = "Package Deploy step"
    ),
    @Property(
            label = "Workflow Label",
            name = "process.label",
            value = "Package Promotion Send Email",
            description = "Package Deploy step"
    )
})
public class PackagePromotionSendEmailApproval implements WorkflowProcess  {
	private static final Logger LOG = LoggerFactory.getLogger(PackagePromotion.class);

	@Reference
	MailService mailService;

	@Reference
	private ResourceResolverFactory resolverFactory;
	
	// change
	private static final String IT_APPROVAL_GROUP = "";
	
	private static final String UAT_APPROVAL_GROUP = "";
	
	private static final String PROD_APPROVAL_GROUP = "group2";
	
	public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap args) throws WorkflowException {
		try {
		
			String payloadPath = workItem.getWorkflowData().getPayload().toString();
			
			Session session = workflowSession.adaptTo(Session.class);
			ResourceResolver resolver = getResourceResolver(session);
			
			int itemHistorySize = workflowSession.getHistory(workItem.getWorkflow()).size();
			
			Workflow workflow = workItem.getWorkflow();
			List<HistoryItem> historyItems = workflowSession.getHistory(workflow);
			
			HistoryItem destinationHistoryItem = historyItems.get(itemHistorySize - 1);
			
			WorkItem destinationWorkItem = destinationHistoryItem.getWorkItem();
			
			MetaDataMap destinationDialogDataMap = destinationWorkItem.getMetaDataMap();
	
			String destinationEnvironment = destinationDialogDataMap.get("destinationEnvironment", String.class);
			String reqNumber = destinationDialogDataMap.get("reqNumber", String.class);
			String comment = destinationDialogDataMap.get("comment", String.class);
			String promotionType = destinationDialogDataMap.get("action", String.class);
			
			String currentServer = destinationWorkItem.getNode().getMetaDataMap().get("PROCESS_ARGS", "localhost:8080");
			
			String initiatorId = workflow.getInitiator();
			
			Map<String, String> parameters = new HashMap<String, String>();
			parameters.put("requestNumber", reqNumber);
			parameters.put("initiatorName", getUserName(resolver, initiatorId));
			parameters.put("comment", comment);
			parameters.put("destination", destinationEnvironment.toUpperCase());
			parameters.put("approvalAction", promotionType.toUpperCase());
			parameters.put("payloadPath", payloadPath);
			parameters.put("inboxPath", "http://" + currentServer + "/inbox");
			
			
			sendEmail(session, getUserEmailIds(resolver, destinationEnvironment), parameters);
			
			LOG.info(payloadPath);
		} catch (Exception ex) {
			LOG.error("Error in package promotion", ex);
		}
	}
	
	private boolean sendEmail (Session session, List<String> approvals,
			Map<String, String> parameters) {
		boolean sendSuccess = false;
		try {
			String emailTemplatePath = "/etc/notification/email/workflow/ckage-promotion-to-approval-en.html";
			
			final MailTemplate mailTemplate = MailTemplate.create(emailTemplatePath, session);
			final HtmlEmail email = mailTemplate.getEmail(StrLookup.mapLookup(parameters), HtmlEmail.class);
			
			for (String emailId : approvals) {
				email.addTo(emailId);
			}
			
			mailService.sendEmail(email);
			
			LOG.info("Email send successfully");
			sendSuccess = true;
		} catch (Exception ex) {
			LOG.error("Error while sending email", ex);
		}
		return sendSuccess;
	}
	
	private String getUserName(ResourceResolver resolver, String userId) {
		String userNameFormatted = userId;
		try {
			UserManager userManager = resolver.adaptTo(UserManager.class);
			
			User user = (User) userManager.getAuthorizable(userId);
			if (null != user) {
				String lastName = user
						.getProperty("./profile/familyName") != null ? user
						.getProperty("./profile/familyName")[0]
						.getString() : null;
				String firstName = user
						.getProperty("./profile/givenName") != null ? user
						.getProperty("./profile/givenName")[0]
						.getString() : null;
				String userName = user.getPrincipal().getName();
				if (StringUtils.isNotBlank(lastName)) {
					userNameFormatted = lastName;
					if (StringUtils.isNotBlank(firstName)) {
						userNameFormatted += ", ";
					}
				}
				if (StringUtils.isNotBlank(firstName)) {
					userNameFormatted += firstName;
				}
				if (StringUtils.isBlank(userNameFormatted)
						&& StringUtils.isNotBlank(userName)) {
					userNameFormatted = userName;
				}
			}
		
		} catch (Exception ex) {
			LOG.error("Error while getting user name", ex);
		}
		return userNameFormatted;
	}
	
	private List<String> getUserEmailIds(ResourceResolver resolver, String destinationEnvironment) {
		List<String> emailIds = new LinkedList<String>();
		try {
			String participant = null;
			if ("it".equals(destinationEnvironment)) {
				participant = IT_APPROVAL_GROUP;
			} else if ("uat".equals(destinationEnvironment)) {
				participant = UAT_APPROVAL_GROUP;
			} else if ("prod".equals(destinationEnvironment)) {
				participant = PROD_APPROVAL_GROUP;
			}
			
			UserManager userManager = resolver.adaptTo(UserManager.class);
			
			Group group =  (Group) userManager.getAuthorizable(participant);
			Iterator<Authorizable> userList = group.getMembers();
			
			while (userList.hasNext()) {
				User user = (User) userList.next();
				String emailId = user
						.getProperty("./profile/email") != null ? user
						.getProperty("./profile/email")[0]
						.getString() : null;
				emailIds.add(emailId);
			}
			
		} catch (Exception ex) {
			LOG.error("Error while getting user name", ex);
		}
		return emailIds;
	}
	
	private ResourceResolver getResourceResolver(Session session) throws LoginException {
		return resolverFactory.getResourceResolver(Collections.<String, Object>singletonMap("user.jcr.session", session));
	}
}