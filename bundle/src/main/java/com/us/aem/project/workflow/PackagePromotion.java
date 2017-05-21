package com.us.aem.project.workflow;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.Session;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.commons.lang.text.StrLookup;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.HtmlEmail;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.Workflow;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowData;
import com.adobe.granite.workflow.exec.HistoryItem;
import com.adobe.granite.workflow.exec.Route;
import com.adobe.granite.workflow.model.WorkflowNode;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.day.cq.commons.mail.MailTemplate;
import com.day.cq.mailer.MailService;
/**
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
*/
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
            value = "Package Promotion",
            description = "Package Deploy step"
    )
})
public class PackagePromotion implements WorkflowProcess  {
	
	private static final Logger LOG = LoggerFactory.getLogger(PackagePromotion.class);

	@Reference
	MailService mailService;

	@Reference
	private ResourceResolverFactory resolverFactory;
	
	public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap args) throws WorkflowException {
		try {
		
			String payloadPath = workItem.getWorkflowData().getPayload().toString();
			
			Session session = workflowSession.adaptTo(Session.class);
			ResourceResolver resolver = getResourceResolver(session);
			
			int itemHistorySize = workflowSession.getHistory(workItem.getWorkflow()).size();
			
			Workflow workflow = workItem.getWorkflow();
			List<HistoryItem> historyItems = workflowSession.getHistory(workflow);
			
			HistoryItem approvedHistoryItem = historyItems.get(itemHistorySize - 1);
			HistoryItem destinationHistoryItem = historyItems.get(itemHistorySize - 3);
			
			WorkItem approvedWorkItem = approvedHistoryItem.getWorkItem();
			WorkItem destinationWorkItem = destinationHistoryItem.getWorkItem();
			
			MetaDataMap approvedDialogDataMap = approvedWorkItem.getMetaDataMap();
			MetaDataMap destinationDialogDataMap = destinationWorkItem.getMetaDataMap();
	
			String approval = approvedDialogDataMap.get("approval", String.class);
			String emerID = approvedDialogDataMap.get("emerID", String.class);
			String password = approvedDialogDataMap.get("password", String.class);
			String comment = approvedDialogDataMap.get("comment", String.class);
			
			String destinationEnvironment = destinationDialogDataMap.get("destinationEnvironment", String.class);
			String reqNumber = destinationDialogDataMap.get("reqNumber", String.class);
			String promotionType = destinationDialogDataMap.get("action", String.class);
			
			String currentServer = destinationWorkItem.getNode().getMetaDataMap().get("PROCESS_ARGS", "localhost:8080");
			
			String initiatorId = workflow.getInitiator();
			String approvalId = approvedHistoryItem.getUserId();
			
			String initiatorEmailId = getUserEmailId(resolver, initiatorId);
			
			Map<String, String> parameters = new HashMap<String, String>();
			parameters.put("requestNumber", reqNumber);
			parameters.put("initiatorName", getUserName(resolver, initiatorId));
			parameters.put("approvalName", getUserName(resolver, approvalId));
			parameters.put("comment", comment);
			parameters.put("destination", destinationEnvironment.toUpperCase());
			parameters.put("approvalAction", promotionType.toUpperCase());
			parameters.put("packageAction", approval.toUpperCase());
			parameters.put("payloadPath", payloadPath);
			parameters.put("currentServer", currentServer);
						
			if (StringUtils.equals("approve", approval)) {
	
				InputStream in = getPackage(session, payloadPath);
				uploadPackage(in,
						getDestinationServicePackageManagerPath(destinationEnvironment),
						getCredentails(emerID, password),
						payloadPath, promotionType);
			}
			
			sendEmail(session, initiatorEmailId, parameters);
			
			LOG.info(payloadPath);
		} catch (Exception ex) {
			LOG.error("Error in package promotion", ex);
		}
	}
	
	private InputStream getPackage(Session session, String packagePath) {
		InputStream in = null;
		try {
			if (null != session && session.itemExists(packagePath + "/jcr:content")) {
				Node packageContentNode = session.getNode(packagePath + "/jcr:content");
				in = packageContentNode.getProperty("jcr:data").getBinary().getStream();
			}
		} catch (Exception ex) {
			LOG.error("Error in getPackage", ex);
		}
		
		return in;
	}
	
	private void uploadPackage(InputStream packageIn, String destination, String credentails, String packagePath, String promotionType) {
		try {
			if (null != packageIn && StringUtils.isNotBlank(destination) && StringUtils.isNotBlank(credentails) && StringUtils.isNotBlank(packagePath)) {
				String packageName = getPackageName(packagePath);
				Runtime runtime = Runtime.getRuntime();
				String aemPath = System.getProperty("user.dir") + File.separator + "temp" + File.separator + "tempPackage.zip";
				File targetFolder = new File(aemPath);
				targetFolder.mkdirs();
				Path targetPath = targetFolder.toPath();
				Files.copy(packageIn, targetPath, StandardCopyOption.REPLACE_EXISTING);
				
				String curlCommand = "";
				if (StringUtils.equalsIgnoreCase("upload-install-replicate", promotionType) || StringUtils.equalsIgnoreCase("upload-install", promotionType)) {
					curlCommand = "curl -u " + credentails + " -F file=@\"" + aemPath + "\" -F name=\"" + packageName + "\" -F force=true -F install=true " + destination;
				} else {
					curlCommand = "curl -u " + credentails + " -F file=@\"" + aemPath + "\" -F name=\"" + packageName + "\" -F force=true -F install=false " + destination;
				}
				LOG.info("Curl Command -- {}", curlCommand);
				
				Process process = runtime.exec(curlCommand);
				
				printCurlResponse(process);
				
				if (StringUtils.equalsIgnoreCase("upload-install-replicate", promotionType)) {
					String replicationPath = StringUtils.replace(destination, "/crx/packmgr/service.jsp", "/bin/replicate.json");
					String replicateCurlCmd = "curl -u " + credentails + " -X POST -F path=\"" + packagePath + "\" -F cmd=\"activate\" " + replicationPath;
					LOG.info("Replication Curl Command -- {}", replicateCurlCmd);
					
					Process replicateProcess = runtime.exec(replicateCurlCmd);
					
					printCurlResponse(replicateProcess);
				}
			}
		} catch (Exception ex) {
			LOG.error("Error in uploadPackage", ex);
		}
	}
	
	private String getDestinationServicePackageManagerPath(String destinationEnvironment) {
		String envPath = null;
		
		// change
		if ("it".equals(destinationEnvironment)) {
			envPath = "VMIKSA69901B1D:4502";
		} else if ("uat".equals(destinationEnvironment)) {
			envPath = "localhost:4502";
		} if ("prod".equals(destinationEnvironment)) {
			envPath = "localhost:4502";
		}
		if (StringUtils.isNotBlank(envPath)) {
			envPath = "http://" + envPath + "/crx/packmgr/service.jsp";
		}
		return envPath;
	}
	
	private String getCredentails(String userName, String password) {
		String credentails = null;
		if (StringUtils.isNotBlank(userName) && StringUtils.isNotBlank(password)) {
			credentails = userName + ":" + password;
		}
		return credentails;
	}
	
	private String getPackageName(String packagePath) {
		return packagePath.substring(packagePath.lastIndexOf("/")+1);
	}
	
	private void printCurlResponse(Process process) {
		try {
			// -- To log the response - START
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream()));
			LOG.info("Curl Output 1  -- {}", reader.readLine());
			
			StringBuffer output = new StringBuffer();
			String line = "";
			while ((line = reader.readLine()) != null) {
				output.append(line + "\n");
			}
			LOG.info("Curl Output 2  -- {}", output.toString());
			// -- To log the response - END
		} catch (Exception ex) {
			LOG.error("Error while printing response", ex);
		}
	}
	
	private boolean sendEmail (Session session, String initiator,
			Map<String, String> parameters) {
		boolean sendSuccess = false;
		try {
			String emailTemplatePath = "/etc/notification/email/workflow/package-promotion-after-approval-en.html";
			
			final MailTemplate mailTemplate = MailTemplate.create(emailTemplatePath, session);
			final HtmlEmail email = mailTemplate.getEmail(StrLookup.mapLookup(parameters), HtmlEmail.class);
			
			email.addTo(initiator);
			
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
	
	private String getUserEmailId(ResourceResolver resolver, String userId) {
		String emailId = null;
		try {
			UserManager userManager = resolver.adaptTo(UserManager.class);
			
			User user = (User) userManager.getAuthorizable(userId);
			if (null != user) {
				emailId = user
						.getProperty("./profile/email") != null ? user
						.getProperty("./profile/email")[0]
						.getString() : null;
			}
		} catch (Exception ex) {
			LOG.error("Error while getting user name", ex);
		}
		return emailId;
	}
	
	private ResourceResolver getResourceResolver(Session session) throws LoginException {
		return resolverFactory.getResourceResolver(Collections.<String, Object>singletonMap("user.jcr.session", session));
	}
}