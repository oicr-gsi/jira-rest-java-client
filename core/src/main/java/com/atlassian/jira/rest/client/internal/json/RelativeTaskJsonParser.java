package com.atlassian.jira.rest.client.internal.json;

import com.atlassian.jira.rest.client.api.domain.IssueType;
import com.atlassian.jira.rest.client.api.domain.Status;
import com.atlassian.jira.rest.client.api.domain.RelativeTask;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.net.URI;

public class RelativeTaskJsonParser implements JsonObjectParser<RelativeTask> {
    private final IssueTypeJsonParser issueTypeJsonParser = new IssueTypeJsonParser();
    private final StatusJsonParser statusJsonParser = new StatusJsonParser();

    @Override
    public RelativeTask parse(JSONObject json) throws JSONException {
        final URI issueUri = JsonParseUtil.parseURI(json.getString("self"));
        final String issueKey = json.getString("key");
        final JSONObject fields = json.getJSONObject("fields");
        final String summary = fields.getString("summary");
        final Status status = statusJsonParser.parse(fields.getJSONObject("status"));
        final IssueType issueType = issueTypeJsonParser.parse(fields.getJSONObject("issuetype"));
        return new RelativeTask(issueKey, issueUri, summary, issueType, status);
    }
}
