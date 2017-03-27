package com.cloudbees.jenkins.plugins;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import net.sf.json.JSONObject;

public class BitbucketPayloadProcessor {

    private final BitbucketJobProbe probe;

    public BitbucketPayloadProcessor(BitbucketJobProbe probe) {
        this.probe = probe;
    }

    public BitbucketPayloadProcessor() {
        this(new BitbucketJobProbe());
    }

    public void processPayload(JSONObject payload, HttpServletRequest request) {
        if ("Bitbucket-Webhooks/2.0".equals(request.getHeader("user-agent"))) {
            if ("repo:push".equals(request.getHeader("x-event-key"))) {
                LOGGER.log(Level.INFO, "Processing new Webhooks payload");
                processWebhookPayload(payload);
            }
        } else {
            LOGGER.log(Level.INFO, "Processing old POST service payload");
            processPostServicePayload(payload);
        }
    }

    private void processWebhookPayload(JSONObject payload) {
        if (payload.has("repository")) {
            JSONObject repo = payload.getJSONObject("repository");
            LOGGER.log(Level.INFO, "Received commit hook notification for {0}", repo);

            String user = payload.getJSONObject("actor").getString("username");
            String url = repo.getJSONObject("links").getJSONObject("html").getString("href");
            String scm = repo.has("scm") ? repo.getString("scm") : "git";

            probe.triggerMatchingJobs(user, url, scm, payload.toString());
        } else if (payload.has("scm")) {
            LOGGER.log(Level.INFO, "Received commit hook notification for hg: {0}", payload);
            String user = payload.getJSONObject("owner").getString("username");
            String url = payload.getJSONObject("links").getJSONObject("html").getString("href");
            String scm = payload.has("scm") ? payload.getString("scm") : "hg";

            probe.triggerMatchingJobs(user, url, scm, payload.toString());
        }

    }

/*
{
    "canon_url": "https://bitbucket.org",
    "commits": [
        {
            "author": "marcus",
            "branch": "master",
            "files": [
                {
                    "file": "somefile.py",
                    "type": "modified"
                }
            ],
            "message": "Added some more things to somefile.py\n",
            "node": "620ade18607a",
            "parents": [
                "702c70160afc"
            ],
            "raw_author": "Marcus Bertrand <marcus@somedomain.com>",
            "raw_node": "620ade18607ac42d872b568bb92acaa9a28620e9",
            "revision": null,
            "size": -1,
            "timestamp": "2012-05-30 05:58:56",
            "utctimestamp": "2012-05-30 03:58:56+00:00"
        }
    ],
    "repository": {
        "absolute_url": "/marcus/project-x/",
        "fork": false,
        "is_private": true,
        "name": "Project X",
        "owner": "marcus",
        "scm": "git",
        "slug": "project-x",
        "website": "https://atlassian.com/"
    },
    "user": "marcus"
}
*/
    private void processPostServicePayload(JSONObject payload)
    {
        if (!payload.containsKey("repository")) {
            LOGGER.log(Level.WARNING, "Stopping processing. Missing the repository key in the payload.");
            return;
        }

        JSONObject repo = payload.getJSONObject("repository");
        LOGGER.log(Level.INFO, "Received commit hook notification for {0}", repo);

        String user = getUser(payload);
        String url = getURL(payload);
        String scm = getSCM(payload);

        probe.triggerMatchingJobs(user, url, scm, payload.toString());
    }

    private String getUser(JSONObject payload)
    {
        if (payload.containsKey("user")) {
            return payload.getString("user");
        }

        if (payload.containsKey("author")) {
            JSONObject author = payload.getJSONObject("author");
            return author.getString("username");
        }

        return "";
    }

    private String getURL(JSONObject payload)
    {
        if (payload.containsKey("repository") && payload.containsKey("canon_url")) {

            JSONObject repo = payload.getJSONObject("repository");
            return payload.getString("canon_url") + repo.getString("absolute_url");
        }

        if (payload.containsKey("repository")) {

            JSONObject repo = payload.getJSONObject("repository");
            return repo.getJSONObject("links").getJSONObject("html").getString("href");
        }

        return "";
    }

    private String getSCM(JSONObject payload)
    {
        if (payload.containsKey("scm")) {
            return payload.getString("scm");
        }

        if (payload.containsKey("repository")) {
            JSONObject repo = payload.getJSONObject("repository");
            return repo.getString("scm");
        }

        return "";
    }

    private static final Logger LOGGER = Logger.getLogger(BitbucketPayloadProcessor.class.getName());

}
