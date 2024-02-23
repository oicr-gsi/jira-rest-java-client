package com.atlassian.jira.rest.client.api.domain;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.net.URI;

/**
 *
 */
public class Subtask extends RelativeTask {

    public Subtask(String issueKey, URI issueUri, String summary, IssueType issueType, Status status) {
	    super(issueKey, issueUri, summary, issueType, status);
    }

}
