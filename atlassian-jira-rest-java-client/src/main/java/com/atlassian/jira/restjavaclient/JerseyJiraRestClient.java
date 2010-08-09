package com.atlassian.jira.restjavaclient;

import com.atlassian.jira.restjavaclient.domain.Attachment;
import com.atlassian.jira.restjavaclient.domain.Comment;
import com.atlassian.jira.restjavaclient.domain.Field;
import com.atlassian.jira.restjavaclient.domain.Issue;
import com.atlassian.jira.restjavaclient.domain.IssueType;
import com.atlassian.jira.restjavaclient.domain.Project;
import com.atlassian.jira.restjavaclient.domain.User;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.config.ApacheHttpClientConfig;
import com.sun.jersey.client.apache.config.DefaultApacheHttpClientConfig;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import javax.annotation.Nullable;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * TODO: Document this class / interface here
 *
 * @since v0.1
 */
public class JerseyJiraRestClient implements JiraRestClient {

	private static final String THUMBNAIL = "thumbnail";
	private ApacheHttpClient client;
	private final URI baseUri;

	public JerseyJiraRestClient(URI serverUri) {
		this.baseUri = UriBuilder.fromUri(serverUri).path("/rest/api/latest").build();
		DefaultApacheHttpClientConfig config = new DefaultApacheHttpClientConfig();
		config.getState().setCredentials(null, null, -1, "admin", "admin");
		// @todo check with Justus why 404 is returned instead of 401 when no credentials are provided automagically
		config.getProperties().put(ApacheHttpClientConfig.PROPERTY_PREEMPTIVE_AUTHENTICATION, true);
		client = ApacheHttpClient.create(config);

	}

	public void login() {
	}

	private static URI getSelfUri(JSONObject jsonObject) throws JSONException {
		return parseURI(jsonObject.getString("self"));
	}

	private static URI parseURI(String str) {
		try {
			return new URI(str);
		} catch (URISyntaxException e) {
			throw new RestClientException(e);
		}
	}

	static Iterable<String> parseExpandos(JSONObject json) throws JSONException {
		final String expando = json.getString("expand");
		return Splitter.on(',').split(expando);
	}

	private static final String UPDATED_ATTR = "updated";
	private static final String CREATED_ATTR = "created";


	private static Set<String> SPECIAL_FIELDS = new HashSet<String>(Arrays.asList("summary", UPDATED_ATTR, CREATED_ATTR));


	static Collection<Field> parseFields(JSONObject json) throws JSONException {
		ArrayList<Field> res = new ArrayList<Field>(json.length());
		for (Iterator<String> it = json.keys(); it.hasNext();) {
			final String key = it.next();
			if (SPECIAL_FIELDS.contains(key)) {
				continue;
			}
			final Object value = json.get(key);
			if (value instanceof JSONObject) {

			} else {
				res.add(new Field(key, value != JSONObject.NULL ? value.toString() : null));
			}
		}
		return res;
	}


	@Nullable
	String getExpandoString(IssueArgs args) {
		Collection<String> expandos = new ArrayList<String>();
		expandos.add("fields"); // this IMO always vital;
		StringBuilder sb = new StringBuilder();
		if (args.withAttachments()) {
			expandos.add("attachments");
		}
		if (args.withComments()) {
			expandos.add("comments");
		}
		if (expandos.size() == 0) {
			return null;
		}
		return Joiner.on(',').join(expandos);
	}

	interface ExpandablePropertyBuilder<T> {
		T parse(JSONObject json) throws JSONException;
	}

	private <T> ExpandableProperty<T> parseExpandableProperty(JSONObject json, ExpandablePropertyBuilder<T> expandablePropertyBuilder)
			throws JSONException {
		final int numItems = json.getInt("size");
		final Collection<T> items;
		JSONArray itemsJa = json.getJSONArray("items");

		if (itemsJa.length() > 0) {
			items = new ArrayList<T>(numItems);
			for (int i = 0; i < itemsJa.length(); i++) {
				final T item = expandablePropertyBuilder.parse(itemsJa.getJSONObject(i));
				items.add(item);
			}
		} else {
			items = null;
		}

		return new ExpandableProperty<T>(numItems, items);
	}


	IssueType parseIssueType(JSONObject json) throws JSONException {
		final URI selfUri = getSelfUri(json);
		final String name = json.getString("name");
		final boolean isSubtask = json.getBoolean("subtask");
		return new IssueType(selfUri, name, isSubtask);
	}

	JSONObject getNestedObject(JSONObject json, String... path) throws JSONException {
		for (String s : path) {
			json = json.getJSONObject(s);
		}
		return json;
	}

	String getNestedString(JSONObject json, String... path) throws JSONException {

		for (int i = 0; i < path.length - 1; i++) {
			String s = path[i];
			json = json.getJSONObject(s);
		}
		return json.getString(path[path.length - 1]);
	}

	public Issue getIssue(final IssueArgs args, ProgressMonitor progressMonitor) {
		final UriBuilder uriBuilder = UriBuilder.fromUri(baseUri);
		uriBuilder.path("issue").path(args.getKey());
		final String expandoString = getExpandoString(args);
		if (expandoString != null) {
			uriBuilder.queryParam("expand", expandoString);
		}

		final WebResource issueResource = client.resource(uriBuilder.build());

		final JSONObject s = issueResource.get(JSONObject.class);

		try {
//            System.out.println(s.toString(4));
			return parseIssue(args, s);
		} catch (JSONException e) {
			throw new RestClientException(e);
		}
	}

	Project parseProject(JSONObject json) throws JSONException {
		final URI selfUri = getSelfUri(json);
		final String key = json.getString("key");
		return new Project(selfUri, key);
	}


	Issue parseIssue(IssueArgs args, JSONObject s) throws JSONException {
		final ExpandableProperty<Comment> expandableComment = parseExpandableProperty(s.getJSONObject("comments"),
				new CommentExpandablePropertyBuilder(args));

		final ExpandableProperty<Attachment> attachments = parseExpandableProperty(s.getJSONObject("attachments"), new ExpandablePropertyBuilder<Attachment>() {
			public Attachment parse(JSONObject json) throws JSONException {
				return parseAttachment(json);
			}
		});
		final Iterable<String> expandos = parseExpandos(s);
		final Collection<Field> fields = parseFields(s.getJSONObject("fields"));
		final IssueType issueType = parseIssueType(getNestedObject(s, "fields", "issuetype"));
		final DateTime creationDate = parseDateTime(getNestedString(s, "fields", "created"));
		final DateTime updateDate = parseDateTime(getNestedString(s, "fields", "updated"));
		final URI transitionsUri = parseURI(s.getString("transitions"));
		final Project project = parseProject(getNestedObject(s, "fields", "project"));

		return new Issue(getSelfUri(s), s.getString("key"), project, issueType, expandos, expandableComment, attachments, fields, creationDate, updateDate, transitionsUri);
	}

	private static User parseAuthor(JSONObject json) throws JSONException {
		return new User(getSelfUri(json), json.getString("name"), json.optString("displayName", null));
	}

	private static final DateTimeFormatter DATE_TIME_FORMATTER = ISODateTimeFormat.dateTime();

	private static DateTime parseDateTime(String str) {
		try {
			return DATE_TIME_FORMATTER.parseDateTime(str);
		} catch (Exception e) {
			throw new RestClientException(e);
		}
	}

	private static Comment parseComment(JSONObject json, @Nullable String renderer) throws JSONException {
		final URI selfUri = getSelfUri(json);
		final String body = json.getString("body");
		final User author = parseAuthor(json.getJSONObject("author"));
		final User updateAuthor = parseAuthor(json.getJSONObject("updateAuthor"));
		return new Comment(selfUri, body, author, updateAuthor, parseDateTime(json.getString("created")),
				parseDateTime(json.getString("updated")), renderer);
	}

	private Attachment parseAttachment(JSONObject json) throws JSONException {
		final URI selfUri = getSelfUri(json);
		final String filename = json.getString("filename");
		final User author = parseAuthor(json.getJSONObject("author"));
		final DateTime creationDate = parseDateTime(json.getString("created"));
		final int size = json.getInt("size");
		final String mimeType = json.getString("mimeType");
		final URI contentURI = parseURI(json.getString("content"));
		final URI thumbnailURI = json.has(THUMBNAIL) ? parseURI(THUMBNAIL) : null;
		return new Attachment(selfUri, filename, author, creationDate, size, mimeType, contentURI, thumbnailURI);
	}

	public User getUser() {
		return null;
	}

	private static class CommentExpandablePropertyBuilder implements ExpandablePropertyBuilder<Comment> {
		private final IssueArgs args;

		public CommentExpandablePropertyBuilder(IssueArgs args) {
			this.args = args;
		}

		public Comment parse(JSONObject json) throws JSONException {
			return parseComment(json, args.getRenderer());
		}
	}
}