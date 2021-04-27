package org.ed.monkiclip.twitch;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.ed.monkiclip.twitch.model.Clip;
import org.ed.monkiclip.twitch.model.User;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static org.ed.monkiclip.Util.convertToRfc3339;

public class TwitchHelix {

    private static final int RETRIES = 3;

    private static final String TOKEN_ENDPOINT = "https://id.twitch.tv/oauth2/token";

    private static final String CLIPS_ENDPOINT = "https://api.twitch.tv/helix/clips?broadcaster_id=%s&started_at=%s&ended_at=%s";

    private static final String REVOKE_TOKEN_ENDPOINT = "https://id.twitch.tv/oauth2/revoke?client_id=%s&token=%s";

    private static final String USER_ENDPOINT = "https://api.twitch.tv/helix/users?login=%s";

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private String clientId;

    private String clientSecret;

    private String accessToken;

    private CloseableHttpClient httpClient;

    private TwitchHelix(String clientId, String clientSecret) {
        if (clientId == null || clientId.length() == 0) {
            throw new IllegalArgumentException("Received a bad clientId.");
        }
        if (clientSecret == null || clientSecret.length() == 0) {
            throw new IllegalArgumentException("Received a bad clientId.");
        }
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public static TwitchHelix initializeTwitchService(InputStream credStream) throws IOException {
        System.out.println("Initializing Twitch client.");

        String credString = "";
        byte[] buf = new byte[1024];
        while(credStream.read(buf) > 0) {
            credString += new String(buf);
        }
        JsonObject credentials = JsonParser.parseString(credString.trim()).getAsJsonObject();
        String clientSecret = credentials.get("client_secret").getAsString();
        String clientId = credentials.get("client_id").getAsString();

        TwitchHelix twitchHelix = new TwitchHelix(clientId, clientSecret);
        twitchHelix.httpClient = HttpClients.createDefault();

        twitchHelix.authorizeClient();

        return twitchHelix;
    }

    public List<User> getUser(String login) throws IOException {
        HttpGet request = new HttpGet(format(USER_ENDPOINT, login));
        request.setHeader("client-id", this.clientId);
        request.setHeader("Authorization", format("Bearer %s", this.accessToken));

        JsonObject response = makeRequest(request);

        JsonArray userData = response.getAsJsonArray("data");
        List<User> result = new ArrayList<>();
        for (int i = 0; i < userData.size(); i++) {
            result.add(GSON.fromJson(userData.get(i), User.class));
        }
        return result;
    }

    public List<Clip> getClips(String broadcasterId, LocalDateTime startAt, LocalDateTime endedAt) throws IOException {
        System.out.println(format("Retrieving clips for %s. startAt:%s endedAt:%s", broadcasterId, startAt.toString(), endedAt.toString()));

        String clipsUri = format(CLIPS_ENDPOINT, broadcasterId, convertToRfc3339(startAt), convertToRfc3339(endedAt));

        HttpGet request = new HttpGet(clipsUri);
        request.setHeader("client-id", this.clientId);
        request.setHeader("Authorization", format("Bearer %s", this.accessToken));

        JsonObject response = makeRequest(request);

        JsonArray clipData = response.getAsJsonArray("data");
        List<Clip> result = new ArrayList<>();
        clipData.forEach(el -> result.add(GSON.fromJson(el, Clip.class)));

        System.out.println(format("Retrieved %d clip data.", clipData.size()));
        return result;
    }

    public void downloadClip(Clip clip, File dlPath) throws IOException {
        if (clip == null) {
            throw new IllegalArgumentException("null clip");
        }
        if (dlPath == null) {
            throw new IllegalArgumentException("null dlPath");
        }

        String[] dlUrl = clip.getThumbnailUrl().split("-preview-");
        if (dlUrl.length == 0) {
            throw new IllegalArgumentException("Could not correctly parse thumbnail url for clip media url.");
        }

        String requestUrl = dlUrl[0] + ".mp4";

        System.out.println(format("Downloading clip %s", clip.getId()));
        FileOutputStream out = new FileOutputStream(dlPath);
        HttpGet clipRequest = new HttpGet(requestUrl);
        CloseableHttpResponse response = this.httpClient.execute(clipRequest);
        try {
            response.getEntity().writeTo(out);
        } finally {
            out.close();
        }

        System.out.println(format("Downloaded clip %s", clip.getId()));
    }

    private JsonObject makeRequest(HttpUriRequest request) throws IOException {
        boolean success = false;
        JsonObject respBody = null;
        int i = 0;

        while (!success && i < RETRIES) {
            CloseableHttpResponse response = this.httpClient.execute(request);

            int statusCode = response.getStatusLine().getStatusCode();
            try {
                respBody = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
            } finally {
                response.close();
            }

            if (statusCode == 200) {
                success = true;
            } else if (statusCode == 401 && "Invalid OAuth token".equals(respBody.get("message").getAsString())) {
                this.authorizeClient();
            } else {
                throw new IllegalStateException(format("Received bad http response code:%d %s", statusCode, request.getURI()));
            }

            i++;
        }

        return respBody;
    }

    public void closeClient() {
        System.out.println("Revoking app access token.");
        HttpPost post = new HttpPost(format(REVOKE_TOKEN_ENDPOINT, this.clientId, this.accessToken));
        try {
            CloseableHttpResponse response = this.httpClient.execute(post);
            try {
                if (response.getStatusLine().getStatusCode() != 200) {
                    System.err.println("Failed to revoke app access token for twitch client.");
                }
            } finally {
                response.close();
            }
        } catch (IOException e) {
            System.err.println("Failed to revoke app access token for twitch client.");
        }
        System.out.println("Successfully revoked app access token.");
        try {
            this.httpClient.close();
        } catch (IOException e) {
            System.err.println("Failed to close the httpclient properly.");
            System.err.println(e.getMessage());
            System.err.println(e.getStackTrace());
        }
    }

    private void authorizeClient() throws IOException {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("grant_type", "client_credentials"));
        params.add(new BasicNameValuePair("client_id", this.clientId));
        params.add(new BasicNameValuePair("client_secret", this.clientSecret));
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, Consts.UTF_8);
        HttpPost post = new HttpPost(TOKEN_ENDPOINT);
        post.setEntity(entity);

        ResponseHandler<String> handler = response -> {
            StatusLine statusLine = response.getStatusLine();
            HttpEntity responseEntity = response.getEntity();
            String responseBody = null;
            if (responseEntity != null) {
                responseBody = EntityUtils.toString(responseEntity);
            }

            if (statusLine.getStatusCode() == 200) {
                Matcher matcher = Pattern.compile("\"access_token\":\"(\\w+)\"").matcher(responseBody);
                if (matcher.find()) {
                    return matcher.group(1);
                } else {
                    throw new IllegalStateException("Unexpected response body from auth endpoint.");
                }
            } else {
                Matcher matcher = Pattern.compile("\"message\":\"([^\"]+)\"").matcher(responseBody);
                if (matcher.find()) {
                    String msg = format("Couldn't get access token. statusCode:%d message:%s",
                            statusLine.getStatusCode(),
                            matcher.group(1));
                    throw new IllegalArgumentException(msg);
                } else {
                    throw new ClientProtocolException("Couldn't get access token. statusCode: " + statusLine.getStatusCode());
                }
            }
        };

        this.accessToken = this.httpClient.execute(post, handler);
    }
}
