/*
 * Copyright (C) 2023 Atlassian
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.atlassian.jira.rest.client.internal.json;

import com.atlassian.jira.rest.client.api.domain.IssueType;
import com.atlassian.jira.rest.client.api.domain.Status;
import com.atlassian.jira.rest.client.api.domain.ParentTask;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.net.URI;

public class ParentTaskJsonParser implements JsonObjectParser<ParentTask>{
	private final IssueTypeJsonParser issueTypeJsonParser = new IssueTypeJsonParser();
	private final StatusJsonParser statusJsonParser = new StatusJsonParser();
	@Override
	public ParentTask parse(JSONObject json) throws JSONException {
		final URI issueUri = JsonParseUtil.parseURI(json.getString("self"));
		final String issueKey = json.getString("key");
		final JSONObject fields = json.getJSONObject("fields");
		final String summary = fields.getString("summary");
		final Status status = statusJsonParser.parse(fields.getJSONObject("status"));
		final IssueType issueType = issueTypeJsonParser.parse(fields.getJSONObject("issuetype"));
		return new ParentTask(issueKey, issueUri, summary, issueType, status);
	}
}