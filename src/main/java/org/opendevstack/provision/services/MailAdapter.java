/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendevstack.provision.services;

import org.opendevstack.provision.model.ProjectData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import com.atlassian.crowd.integration.springsecurity.user.CrowdUserDetails;

/**
 * Service for mail interaction with the user in the provisioning app
 *
 * @author Brokmeier, Pascal
 */
@Service
public class MailAdapter {

  private static final Logger logger = LoggerFactory.getLogger(MailAdapter.class);
  private static final String STR_LOGFILE_KEY = "loggerFileName";

  private JavaMailSender mailSender;

  @Value("${provison.mail.sender}")
  private String mailSenderAddress;

  // open because of testing
  @Value("${mail.enabled:false}")
  boolean isMailEnabled;
  
  @Autowired
  private TemplateEngine templateEngine;

  // testing only!
  private CrowdUserDetails crowdUserDetails = null;

  @Autowired
  public MailAdapter(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  public void notifyUsersAboutProject(ProjectData data) {
	if (!isMailEnabled) {
		logger.debug("Email disabled, returning");
		return;
	}
    CrowdUserDetails userDetails = getCrowdUserDetails();
    String recipient = userDetails.getEmail();
    MimeMessagePreparator messagePreparator = mimeMessage -> {
        MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage);
        messageHelper.setFrom(mailSenderAddress);
        messageHelper.setTo(recipient);
        messageHelper.setSubject("ODS Project provision update");
        messageHelper.setText(build(data), true);
      };
      
      Thread sendThread = new Thread(() -> {
          try {
            MDC.put(STR_LOGFILE_KEY, data.key);
            mailSender.send(messagePreparator);
          } catch (MailException e) {
            logger.error("Error in sending mail for project: " + data.key, e);
          } finally {
          MDC.remove(STR_LOGFILE_KEY);
          }
      });
      
      sendThread.start();
      logger.debug("Mail for project: {} sent", data.key );
  }

  String build(ProjectData data) {
    try {
      Context context = new Context();
      context.setVariable("project", data);
      return templateEngine.process("mailTemplate", context);
    } catch (SpelEvaluationException ex) {
      logger.error("Error in creating mail template", ex);
    }
    return "";
  }

  void setCrowdUserDetails(CrowdUserDetails details) {
    this.crowdUserDetails = details;
  }

  private CrowdUserDetails getCrowdUserDetails() {
    if (crowdUserDetails == null) {
      crowdUserDetails = getCrowdUserDetailsFromContext();
    }
    return crowdUserDetails;
  }

  CrowdUserDetails getCrowdUserDetailsFromContext() {
    return (CrowdUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }
}
